package info.kwarc.mmt.api.objects
import info.kwarc.mmt.api._
//import info.kwarc.mmt.api.objects._
import libraries._
import modules._
import symbols._
import frontend._
import objects.Conversions._
import scala.collection.mutable.{HashSet,HashMap}

/** A wrapper around a Judgement to maintain meta-information while a constraint is delayed */
class DelayedConstraint(val constraint: Judgement) {
  private val freeVars = constraint.freeVars
  private var activatable = false
  /** This must be called whenever a variable that may occur free in this constraint has been solved */
  def solved(names: List[LocalName]) {
     if (! activatable && (names exists {name => freeVars contains name})) activatable = true
  }
  /** @return true iff, since delaying, a variable has been solved that occurs free in this Constraint */
  def isActivatable: Boolean = activatable
  override def toString = constraint.toString
}

/**
 * A Solver is used to solve a system of constraints about Term's given as judgments.
 * The judgments may contain unknown variables (also called meta-variables or logic variables);
 * variables may represent any MMT term, i.e., object language terms, types, etc.;
 * the solution is a Substitution that provides a closed Term for every unknown variable.
 * (Higher-order abstract syntax should be used to represent unknown terms with free variables as closed function terms.) 
 * The Solver decomposes the judgments individually by applying typing rules, collecting (partial) solutions along the way and possibly delaying unsolvable judgments.
 * Unsolvable constraints are delayed and reactivated if later solving of unknowns provides further information.
 * @param an MMT controller that is used to look up Rule's and Constant's. No changes are made to the controller.
 * @param unknowns the list of all unknown variables including their types and listed in dependency order;
 *   unknown variables may occur in the types of later unknowns.
 * Use: Create a new instance for every problem, call apply on all constraints, then call getSolution.  
 */
class Solver(controller: Controller, unknowns: Context) {
   /** tracks the solution, initially equal to unknowns, then a definiens is added for every solved variable */ 
   private var solution : Context = unknowns
   /** the unknowns that were solved since the last call of activate (used to determine which constraints are activatable) */
   private var newsolutions : List[LocalName] = Nil
   /** tracks the delayed constraints, in any order */ 
   private var delayed : List[DelayedConstraint] = Nil
   /** true if unresolved constraints are left */
   def hasUnresolvedConstraints : Boolean = ! delayed.isEmpty
   /** true if unsolved variables are left */
   def hasUnsolvedVariables : Boolean = solution.toSubstitution.isEmpty
   /** the solution to the constraint problem
    * @return None if there are unresolved constraints or unsolved variables; Some(solution) otherwise 
    */
   def getSolution : Option[Substitution] = if (delayed.isEmpty) solution.toSubstitution else None

   /** shortcut the controller's report instance for logging */ 
   private val report = controller.report
   /** shortcut for the log function; in the MMT shell, call "log+ object-checker" to activate logging for this component */
   private def log(s: => String) = report("object-checker", s)
   /** shortcut for the global Lookup of the controller; used to lookup Constant's */
   private val content = controller.globalLookup
   /** shortcut for the RuleStore of the controller; used to retrieve Rule's */
   private val ruleStore = controller.extman.ruleStore
   
   /** @return a string representation of the current state */ 
   override def toString = {
      "  unknowns: " + unknowns.toString + "\n" +
      "  solution: " + solution.toString + "\n" +
      "  constraints:\n" + delayed.mkString("  ", "\n    ", "") 
   }
   
   /** delays a constraint for future processing
    * @param c the Judgement to be delayed
    * @return true (i.e., delayed Judgment's always return success)
    */
   private def delay(c: Judgement): Boolean = {
      val dc = new DelayedConstraint(c)
      delayed = dc :: delayed
      true
   }
   /** activates a previously delayed constraint if one of its free variables has been solved since
    * @return whatever application to the delayed judgment returns
    */
   private def activate: Boolean = {
      delayed foreach {_.solved(newsolutions)}
      val res = delayed find {_.isActivatable} match {
         case None => true
         case Some(dc) =>
           delayed = delayed filterNot (_ == dc)
           apply(dc.constraint)
      }
      newsolutions = Nil
      res
   }
   /** registers the solution for a variable
    * If a solution exists already, their equality is checked.
    * @param solve the solved variable
    * @param the solution; must not contain object variables, but may contain meta-variables that are declared before the solved variable
    * @return true unless the solution differs from an existing one
    */
   private def solve(name: LocalName, value: Term): Boolean = {
      val (left, solved :: right) = solution.span(_.name != name)
      if (solved.df.isDefined)
         checkEquality(value, solved.df.get, solved.tp)(Context())
      else {
         solution = left ::: solved.copy(df = Some(value)) :: right
         newsolutions = name :: newsolutions
         true
      }
   }
   /** applies this Solver to one Judgement
    *  This method can be called multiple times to solve a system of constraints.
    *  @param j the Judgement
    *  @return false if the Judgment is definitely not provable; true if the it has been proved or delayed  
    */
   def apply(j: Judgement): Boolean = {
     log("judgment: " + j)
     log("state: \n" + this.toString)
     val subs = solution.toPartialSubstitution
     val mayhold = j match {
        case Typing(con, tm, tp) =>
           checkTyping(tm ^ subs, tp ^ subs)(con ^ subs)
        case Equality(con, tm1, tm2, tp) =>
           def prepare(t: Term) = simplify(t ^ subs)(con)
           checkEquality(prepare(tm1), prepare(tm2), tp map prepare)(simplifyCon(con ^ subs))
     }
     if (mayhold) activate else false
   }

