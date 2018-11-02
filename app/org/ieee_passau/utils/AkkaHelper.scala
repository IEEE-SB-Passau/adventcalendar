package org.ieee_passau.utils

import com.google.inject.AbstractModule
import org.ieee_passau.controllers.{MonitoringActor, RankingActor}
import org.ieee_passau.evaluation._
import play.api.libs.concurrent.AkkaGuiceSupport

object AkkaHelper {
  final val rankingActor = "RankingActor"
  final val monitoringActor = "MonitoringActor"
  final val evaluator = "Evaluator"
  val basePath: String = "akka://application/user/"
  val evalPath: String = basePath + evaluator + "/"
}

class AkkaModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindActor[MonitoringActor](AkkaHelper.monitoringActor)
    bindActor[RankingActor](AkkaHelper.rankingActor)
    bindActor[Evaluator](AkkaHelper.evaluator)
    bindActorFactory[DBReader, DBReader.Factory]
    bindActorFactory[DBWriter, DBWriter.Factory]
    bindActorFactory[VMMaster, VMMaster.Factory]
    bindActorFactory[InputRegulator, InputRegulator.Factory]
  }
}
