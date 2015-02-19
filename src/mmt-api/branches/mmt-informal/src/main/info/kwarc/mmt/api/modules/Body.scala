package info.kwarc.mmt.api.modules
import info.kwarc.mmt.api._
import info.kwarc.mmt.api.libraries._
import info.kwarc.mmt.api.symbols._
import info.kwarc.mmt.api.objects._
import info.kwarc.mmt.api.utils._

import scala.xml.Node

/**
 * Body represents the content of modules, i.e., a set of declarations.
 * 
 * It is mixed into theories and links to hold the symbols and assignments, respectively.
 * It provides a name-indexed hash map named declarations, and a set of unnamed declarations.
 * For named declarations, the name must be unique; for unnamed declarations, the field implictKey must be non-empty and unique. 
*/
trait Body {
   /** the set of named statements, indexed by name
    * if a statement has an alternativeName, it occurs twice in this map
    */
   protected val statements = new scala.collection.mutable.HashMap[LocalName,Declaration]
   /** all declarations in reverse order of addition */
   protected var order : List[Declaration] = Nil
   /** true iff a declaration for a name is present */ 
   def declares(name: LocalName) = statements.isDefinedAt(name)
   /** the set of names of all declarations */
   def domain = statements.keySet
   /** retrieve a named declaration, may throw exception if not present */ 
   def get(name : LocalName) : Declaration = statements(name)
   /** retrieve a declaration, None if not present */ 
   def getO(name : LocalName) : Option[Declaration] =
      try {Some(get(name))}
      catch {case _ : Throwable => None}
   /** same as get(LocalName(name)) */ 
   def get(name : String) : Declaration = get(LocalName(name))
   /** retrieves the most specific applicable declaration
    * @param name the name of the declaration
    * @param rest the suffix that has been split off so far; this argument should be omitted in calls from outside this class 
    * @return the most specific (longest prefix of name) known declaration (if any) and the remaining suffix
    */
   def getMostSpecific(name: LocalName, rest : LocalName = LocalName(Nil)) : Option[(Declaration, LocalName)] =
      statements.get(name) match {
         case Some(d) => Some((d, rest))
         case None => name match {
            case LocalName(Nil) => None //should be impossible
            case !(n) => None
            case ln \ n => getMostSpecific(ln, n / rest)
         }
      }
   /** adds a named or unnamed declaration, throws exception if name already declared */ 
   def add(s : Declaration) {
	      val name = s.name
         if (statements.isDefinedAt(name)) {
            throw AddError("a declaration for the name " + name + " already exists")
         }
         s.alternativeName foreach {a =>
            if (statements.isDefinedAt(a))
               throw AddError("a declaration for the name " + a + " already exists")
            statements(a) = s
         }
         statements(name) = s
         order = s :: order
   }
   /** delete a named declaration (does not have to exist)
    *  @return the deleted declaration
    */
   def delete(name : LocalName): Option[Declaration] = {
      statements.get(name) map {s =>
         statements -= s.name
         s.alternativeName foreach {a => statements -= a} 
         order = order.filter(_.name != name)
         s
      }
   }
   /** updates a named declaration (preserving the order) */
   def update(s : Declaration) = {
	   replace(s.name, s)
   }
   /** replaces a named declaration with others (preserving the order) */
   //TODO alternativeNames
   def replace(name: LocalName, decs: Declaration*) {
      var seen : List[Declaration] = Nil
      var tosee : List[Declaration] = order
      var continue = true
      while (continue && ! tosee.isEmpty) {
         val hd = tosee.head
         if (hd.name == name) {
            order = seen.reverse ::: decs.reverse.toList ::: tosee.tail
            continue = false
         } else {
            seen = hd :: seen
            tosee = tosee.tail
         }
      }
      if (continue) throw AddError("no declaration " + name + " found")
      statements -= name
      decs foreach {d =>
        if (statements.isDefinedAt(d.name))
           throw AddError("a declaration for the name " + d.name + " already exists")
        statements(d.name) = d
      }
   }
   /** true iff no declarations present */
   def isEmpty = statements.isEmpty
   /** the list of declarations in the order of addition, includes declarations generated by MMT */
   def getDeclarations: List[Declaration] = order.reverse
   /** the list of declarations in the order of addition, excludes declarations generated by MMT */
   def getPrimitiveDeclarations = getDeclarations.filter(! _.isGenerated)
   /** the list of declarations using elaborated declarations where possible */
   def getDeclarationsElaborated = getDeclarations.filter(_.inElaborated)
   /** the list of declarations in the order of addition as an iterator */
   def iterator = getDeclarations.iterator
   def children = getDeclarations
   protected def innerNodes = getPrimitiveDeclarations.map(_.toNode)
   protected def innerNodesElab = getDeclarationsElaborated.map(_.toNode)
   protected def innerString =
      if (getPrimitiveDeclarations.isEmpty) ""
      else getPrimitiveDeclarations.map("\t" + _.toString).mkString(" = {\n", "\n", "\n}")
}