   /** proves a Typing Judgment by recursively applying TypingRule's and InferenceRule's.
    * @param tm the term
    * @param tp its type
    * @param context their context
    * @return false if the Judgment is definitely not provable; true if the it has been proved or delayed
    * 
    * This method should not be called by users (instead, call apply). It is only public because it serves as a callback for Rule's.
    */
   def checkTyping(tm: Term, tp: Term)(implicit context: Context): Boolean = {
      log("typing: " + context + " |- " + tm + " : " + tp)
      report.indent
      val res = tm match {
         // the foundation-independent cases
         case OMV(x) => (unknowns ++ context)(x).tp match {
            case None => false //untyped variable type-checks against nothing
            case Some(t) => checkEquality(t, tp, None)
         }
         case OMS(p) =>
            val c = content.getConstant(p)
            c.tp match {
               case None => c.df match {
                  case None => false //untyped, undefined constant type-checks against nothing
                  case Some(d) => checkTyping(d, tp) // expand defined constant 
               }
               case Some(t) => checkEquality(t, tp, None)
            }
         // the foundation-dependent cases
         case tm =>
            limitedSimplify(tp) {t => t.head flatMap {h => ruleStore.typingRules.get(h)}} match {
               case (tpS, Some(rule)) => rule(this)(tm, tpS)
               case (tpS, None) =>
                   // either this is an atomic type, or no typing rule is known
                   inferType(tm) match {
                      case Some(itp) => checkEquality(itp, tpS, None)
                      case None => delay(Typing(context, tm, tpS))
                   }
            }
      }
      report.unindent
      res
   }
   
   /** infers the type of a term by applying InferenceRule's
    * @param tm the term
    * @param context its Context
    * @return the inferred type, if inference succeeded
    *
    * This method should not be called by users (instead, call apply to a typing judgement with an unknown type). It is only public because it serves as a callback for Rule's.
    */
   def inferType(tm: Term)(implicit context: Context): Option[Term] = {
      log("inference: " + context + " |- " + tm + " : ?")
      report.indent
      val res = tm match {
         //foundation-independent cases
         case OMV(x) => (unknowns ++ context)(x).tp
         case OMS(p) =>
            val c = content.getConstant(p)
            c.tp orElse {
               c.df match {
                  case None => None
                  case Some(d) => inferType(d) // expand defined constant 
               }
            }
         //foundation-dependent cases
         case tm =>
            val hd = tm.head.get //TODO
            limitedSimplify(tm) {t => t.head flatMap {h => ruleStore.inferenceRules.get(h)}} match {
               case (tmS, Some(rule)) => rule(this)(tmS)
               case (_, None) => None
            }
      }
      report.unindent
      log("inferred: " + res.getOrElse("failed"))
      res
   }
   
