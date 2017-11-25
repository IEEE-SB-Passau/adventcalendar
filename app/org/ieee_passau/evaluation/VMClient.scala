package org.ieee_passau.evaluation

import java.io.PrintStream
import java.net.Socket
import java.util.Base64

import akka.actor.SupervisorStrategy.Escalate
import akka.actor.{OneForOneStrategy, PoisonPill, Props, SupervisorStrategy}
import akka.pattern.ask
import akka.util.Timeout
import org.ieee_passau.evaluation.Messages._
import org.ieee_passau.models._
import org.ieee_passau.utils.MathHelper
import org.ieee_passau.utils.StringHelper._
import play.api.Play.current
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick._

import scala.collection.immutable.StringOps
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.BufferedSource
import scala.language.postfixOps
import scala.xml.{Elem, Text}

object VMClient {
  def props(host: String,
            port: Int,
            name: String): Props =
    Props(new VMClient(host, port, name))
}

class VMClient(host: String, port: Int, name:String)
  extends EvaluationActor {

  private var timeout = Timeout(MathHelper.makeDuration(play.Configuration.root().getString("evaluator.eval.basetime", "60 seconds")).mul(2))

  private case class ExecutionResult(var duration: Duration = 0 seconds,
                                     var memory: Int = 0,
                                     var terminationResult: Option[String] = None,
                                     var stdout: Option[String] = None,
                                     var stderr: Option[String] = None,
                                     var exitCode: Option[Int] = None,
                                     var score: Option[Int] = None,
                                     var result: Option[Result] = None,
                                     var progErr: Option[String] = None)

  // 10 * base timeout seems to be enough, up if read times out
  private val connection = context.actorOf(TCPActor.props(host, port, timeout.duration.mul(5).toMillis.toInt))

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _ => Escalate
  }

  override def postStop(): Unit = {
    connection ! PoisonPill
    super.postStop()
  }
  override def toString: String = {
    "VMClient@%s[host=%s]".format(Integer.toHexString(hashCode()), name)
  }

  //noinspection RedundantBlock
  private def runJob(job: Job): EvaluatedJob = {
    // Beans to store the result of compilation and evaluation
    val compilerResult: ExecutionResult = new ExecutionResult
    val evaluationResult: ExecutionResult = new ExecutionResult

    // Result:
    /*
    <ieee-advent-calendar>
      <run>
        <backendId>b6177f18-4ed7-453e-b1c1-d9679daac2a7</backendId>
        <successful>True</successful>
        <message></message>
        <outputs>
          <compilation>
            <returnCode>0</returnCode>
            <terminationReason>success</terminationReason>
            <streams>
              <stdOut>c3RkT3V0</stdOut>
              <stdErr>c3RkRXJy</stdErr>
            </streams>
          </compilation>
          <evaluation>
            <returnCode>0</returnCode>
            <terminationReason>success</terminationReason>
            <streams>
              <stdOut>c3RkT3V0</stdOut>
              <stdErr>c3RkRXJy</stdErr>
            </streams>
            <files>
              <file id="score">b64score</file>
              <file id="check">b64check</file>
            </files>
          </evaluation>
        </outputs>
        <utilization>
          <compilation>
            <cpu>0.0</cpu>
            <memory>0.0</memory>
            <runtime>0.0</runtime>
          </compilation>
          <evaluation>
            <cpu>0.0</cpu>
            <memory>0.0</memory>
            <runtime>0.0</runtime>
          </evaluation>
        </utilization>
      </run>
    </ieee-advent-calendar>
    */

    job match {
      case BaseJob(pid, rid, l, program, programName, stdin, expout) => {
        val lang = Languages.byLang(l).get
        val problem = DB.withSession { implicit session =>
          Problems.byId(pid).first
        }
        val runtimeLimit = MathHelper.makeDuration(play.Configuration.root().getString("evaluator.eval.basetime")).mul(lang.cpuFactor * problem.cpuFactor)
        timeout = Timeout(FiniteDuration(runtimeLimit.mul(2).toMillis, "milliseconds"))
        // Deploy Job
        val data =
          <ieee-advent-calendar>
            <job>
              <program>
                <filename>{programName}</filename>
                <content>{base64Encode(program)}</content>
                <language>{lang.id}</language>
              </program>
              <inputs>
                <streams>
                  {if (!stdin.isEmpty) {<stream id="stdin">{base64Encode(stdin)}</stream>}}
                </streams>
                <mask>{if (!stdin.isEmpty) {"{stdin}"}}</mask>
              </inputs>
              <outputs>
              </outputs>
              <limits>
                <cpu>{runtimeLimit.toSeconds}</cpu>
                <!-- seconds -->
                <memory>{(lang.memFactor * problem.memFactor * play.Configuration.root().getInt("evaluator.eval.basemem", 100)).floor.toInt}</memory>
                <!-- MB -->
              </limits>
            </job>
          </ieee-advent-calendar>

        val resultXml = Await.result((connection ? data) (timeout).mapTo[Elem], timeout.duration)
        log.debug("%s successfully deployed sourcecode for %s".format(this, job))

        if (!(resultXml \\ "successful").text.toBoolean) throw new RuntimeException("Received no valid result from eval. " + (resultXml \\ "reason").text + "\n" + (resultXml \\ "message").text)

        // Compile result
        compilerResult.exitCode = if ((resultXml \\ "outputs" \ "compilation" \ "returnCode").text.isEmpty) None
                                  else Some((resultXml \\ "outputs" \ "compilation" \ "returnCode").text.toInt)
        compilerResult.stdout = Some(base64Decode((resultXml \\ "outputs" \ "compilation" \ "streams" \ "stdOut").text))
        compilerResult.stderr = Some(base64Decode((resultXml \\ "outputs" \ "compilation" \ "streams" \ "stdErr").text))
        compilerResult.duration = Duration((resultXml \\ "utilization" \ "compilation" \ "runtime").text.toFloat, MILLISECONDS)
        compilerResult.memory = (resultXml \\ "utilization" \ "compilation" \ "memory").text.toInt
        compilerResult.terminationResult = Some((resultXml \\ "outputs" \ "compilation" \ "terminationReason").text)

        log.info("%s compiled sourcecode for %s with exit code %d in %d millis".format(this, job,
          compilerResult.exitCode.getOrElse(-1), compilerResult.duration.toMillis))

        // Evaluation result
        evaluationResult.exitCode = if ((resultXml \\ "outputs" \ "evaluation" \ "returnCode").text.isEmpty) None
                                    else Some((resultXml \\ "outputs" \ "evaluation" \ "returnCode").text.toInt)
        evaluationResult.stdout = Some(base64Decode((resultXml \\ "outputs" \ "evaluation" \ "streams" \ "stdOut").text))
        evaluationResult.stderr = Some(base64Decode((resultXml \\ "outputs" \ "evaluation" \ "streams" \ "stdErr").text))
        evaluationResult.duration = Duration((resultXml \\ "utilization" \ "evaluation" \ "runtime").text.toFloat, MILLISECONDS)
        evaluationResult.memory = (resultXml \\ "utilization" \ "evaluation" \ "memory").text.toInt
        evaluationResult.terminationResult = Some((resultXml \\ "outputs" \ "evaluation" \ "terminationReason").text)

        log.info("%s evaluated sourcecode for %s with exit code %d in %d millis".format(this, job,
          evaluationResult.exitCode.getOrElse(-1), evaluationResult.duration.toMillis))

        val result = if (evaluationResult.terminationResult.getOrElse("").contains("time")) {
          RuntimeExceeded
        } else if (evaluationResult.terminationResult.getOrElse("").contains("memory"))  {
          MemoryExceeded
        } else if (!compilerResult.exitCode.contains(0)) {
          CompileError
        } else if (!evaluationResult.stderr.getOrElse("").trim.isEmpty || !evaluationResult.exitCode.contains(0)) {
          ProgramError
        } else {
          // Magic newline handling
          val outLines = evaluationResult.stdout.getOrElse("").split("(\\n|\\r\\n|\\r)")
          val expLines = job.expectedOut.split("(\\n|\\r\\n|\\r)")
          //noinspection CorrespondsUnsorted
          if (!outLines.sameElements(expLines)) {
            WrongAnswer
          } else {
            Passed
          }
        }
        evaluationResult.result = Some(result)

        evaluationResult.progErr = Some(if (result == MemoryExceeded) {
          "" // Don't show stderr if memory was exceeded

        } else if (result == ProgramError
          && evaluationResult.exitCode.contains(139)
          && evaluationResult.stderr.contains("Segmentation fault")) {
          "Segmentation fault" // Show simple message for segfault

        } else if (result == ProgramError
          && evaluationResult.exitCode.contains(134)
          && evaluationResult.stderr.contains("double free or corruption")) {
          new StringOps(evaluationResult.stderr.getOrElse("")).lines.next() // Show first line of error for memory corruption

        } else if (result == ProgramError
          && evaluationResult.exitCode.contains(134)
          && evaluationResult.stderr.contains("Aborted")
          && evaluationResult.stderr.contains("after throwing an instance of")) {
          new StringOps(evaluationResult.stderr.getOrElse("")).lines.next() // Show first line for CPP exceptions

        } else {
          evaluationResult.stderr.getOrElse("") // Show stderr by default
        })
      }

      case NextStageJob(rid, stage, program, stdin, progOut, expOut, cmd, input, outputStdoutCheck, outputScore, filename, file) => {
        val lang = Languages.byLang(if (filename.endsWith("jar")) {"JAVAJAR"} else {"BINARY"}).get
        timeout = Timeout(MathHelper.makeDuration("180 seconds"))
        // Deploy binary file
        val data =
          <ieee-advent-calendar>
            <job>
              <program>
                <filename>{filename}</filename>
                <content>{Base64.getEncoder.encodeToString(file)}</content>
                <language>{lang.id}</language>
              </program>
              <inputs>
                <streams>
                  {if (input._1) {<stream id="stdIn">{  base64Encode(stdin)  }</stream>}}
                  {if (input._2) {<stream id="progOut">{base64Encode(progOut)}</stream>}}
                  {if (input._3) {<stream id="expOut">{ base64Encode(expOut) }</stream>}}
                  {if (input._4) {<stream id="program">{base64Encode(program)}</stream>}}
                </streams>
                <mask>{cmd}</mask>
              </inputs>
              <outputs>
                {if (outputScore)       {<file id="score">{{homedir}}/score</file>}}
                {if (outputStdoutCheck) {<file id="check">{{homedir}}/check</file>}}
              </outputs>
              <limits>
                <cpu>120</cpu>
                <memory>2048</memory>
              </limits>
            </job>
          </ieee-advent-calendar>

        val resultXml = Await.result((connection ? data) (timeout).mapTo[Elem], timeout.duration)
        log.debug("%s successfully deployed binary file for %s".format(this, job))

        if ((resultXml \\ "successfull").text.toBoolean) throw new RuntimeException("Received no valid result from eval.")

        // Evaluation result
        evaluationResult.exitCode = if ((resultXml \\ "outputs" \ "evaluation" \ "returnCode").text.isEmpty) None
                                    else Some((resultXml \\ "outputs" \ "evaluation" \ "returnCode").text.toInt)
        evaluationResult.stdout = Some(base64Decode((resultXml \\ "outputs" \ "evaluation" \ "streams" \ "stdOut").text))

        if (evaluationResult.stdout.get.isEmpty) evaluationResult.stdout = None
        evaluationResult.duration = Duration((resultXml \\ "utilization" \ "evaluation" \ "runtime").text.toFloat, MILLISECONDS)
        (resultXml \\ "outputs" \ "evaluation" \ "files" \ "file").foreach { tag => tag.attribute("id").getOrElse(new Text("")) match {
          case Text("score") => evaluationResult.score = Some(base64Decode(tag.text).toInt)
          case Text("check") =>
            val txt = base64Decode(tag.text)
            evaluationResult.result = Some(
              if (txt == "CORRECT") Passed
              else if (txt == "WRONG") WrongAnswer
              else ProgramError)
          case _ => // ignore
        }}

        log.info("%s evaluated task for %s with exit code %d in %d millis".format(this, job,
          evaluationResult.exitCode.getOrElse(-1), evaluationResult.duration.toMillis))
      }
    }

    EvaluatedJob(
      job = job,
      progOut = evaluationResult.stdout,
      progErr = evaluationResult.progErr,
      progExit = evaluationResult.exitCode,
      progRuntime = Some(evaluationResult.duration.toMillis / 1000.0),
      progMemory = Some(evaluationResult.memory),
      compOut = compilerResult.stdout,
      compErr = compilerResult.stderr,
      compExit = compilerResult.exitCode,
      compRuntime = Some(compilerResult.duration.toMillis / 1000.0),
      compMemory = Some(compilerResult.memory),
      result = evaluationResult.result,
      score = evaluationResult.score
    )
  }

  override def receive: Receive = {
    case JobM(job) =>
      log.info("%s received %s".format(this, job))

      var eJob: EvaluatedJob = null
      try {
        eJob = runJob(job)
      } catch {
        case e: Exception =>
          log.error("%s encountered an exception while processing %s: %s".format(this.toString, job, e), e)
          context.system.eventStream.publish(JobFailure(job))
          throw new RuntimeException("Timeout, shutting down actor", e)
      }

      log.info("%s finished %s".format(this, job))

      context.actorSelection("../../DBWriter") ! EvaluatedJobM(eJob)
  }
}

