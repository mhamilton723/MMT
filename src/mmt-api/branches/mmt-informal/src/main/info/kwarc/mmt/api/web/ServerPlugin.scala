package info.kwarc.mmt.api.web
import info.kwarc.mmt.api._
import frontend._
import ontology._
import utils._
import tiscaf._
import objects._

/**
 * An MMT extensions that handles certain requests in MMT's HTTP server.
 * 
 * It will be called on URIs of the form http://server:port/:CONTEXT/PATH?QUERY
 * 
 * @param cont the CONTEXT
 */
abstract class ServerExtension(context: String) extends Extension {
  /**
   * @param cont the context of the request
   * @return true if cont is equal to this.context
   */
  def isApplicable(cont : String) : Boolean = cont == context
  /**
   * handles the HTTP request
   * @param path the PATH from above (excluding CONTEXT)
   * @param query the QUERY from above
   * @param body the body of the request
   * @return the HTTP response
   * 
   * Implementing classes can and should use Server.XmlResponse etc to construct responses conveniently.
   * 
   * Errors thrown by this method are caught and sent back to the browser.
   */
  def apply(path: List[String], query: String, body: Body): HLet
}

/** interprets the query as an MMT document URI and returns the SVG representation of the theory graph */
class SVGServer extends ServerExtension("svg") {
   /**
    *  @param path ignored
    *  @param query the document path
    *  @param body ignored
    */
   
   def apply(path: List[String], query: String, body: Body) = {
      val path = Path.parse(query, controller.getNamespaceMap)
      val (inNarr, newPath) = path match {
        // doc path
         case dp: DPath => (true, dp)
         // module path
         case mp: MPath => (false, mp)
         case gp: GlobalName => (false, gp.module.toMPath)
         case cp: CPath => (false, cp.parent.module.toMPath) 
      }
      val svgFile = if (inNarr) {
          val dp = newPath.asInstanceOf[DPath]
	      val (arch,inPath) = controller.backend.resolveLogical(dp.uri).getOrElse {
	         throw LocalError("illegal path: " + query)
	      }
	      val inPathFile = archives.Archive.narrationSegmentsAsFile(inPath, "omdoc")
	      arch.root / "export" / "svg" / "narration" / inPathFile
      } else {
          val mp = newPath.asInstanceOf[MPath]
	      val arch = controller.backend.findOwningArchive(mp).getOrElse {
	         throw LocalError("illegal path: " + query)
	      } 
          val inPathFile = archives.Archive.MMTPathToContentPath(mp)
          arch.root / "export" / "svg" / "content" / inPathFile
      }
      val node = utils.File.read(svgFile.setExtension("svg"))
      Server.TypedTextResponse(node, "image/svg+xml")
   }
}

/** interprets the body as a QMT [[ontology.Query]] and evaluates it */
class QueryServer extends ServerExtension("query") {
   /**
    *  @param path ignored
    *  @param httpquery ignored
    *  @param body the query as XML
    */
   def apply(path: List[String], httpquery: String, body: Body) = {
      val mmtquery = body.asXML
      log("qmt query: " + mmtquery)
      val q = Query.parse(mmtquery)(controller.extman.queryExtensions)
      //log("qmt query: " + q.toString)
      Query.infer(q)(Nil) // type checking
      val res = controller.evaluator.evaluate(q)
      val resp = res.toNode
      Server.XmlResponse(resp)
   }
}

