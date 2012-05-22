package info.kwarc.mmt.api.patterns

import info.kwarc.mmt.api._
import libraries._
import modules._
import frontend._
import symbols._
import objects._
import objects.Conversions._
import utils._
import utils.MyList._
import scala.io.Source

/** elaborates Instance declarations
 * this is also called the pragmatic-to-strict translation
 */
class PatternChecker(controller: Controller) extends Elaborator {
  def getPatterns(home : Term)(n : Int) : List[Pattern] = {     
     home match {
       case OMMOD(p) => 
         val thy = controller.globalLookup(p)
         thy match {
           case d : DeclaredTheory => d.meta match {
             case Some(m) => 
               val cmeta = controller.globalLookup(m)
               cmeta match {
                 case mthy : DeclaredTheory => 
                   val decls = mthy.valueList
                   decls.mapPartial{
                     case p : Pattern => 
                       if (p.body.variables.toList.length == n) { 
                         Some(p)
                       } else None
                     case _ => None}
                 case _ => Nil //TODO
               }
             case None => Nil
            }
           case _ => Nil //TODO
          }
        case _ => Nil //TODO
     }
  }
  def patternCheck(constants : List[Constant], pattern : Pattern) : Option[Substitution] = {        
    if (constants.length == pattern.body.length) {
      val mat = new Matcher(controller,pattern.body)
      var sub = Substitution()
//      val cons = constants// this is List
//      val ptr = pattern.body// this is not a List
//      val whatisit = constants.zip(pattern.body)
      constants.zip(pattern.body).forall {
        case (con,decl) =>
          val dtype = decl.tp.map(t => t ^ sub)
          val ddef = decl.df.map(d => d ^ sub)
          sub ++ Sub(con.name,decl.name)
          val aa = con.tp.toString()
          val bb = dtype.toString()
          val a = mat(con.tp,dtype,Context()) // does not match!!!
          val b = mat(con.df,ddef,Context()) // mat(con.df,ddef,..) should match and it does!
          mat(con.tp,dtype,Context()) && mat(con.df,ddef,Context())          
      }
      mat.metaContext.toSubstitution 
    } else None //Fail: Wrong number of declarations in pattern or number of constants               
  }  
  def apply(e: StructuralElement)(implicit cont: StructuralElement => Unit) : Unit = e match {
     case c: Constant =>
       val patts = getPatterns(c.home)(1)
       patts.mapPartial(p => patternCheck(List(c),p))
     case _ => 
   }
}

class Matcher(controller : Controller, var metaContext : Context) {
  def apply(dterm : Term, pterm : Term, con : Context = Context()) : Boolean = {    
    //if (lengthChecker(dterm,pterm)) {      
        (dterm,pterm) match {
        	case (OMID(a), OMID(b)) => a == b
        	case (OMI(i),OMI(j)) => i == j                   
            case (OMV(v),OMV(w)) if (v == w) => con.isDeclared(v) && con.isDeclared(w) 
            case (OMA(f1,args1),OMA(f2,args2)) => 
               apply(f1,f2,con) && args1.zip(args2).forall { 
                  case (x,y) => apply(x,y,con) 
               }
            case (OMBINDC(b1, ctx1, cond1, bod1), OMBINDC(b2,ctx2,cond2,bod2)) => apply(b1,b2,con) && apply(cond1,cond2,con ++ ctx1) && apply(bod1,bod2,con ++ ctx1)
// a missing case:
            //            case (OMV(v), anyT) => if metaContext.isDeclared(v)
//            							metaContext.++() // add v = anyT as definient to the metaContext, also true
//            						else false
            case (_,_) => false      
        }
    //}
  }
    
  def apply(dterm : Option[Term], pterm : Option[Term], con : Context) : Boolean = {
    (dterm,pterm) match {
      case (Some(d),Some(p)) => apply(d,p,con)
      case (None,None) => true
      case (_,_) => false
    }
  }
  
  def lengthChecker(term1 : Term, term2 : Term) : Boolean = {
    true
     //val len1N = normalize(length(term1))
     //val len2N = normalize(length(term2))
    /*
     (term1,len2N) match {
       case (OMI(m),OMI(n)) if (m == n) => true
       case (_,_) => 
         freeVars(len1N)
         freeVars(len1N)
         lengthSolver()
     }
     */
  }
}


object Test  {
  
  // just a random test file with THF theory
  val testfile = "/home/aivaras/TPTP/tptp/compiled/Problems/AGT/AGT031^2.omdoc"
  //TODO 
     // read omdoc version of a thf file   
     // should get a list of constants
     //check constants one by one - thf can only have one declaration anyway     
     //check a parsed constant immediatelly against all patterns OR get list of constants and then check      
    
//  var reader = new java.io.
    
//  val src = Source.fromFile(testfile)
  
  
    

  val tptpbase = DPath(URI("http://latin.omdoc.org/logics/tptp"))
  val pbbase = DPath(URI("http://oaff.omdoc.org/tptp/problems"))

  val baseType = new Pattern(OMID(tptpbase ? "THF0"), LocalName("baseType"),Context(), OMV("t") % OMS(tptpbase ? "Types" ? "$tType"))
  val typedCon = new Pattern(OMID(tptpbase ? "THF0"), LocalName("typedCon"), OMV("A") % OMS(tptpbase ? "Types" ? "$tType") , OMV("c") % OMA(OMS(tptpbase ? "Types" ? "$tm"), List(OMV("A"))) )
  val axiom = new Pattern(OMID(tptpbase ? "THF0"), LocalName("axiom"), OMV("F") % OMA(OMS(tptpbase ? "Types" ? "$tm"),List(OMS(tptpbase ? "THF0" ? "$o"))) , OMV("c") % OMA(OMS(tptpbase ? "Types" ? "$tm"), List(OMV("A"))) )
  val controller = new Controller
  controller.handleLine("file pattern-test.mmt")
  controller.add(baseType)
  controller.add(typedCon)
  controller.add(axiom)
  
  
  
  def main(args : Array[String]) {
  
    val pc = new PatternChecker(controller)
        														
//    val testget = controller.globalLookup.getStructure(pbbase / "SomeProblem.omdoc" ? "SomeProblem")
    println("OK up till here")
    														// file name ? theory name ? constant name 
//    controller.get(pbbase)
    val conMu = controller.globalLookup.getConstant(pbbase  ? "SomeProblem" ? "mu")
    
//    val conMeq_ind = controller.globalLookup.getConstant(pbbase  ? "SomeProblem" ? "meq_ind")
    
    
//    var tmp1 = pc.patternCheck(List(ccon), baseType)
//    tmp1 match {
//      case None => 
//      case Some(a) => tmp1 =
//    }
    val testtest = pc.patternCheck(List(conMu), baseType)
    
    println(testtest.toString())
//    println(pc.patternCheck(List(conMeq_ind), baseType).toString())
//    println(pc.patternCheck(List(conMu), typedCon).toString())
//    println(pc.patternCheck(List(conMu), axiom).toString())
    
//    pc.patternCheck()
    
//    log("works!")
    
  }
  
//  src.close()
    
}
























