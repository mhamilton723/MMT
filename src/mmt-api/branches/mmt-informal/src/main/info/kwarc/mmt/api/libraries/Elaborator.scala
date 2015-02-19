package info.kwarc.mmt.api.libraries
import info.kwarc.mmt.api._
import frontend._
import modules._
import symbols._
import patterns._
import objects._
import utils.MyList.fromList
import collection.immutable.{HashSet, HashMap}

/* idea: elaboration is structure level simplification
 * defined -> declared module
 * mod exp -> materialization
 * structure -> elaboration
 * constant of THY type <-> theory
 * view -> theory ?
 */

/**
 * Elaborates modules by enriching with induced statements
 */
class ModuleElaborator(controller : Controller) {
  private val content = controller.globalLookup
  private val modules = controller.memory.content.getAllPaths.map(controller.globalLookup.get(_)).toList

  var totalImports : Int = 0
  var thys : Int = 0
  var decls : Int = 0


  def printStatistics = {
    println("totalImports : " + totalImports)
    println("thys : " + thys)
    println("decls : " + decls)
  }

  private def rewrite(t : Term)(implicit rules : HashMap[Path,Term]) : Term = t match {
    case OMID(path) => rules.get(path) match {
      case None => t
      case Some(tm) => tm
    }
    case OMA(f, args) => OMA(rewrite(f), args.map(rewrite))
    case OMBINDC(b, context, scopes) =>
      val nwctx = Context(context.variables.map(v =>
        VarDecl(v.name, v.tp.map(rewrite), v.df.map(rewrite), v.not)
      ) :_ *)
      OMBINDC(rewrite(b), nwctx, scopes.map(rewrite))
    case OMM(arg,via) => OMM(rewrite(arg), rewrite(via))
    case OMATTR(arg, key, value) => OMATTR(rewrite(arg), key, rewrite(value)) //TODO maybe handle key (here) & uri (below)
    case _ => t
  }

  def getIncludes(t : Term) : List[DeclaredStructure] = {
    content.getTheory(t.toMPath).getDeclarations flatMap {
      case s : DeclaredStructure if s.isInclude => List(s)
      case _ => Nil
    }
  }

  private def importsTo(t : DeclaredTheory) : List[DeclaredStructure] = {
    t.getDeclarations.flatMap {
      case i @ Include(_, fromPath, _) =>
         i.asInstanceOf[DeclaredStructure] :: getIncludes(OMMOD(fromPath))
      case s: DeclaredStructure =>
         List(s)
      case _ => Nil
    }
  }

  /**
   * @param e the ContentElement to elaborate
   * @param cont a continuation function to call on every generated StructuralElement (e.g., Controller.add)
   */
  def apply(e: StructuralElement)(implicit cont: StructuralElement => Unit) : Unit = e match {
    case t : DeclaredTheory =>
      val elabImports : List[DeclaredStructure] = importsTo(t)

      totalImports += elabImports.length
      thys += 1
      var newDecs = new HashSet[Constant]()
      var rewriteRules = new HashMap[Path,Term]
      //s.home == t.path
      elabImports map {case SimpleStructure(s: DeclaredStructure, p) =>
            if (s.domain.isEmpty) { //import is essentially an include
              val impThy = content.getTheory(p)
              impThy.getDeclarations collect {
                case c : Constant =>
                  val nwName = ComplexStep(impThy.path) / c.name
                  val nwHome = OMMOD(t.path)
                  rewriteRules += (c.home.toMPath ? c.name -> OMID(nwHome.toMPath ? nwName))
                  newDecs += c
              }
            } else { // import is a struct defined by assignments
              s.domain map {x =>
                val ass = s.get(x)
                ass match {
                  case conAss : Constant =>
                    val genCons = Constant(conAss.home, conAss.name, conAss.alias, None, conAss.df, None)
                    newDecs += genCons
                  case _ => None
                }
              }
            }
      }
      val nt = new DeclaredTheory(t.parent, t.name, t.meta)
      newDecs foreach {
        case c : Constant =>
          val nwName = c.name
          val nwHome = OMMOD(t.path)

          val ntp = c.tp.map(rewrite(_)(rewriteRules))
          val ndf = c.tp.map(rewrite(_)(rewriteRules))

          val nc = Constant(nwHome, nwName, c.alias, ntp, ndf, c.rl, c.notC)
          nt.add(nc)
          decls += 1
        case _ => nt.add(_)
      }
      t.getDeclarations collect {
        case c : Constant =>
          newDecs += c
          nt.add(c)
          decls += 1
      }
      cont(nt)
      modules collect {
        case v : DeclaredView =>
          v.from match {
            case OMMOD(p) =>
              var viewRewrRules = new HashMap[Path,Term]
              v.getDeclarations collect {
                case ca : Constant =>
                  ca.df.foreach {t =>
                     viewRewrRules += (p ? ca.name -> t)
                  }
              }
              if (p == t.path) {    // view from this theory
              val nwIndThy = new DeclaredTheory(v.to.toMPath.parent, LocalName(v.to.toMPath.name.last + "^" +  escape(v.path.toPath) + "^" + escape(t.path.toPath)), t.meta)
                newDecs foreach { c =>
                  val nc = Constant(c.home, c.name, c.alias, c.tp.map(rewrite(_)(viewRewrRules)), c.df.map(rewrite(_)(viewRewrRules)), c.rl, c.notC)
                  nwIndThy.add(nc)
                  decls += 1
                }
                cont(nwIndThy)
              }  else {
                elabImports foreach {i =>
                  i.from match {
                    case OMMOD(path) =>
                      if (p == path) {    // view from some imported theory

                      val nwIndThy = new DeclaredTheory(t.parent, LocalName(v.to.toMPath.name.last + "^" +  escape(v.path.toPath) + "^" + escape(t.path.toPath)), t.meta)
                        newDecs foreach { c =>
                          val nc = Constant(c.home, c.name, c.alias, c.tp.map(rewrite(_)(viewRewrRules)), c.df.map(rewrite(_)(viewRewrRules)), c.rl, c.notC)

                          nwIndThy.add(nc)
                          decls += 1
                        }
                        cont(nwIndThy)
                      }
                  }
                }
              }
          }
      }
    case v : DeclaredView => cont(v)
    case _ => None
  }

  def escape(s : String) = {
    s.replace("/","|").replace("?","!")
  }
  
  def gatherConstants(thy : DeclaredTheory) : List[Constant] = {
    var constants : List[Constant] = Nil 		
    thy.getDeclarations foreach {
      case c : Constant => constants = constants ::: List(c) //MPath may be irrelevant, otherwise needs changing
      case _ => 
    }
    constants
  }
  
  def flatten(thy : DeclaredTheory) : DeclaredTheory = {
    var includes : HashSet[MPath] = new HashSet[MPath]()
    var constants : List[Constant] = Nil
    thy.getDeclarations collect {
      case s : DeclaredStructure =>
      if (s.isInclude) {
        val inclPath = s.from.toMPath
        controller.get(inclPath) match {
          case inclThy : DeclaredTheory => 
            val flatInclThy = flatten(inclThy)
            constants = constants ::: gatherConstants(flatInclThy)
          case _ => 
        }
      }
      case c : Constant => 
        constants = constants ::: c :: Nil
    }
    
   val nt = new DeclaredTheory(thy.parent, thy.name, thy.meta)
   constants.foreach(nt add _)
   nt    
  }
}