/** HTTP frontend to the [[Search]] class */
class SearchServer extends ServerExtension("search") {
   private lazy val search = new Search(controller)
   private val mmlpres = new presentation.MathMLPresenter
   override def start(args: List[String]) {mmlpres.init(controller)}
   /**
    *  @param path ignored
    *  @param httpquery search parameters
    *  @param body ignored
    */
   def apply(path: List[String], httpquery: String, body: Body) = {
      val wq = WebQuery.parse(httpquery)
      val base = wq("base")
      val mod = wq("module")
      val name = wq("name")
      val theory = wq("theory")
      val pattern = wq("pattern")
      val intype = wq.boolean("type")  
      val indef = wq.boolean("definition")
      val allcomps = List(TypeComponent, DefComponent)
      val comps = allcomps.zip(List(intype,indef)).filter(_._2).map(_._1)
      val pp = PathPattern(base, mod, name)
      val tp = (theory, pattern) match {
         case (Some(t), Some(p)) => Some(TermPattern.parse(controller, t, p))
         case (_, _) => None
      }
      val sq = SearchQuery(pp, comps, tp)
      val res = search(sq, true)
      val html = utils.HTML.builder
      import html._
      div(attributes = List("xmlns" -> xml.namespace("html"), "xmlns:jobad" -> utils.xml.namespace("jobad"))) {
         res.foreach {r =>
            div("result") {
               val CPath(par, comp) = r.cpath
               div {
                  text {comp.toString + " of "}
                  span("mmturi", attributes=List("jobad:href" -> par.toPath)) {text {par.last}}
               }
               r match {
                  case SearchResult(cp, pos, None) =>
                  case SearchResult(cp, pos, Some(term)) =>
                     def style(pc: presentation.PresentationContext) = if (pc.pos == pos) "resultmatch" else ""
                     div {mmlpres(term, Some(cp), style)(new presentation.HTMLRenderingHandler(html))}
               }
            }
         }
      }
      Server.XmlResponse(html.result)
   }
}

/** interprets the query as an MMT [[frontend.GetAction]] and returns the result */
class GetActionServer extends ServerExtension("mmt") {
    def apply(path: List[String], query: String, body: Body) = {
       val action = Action.parseAct(query, controller.getBase, controller.getHome)
        val resp: String = action match {
          case GetAction(a: ToWindow) =>
             a.make(controller)
             <done action={a.toString}/>.toString
          case GetAction(a: Respond) =>
             a.get(controller)
          case _ =>
             <notallowed action={action.toString}/>.toString
        }
        Server.XmlResponse(resp)
    }
}

/** interprets the query as an MMT [[frontend.Action]] and returns the log output */
class ActionServer extends ServerExtension("action") {
   private lazy val logCache = new RecordingHandler(logPrefix)
   override def start(args: List[String]) {
      report.addHandler(logCache)
   }
   override def destroy {
      report.removeHandler(logPrefix)
   }
   def apply(path: List[String], query: String, body: Body): HLet = {
      val c = query.replace("%20", " ")
      val act = frontend.Action.parseAct(c, controller.getBase, controller.getHome)
      if (act == Exit) {
         // special case for sending a response when exiting
         (new Thread {override def run {Thread.sleep(2); sys.exit}}).start
         return Server.XmlResponse(<exited/>)
      }
      logCache.record
      controller.handle(act)
      val r = logCache.stop
      logCache.clear
      val html = utils.HTML.builder
      import html._
      div {r foreach {l => div {text {l}}}}
      Server.XmlResponse(html.result)
   }
}


class MwsQuery extends ServerExtension("mwsq") {
	def apply(path : List[String], query : String, body : Body) = {
	  val bodyS = body.asString
	  val bjson = body.asJSON
	  val mpathS = bjson.obj.getOrElse("mpath", throw ServerError("mpath not given")).toString
	  val mpath : MPath = Path.parseM(mpathS, NamespaceMap.empty)
	  val tmS = bjson.obj.getOrElse("input", throw ServerError("input not given")).toString
	  var context = Context(mpath)
	  val parts = tmS.split(" ").toList map {s =>
	    if (s.charAt(0) == '?') { //assuming qvar
	      context ++= VarDecl(LocalName(s.substring(1)), None, None, None)
	      s.substring(1)
	    } else s
	  } 
	  val newtmS = parts.mkString(" ")
	  println(context)
	  val pu = parser.ParsingUnit(parser.SourceRef.anonymous(newtmS), context, newtmS)
	  val tm = try {
        controller.textParser(pu)(new ErrorContainer(None))
      } catch {
        case e : Error => println(e); throw e
        case e : Exception => println(e); throw e
      }
      println(tm)
      val pres = controller.extman.getPresenter("planetary").getOrElse(throw ServerError("Presenter not found"))
      val sb = new presentation.StringBuilder
      pres.apply(tm, None)(sb)
	  val res = sb.get
      println(res)
      Server.XmlResponse(res)
	}
}