   /** proves an Equality Judgment by recursively applying EqualityRule's and other Rule's.
    * @param tm1 the first term
    * @param tm2 the second term
    * @param tpOpt their type; if given it is used as guidance for the selection of Rule's
    *   The well-typedness of tm1 and tm2 is neither assumed nor guaranteed;
    *   however, if tpOpt is given and the terms are well-typed, they must type-check against tpOpt.
    * @param context their context
    * @return false if the Judgment is definitely not provable; true if the it has been proved or delayed
    * 
    * This method should not be called by users (instead, call apply). It is only public because it serves as a callback for Rule's.
    */
   def checkEquality(tm1: Term, tm2: Term, tpOpt: Option[Term])(implicit con: Context): Boolean = {
      log("equality: " + con + " |- " + tm1 + " = " + tm2 + " : " + tpOpt)
      // first, we check for some common cases where it's redundant to do induction on the type
      // identical terms
      if (tm1 == tm2) return true
      // solve an unknown
      val solved = tryToSolve(tm1, tm2) || tryToSolve(tm2, tm1)
      if (solved) return true

      // use the type for foundation-specific equality reasoning
      
      // first infer the type if it has not been given in tpOpt
      val tp = tpOpt match {
        case Some(tp) => tp
        case None =>
           val itp = inferType(tm1) orElse inferType(tm2)
           itp.getOrElse(return false)
      }
      // try to simplify the type until an equality rule is applicable 
      limitedSimplify(tp) {t => t.head flatMap {h => ruleStore.equalityRules.get(h)}} match {
         case (tpS, Some(rule)) => rule(this)(tm1, tm2, tpS)
         case (tpS, None) =>
            // this is either a base type or an equality rule is missing
            // TorsoNormalForm is useful to inspect terms of base type
            val tm1T = TorsoForm.fromHeadForm(tm1)
            val tm2T = TorsoForm.fromHeadForm(tm2)
            val heads = tm1T.heads
            // TODO: if the torsos are constants but not equal, try to make them equal by expanding definitions
            if (tm1T.torso == tm2T.torso && heads == tm2T.heads) {
               //the two terms have the same shape, i.e., same torso and same heads
               //we can assume heads != Nil; otherwise, tm1 == tm2 would hold
               ruleStore.atomicEqualityRules.get(heads.head) match {
                  case Some(rule) => rule(this)(tm1, tm2, tpS)   //apply the rule for the outermost head 
                  case None => 
                    //default: apply congruence rules backwards, amounting to initial model semantics, i.e., check for same number and equality of arguments everywhere
                    (tm1T.apps zip tm2T.apps) forall {case (Appendages(_,args1),Appendages(_,args2)) =>
                        if (args1.length == args2.length)
                           (args1 zip args2) forall {case (a1, a2) => checkEquality(a1, a2, None)}
                        else false
                    }
               }
            } else
              //TODO: in some cases, we may conclude false right away
              delay(Equality(con, tm1, tm2, Some(tp)))
      }
   }
   /** tries to solve an unknown occurring as the torso of tm1 in terms of tm2.
    * It is an auxiliary function of checkEquality because it is called twice to handle symmetry of equality.
    */
   private def tryToSolve(tm1: Term, tm2: Term)(implicit context: Context): Boolean = {
      tm1 match {
         //foundation-independent case: direct solution of an unknown variable
         case OMV(m) if unknowns.isDeclared(m) && tm2.freeVars.isEmpty => //forall {v => context.isDeclaredBefore(v,m)}
            solve(m, tm2)
         //apply a foundation-dependent solving rule selected by the head of tm1
         case TorsoNormalForm(OMV(m), Appendages(h,_) :: _) if unknowns.isDeclared(m) && ! tm2.freeVars.contains(m) => //TODO what about occurrences of m in tm1?
            ruleStore.solutionRules.get(h) match {
               case None => false
               case Some(rule) => rule(this)(tm1, tm2)
            }
         case _ => false
      }
   }
  
   /** applies all ForwardSolutionRules of the given priority
    * @param priority exactly the rules with this Priority are applied */
   //TODO call this method at appropriate times
   private def forwardRules(priority: ForwardSolutionRule.Priority): Boolean = {
      val results = unknowns.zipWithIndex map {
         case (vd @ VarDecl(x, Some(tp), None, _*), i) =>
            implicit val con : Context = unknowns.take(i)
            limitedSimplify(tp) {t => t.head flatMap {h => ruleStore.forwardSolutionRules.get(h)}} match {
               case (tpS, Some(rule)) if rule.priority == priority => rule(this)(vd)
               case _ => false 
            }
         case _ => false
      }
      results.exists(_ == true)
   }
   
   /** applies ComputationRule's to simplify a term until some condition is satisfied;
    *  A typical case is transformation into weak head normal form.
    *  @param tm the term to simplify (It may be simple already.)
    *  @param simple a term is considered simple if this function returns a non-None result
    *  @param its context
    *  @return (tmS, Some(a)) if tmS is simple and simple(tm)=tmS; (tmS, None) if tmS is not simple but no further simplification rules are applicable
    */  
   private def limitedSimplify[A](tm: Term)(simple: Term => Option[A])(implicit context: Context): (Term,Option[A]) = {
      simple(tm) match {
         case Some(a) => (tm,Some(a))
         case None => tm.head match {
            case None => (tm, None)
            case Some(h) =>
               ruleStore.computationRules.get(h) match {
                  case None => (tm, None) //TODO test for definition expansion
                  case Some(rule) =>
                     rule(this)(tm) match {
                        case Some(tmS) => limitedSimplify(tmS)(simple)
                        case None => (tm, None) 
                     }
               }
         }
      }
   }
   /** applies ComputationRule's until no further rules are applicable.
    * @param tm the term
    * @param context its context
    * @return the simplified Term
    * 
    * This method should not be called by users. It is only public because it serves as a callback for Rule's.
    */
   def simplify(tm: Term)(implicit context: Context): Term = tm.head match {
      case None => tm
      case Some(h) => ruleStore.computationRules.get(h) match {
         case None => tm
         case Some(rule) =>
            rule(this)(tm) match {
               case Some(tmS) => simplify(tmS)
               case None => tm
            }
      }
   }
   /** applies simplify to all Term's in a Context
    * @param context the context
    * @return the simplified Context
    */
   private def simplifyCon(context: Context) = context mapTerms {case (con, t) => simplify(t)(context ++ con)}
}

