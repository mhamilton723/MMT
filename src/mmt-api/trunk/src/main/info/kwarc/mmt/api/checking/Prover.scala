package info.kwarc.mmt.api.checking

import info.kwarc.mmt.api._
import objects._
import frontend._
import symbols._
import objects.Conversions._

class Prover(controller: Controller) {
   val introRules = controller.extman.ruleStore.introProvingRules
   val elimRules = controller.extman.ruleStore.elimProvingRules
   def applicable(goal: Term)(implicit stack: Stack) : List[ApplicableProvingRule] = {
      val head = goal.head.getOrElse(return Nil)

      // first look for all intro rules, if any return them
      val possibleIntro = introRules(head).toList flatMap {r => r(goal).toList}
      if (possibleIntro != Nil) return possibleIntro

      // if none, look for applicable elim rules by inspecting the current theory and context
      // axioms holds the list of applicable axiom rules, which are found along the way
      var axioms : List[ApplicableProvingRule] = Nil
      def doType(src: Term, tpOpt: Option[Term]) : List[ApplicableProvingRule] = {
            val tp = tpOpt.getOrElse(return Nil)
            if (tp == goal) axioms ::= axiomRule(src) 
            val tpH = tp.head.getOrElse(return Nil)
            elimRules(tpH).toList flatMap {r => r(src, tp, goal).toList}
      }
      val possibleElim = stack.context flatMap {
         case IncludeVarDecl(p,_) =>
            val decls = controller.localLookup.getDeclaredTheory(p).getConstants
            decls flatMap {c => doType(c.toTerm, c.tp)}
         case vd => doType(vd.toTerm, vd.tp)
      } 
      axioms.reverse ::: possibleElim
   }
   
   private def axiomRule(ax: Term) = new ApplicableProvingRule {
      def label = ax.toString
      def apply() = ax
   }
}