object TCPActor {
  def props(host: String, port: Int, timeout: Int): Props = Props(new TCPActor(host, port, timeout))
}

class TCPActor(host: String, port: Int, timeout: Int) extends EvaluationActor {

  private var socket: Socket = _

  override def postStop(): Unit = {
    if (socket != null) {
      socket.close()
    }
    super.postStop()
  }

  override def receive: Receive = {
    case PoisonPill =>
      socket.close()

    case data: xml.Elem =>
      new Runnable {
        override def run(): Unit = {
          Thread.currentThread().setName("TCP-actor" + host + ":" + port)
          try {
            socket = new Socket(host, port)
            socket.setSoTimeout(timeout)
            val os = new PrintStream(socket.getOutputStream)
            os.print(data)
            os.flush()
            val is = new BufferedSource(socket.getInputStream)
            val result = xml.XML.loadString(is.mkString)
            if (!(result \\ "successful").text.toBoolean) {
              log.info("Error in vm %s: %s", this, base64Decode((result \\ "message").text))
            }
            sender ! result
          } catch {
            case e: Throwable =>
              val error =
                <ieee-advent-calendar>
                  <run>
                    <successful>False</successful>
                    <reason>{e.getMessage}</reason>
                    <message>{e.getStackTrace}</message>
                  </run>
                </ieee-advent-calendar>
              sender ! error
              throw e
          } finally {
            socket.close()
            socket = null
          }
        }
      }.run()
  }
}
