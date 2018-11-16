
package org.ieee_passau.views.html.monitoring

import _root_.play.twirl.api.TwirlFeatureImports._
import _root_.play.twirl.api.TwirlHelperImports._
import _root_.play.twirl.api.Html
import _root_.play.twirl.api.JavaScript
import _root_.play.twirl.api.Txt
import _root_.play.twirl.api.Xml
import models._
import controllers._
import play.api.i18n._
import views.html._
import play.api.templates.PlayMagic._
import play.api.mvc._
import play.api.data._
/*1.2*/import java.util.Date
/*2.2*/import helper._
/*3.2*/import org.ieee_passau.models.User
/*4.2*/import org.ieee_passau.views.html.main
/*5.2*/import play.api.mvc.{Flash, RequestHeader}
/*6.2*/import scala.language.implicitConversions

object indexQueued extends _root_.play.twirl.api.BaseScalaTemplate[play.twirl.api.HtmlFormat.Appendable,_root_.play.twirl.api.Format[play.twirl.api.HtmlFormat.Appendable]](play.twirl.api.HtmlFormat) with _root_.play.twirl.api.Template5[List[scala.Tuple10[Int, Int, Int, Int, String, String, Int, String, Date, Option[Date]]],Flash,Option[User],RequestHeader,Messages,play.twirl.api.HtmlFormat.Appendable] {

  /**/
  def apply/*7.2*/(list: List[(Int, Int, Int, Int, String, String, Int, String, Date, Option[Date])])(implicit flash: Flash, sessionUser: Option[User], requestHeader: RequestHeader, messages: Messages):play.twirl.api.HtmlFormat.Appendable = {
    _display_ {
      {


Seq[Any](format.raw/*8.1*/("""
"""),_display_(/*9.2*/main(messages("nav.admin.evalqueue"))/*9.39*/ {_display_(Seq[Any](format.raw/*9.41*/("""
    """),format.raw/*10.5*/("""<form style="display: inline" method="POST" class="form-horizontal" action=""""),_display_(/*10.82*/org/*10.85*/.ieee_passau.controllers.routes.EvaluationController.cancelAll),format.raw/*10.147*/("""">
        <div class="form-group">
            """),_display_(/*12.14*/CSRF/*12.18*/.formField),format.raw/*12.28*/("""
            """),format.raw/*13.13*/("""<button type="submit" onclick="return (window.confirm('"""),_display_(/*13.69*/messages("eval.jobs.cancelall.confirm")),format.raw/*13.108*/("""'))" class="btn btn-danger pull-right">
            """),_display_(/*14.14*/messages("eval.jobs.cancelall.button")),format.raw/*14.52*/("""
            """),format.raw/*15.13*/("""</button>
        </div>
    </form>

    <table class="table table-striped">
        <thead>
            <tr>
                <th>"""),_display_(/*22.22*/messages("submission.title")),format.raw/*22.50*/(""" """),format.raw/*22.51*/("""/ """),_display_(/*22.54*/messages("testcase.title")),format.raw/*22.80*/(""" """),format.raw/*22.81*/("""/ """),_display_(/*22.84*/messages("evaltask.title")),format.raw/*22.110*/("""</th>
                <th>"""),_display_(/*23.22*/messages("problem.title")),format.raw/*23.47*/("""</th>
                <th>"""),_display_(/*24.22*/messages("user.title")),format.raw/*24.44*/("""</th>
                <th>"""),_display_(/*25.22*/messages("codelang.title")),format.raw/*25.48*/("""</th>
                <th>"""),_display_(/*26.22*/messages("general.createdate")),format.raw/*26.52*/("""</th>
                <th>"""),_display_(/*27.22*/messages("eval.jobs.state.title")),format.raw/*27.55*/("""</th>
                <th></th>
            </tr>
        </thead>
        <tbody>
        """),_display_(/*32.10*/list/*32.14*/.map/*32.18*/ {case (rid, sid, cid, stage, language, user, door, title, dateSub, dateQueued) =>_display_(Seq[Any](format.raw/*32.100*/("""
            """),format.raw/*33.13*/("""<tr>
                <td><a href=""""),_display_(/*34.31*/org/*34.34*/.ieee_passau.controllers.routes.EvaluationController.details(sid)),format.raw/*34.99*/("""">"""),_display_(/*34.102*/sid),format.raw/*34.105*/("""</a> / """),_display_(/*34.113*/cid),format.raw/*34.116*/(""" """),format.raw/*34.117*/("""/ """),_display_(/*34.120*/stage),format.raw/*34.125*/("""</td>
                <td>"""),_display_(/*35.22*/door),format.raw/*35.26*/(""" """),_display_(/*35.28*/title),format.raw/*35.33*/("""</td>
                <td>"""),_display_(/*36.22*/user),format.raw/*36.26*/("""</td>
                <td>"""),_display_(/*37.22*/language),format.raw/*37.30*/("""</td>
                <td><abbr class="timeago" title=""""),_display_(/*38.51*/dateSub/*38.58*/.format("yyyy-MM-dd'T'HH:mm:ssZ")),format.raw/*38.91*/("""">"""),_display_(/*38.94*/dateSub/*38.101*/.format("yyyy-MM-dd HH:mm:ss")),format.raw/*38.131*/("""</abbr></td>
                <td>"""),_display_(/*39.22*/if(dateQueued.isDefined)/*39.46*/ {_display_(Seq[Any](format.raw/*39.48*/("""
                    """),_display_(/*40.22*/messages("eval.jobs.state.assigned")),format.raw/*40.58*/(""" """),format.raw/*40.59*/("""<abbr class="timeago" title=""""),_display_(/*40.89*/dateQueued/*40.99*/.get.format("yyyy-MM-dd'T'HH:mm:ssZ")),format.raw/*40.136*/("""">"""),_display_(/*40.139*/dateQueued/*40.149*/.get.format("yyyy-MM-dd HH:mm:ss")),format.raw/*40.183*/("""</abbr>
                """)))}/*41.19*/else/*41.24*/{_display_(Seq[Any](format.raw/*41.25*/("""
                    """),_display_(/*42.22*/messages("eval.jobs.state.pending")),format.raw/*42.57*/("""
                """)))}),format.raw/*43.18*/("""</td>
                <td>
                    <form style="display: inline" method="POST" action=""""),_display_(/*45.74*/org/*45.77*/.ieee_passau.controllers.routes.EvaluationController.cancel(rid)),format.raw/*45.141*/("""">
                        """),_display_(/*46.26*/CSRF/*46.30*/.formField),format.raw/*46.40*/("""
                        """),format.raw/*47.25*/("""<button type="submit" onclick="return(window.confirm('"""),_display_(/*47.80*/messages("eval.jobs.cancel.confirm")),format.raw/*47.116*/("""'))" class="btn btn-danger"><span class="glyphicon glyphicon-remove"></span></button>
                    </form>
                </td>
            </tr>
        """)))}),format.raw/*51.10*/("""
        """),format.raw/*52.9*/("""</tbody>
    </table>
""")))}),format.raw/*54.2*/("""
"""))
      }
    }
  }

