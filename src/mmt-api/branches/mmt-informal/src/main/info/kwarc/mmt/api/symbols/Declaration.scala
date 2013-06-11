package info.kwarc.mmt.api.symbols
import info.kwarc.mmt.api._
import info.kwarc.mmt.api.modules._
import info.kwarc.mmt.api.objects._
import info.kwarc.mmt.api.libraries._
import info.kwarc.mmt.api.presentation._

/**
 * Symbol unifies MMT symbols and MMT assignments.
 * 
 * These are the named statements living in [[info.kwarc.mmt.api.modules.Module]]s
 */
abstract class Symbol extends ContentElement {
   /** the containing module */
   val parent = home.toMPath
   /** the containing module
    * 
    * this is almost always OMMOD(p:MPath),
    * the main exception are generated anonymous modules
    */
   val home : Term
   /** the local name in the containing module
    * 
    *  for symbols: the name of the symbols
    *  
    *  for assignments: the name of the symbols to which a value is assigned 
    */
   val name : LocalName
   /** an alternative name
    * 
    *  None by default; overridden in particular by Constant
    */
   val alternativeName: Option[LocalName] = None
   /** the full MMT URI, parent ? name */
   def path = GlobalName(home, name)
   /** the component used to identify anonymous declarations, e.g., the from of an import, None by default but may be overridden */ 
   def implicitKey : Option[MPath] = None
   /** every MMT symbol takes a list of parameters
    * empty by default, may be overridden when constructing instances
    */
   val parameters = Context()

}