  def render(list:List[scala.Tuple10[Int, Int, Int, Int, String, String, Int, String, Date, Option[Date]]],flash:Flash,sessionUser:Option[User],requestHeader:RequestHeader,messages:Messages): play.twirl.api.HtmlFormat.Appendable = apply(list)(flash,sessionUser,requestHeader,messages)

  def f:((List[scala.Tuple10[Int, Int, Int, Int, String, String, Int, String, Date, Option[Date]]]) => (Flash,Option[User],RequestHeader,Messages) => play.twirl.api.HtmlFormat.Appendable) = (list) => (flash,sessionUser,requestHeader,messages) => apply(list)(flash,sessionUser,requestHeader,messages)

  def ref: this.type = this

}


              /*
                  -- GENERATED --
                  DATE: Fri Nov 16 19:55:49 CET 2018
                  SOURCE: /home/sebi/IEEE/adventskalender/frontend/app/org/ieee_passau/views/monitoring/indexQueued.scala.html
                  HASH: 78bfc3e175029f466c91f53097fb8318b3fa1ea4
                  MATRIX: 459->1|488->24|511->41|553->77|599->117|649->161|1125->204|1402->388|1429->390|1474->427|1513->429|1545->434|1649->511|1661->514|1745->576|1821->625|1834->629|1865->639|1906->652|1989->708|2050->747|2130->800|2189->838|2230->851|2389->983|2438->1011|2467->1012|2497->1015|2544->1041|2573->1042|2603->1045|2651->1071|2705->1098|2751->1123|2805->1150|2848->1172|2902->1199|2949->1225|3003->1252|3054->1282|3108->1309|3162->1342|3281->1434|3294->1438|3307->1442|3428->1524|3469->1537|3531->1572|3543->1575|3629->1640|3660->1643|3685->1646|3721->1654|3746->1657|3776->1658|3807->1661|3834->1666|3888->1693|3913->1697|3942->1699|3968->1704|4022->1731|4047->1735|4101->1762|4130->1770|4213->1826|4229->1833|4283->1866|4313->1869|4330->1876|4382->1906|4443->1940|4476->1964|4516->1966|4565->1988|4622->2024|4651->2025|4708->2055|4727->2065|4786->2102|4817->2105|4837->2115|4893->2149|4937->2175|4950->2180|4989->2181|5038->2203|5094->2238|5143->2256|5270->2356|5282->2359|5368->2423|5423->2451|5436->2455|5467->2465|5520->2490|5602->2545|5660->2581|5854->2744|5890->2753|5943->2776
                  LINES: 17->1|18->2|19->3|20->4|21->5|22->6|27->7|32->8|33->9|33->9|33->9|34->10|34->10|34->10|34->10|36->12|36->12|36->12|37->13|37->13|37->13|38->14|38->14|39->15|46->22|46->22|46->22|46->22|46->22|46->22|46->22|46->22|47->23|47->23|48->24|48->24|49->25|49->25|50->26|50->26|51->27|51->27|56->32|56->32|56->32|56->32|57->33|58->34|58->34|58->34|58->34|58->34|58->34|58->34|58->34|58->34|58->34|59->35|59->35|59->35|59->35|60->36|60->36|61->37|61->37|62->38|62->38|62->38|62->38|62->38|62->38|63->39|63->39|63->39|64->40|64->40|64->40|64->40|64->40|64->40|64->40|64->40|64->40|65->41|65->41|65->41|66->42|66->42|67->43|69->45|69->45|69->45|70->46|70->46|70->46|71->47|71->47|71->47|75->51|76->52|78->54
                  -- GENERATED --
              */
          