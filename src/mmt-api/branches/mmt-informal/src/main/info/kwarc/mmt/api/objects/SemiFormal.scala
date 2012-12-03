package info.kwarc.mmt.api.objects
import info.kwarc.mmt.api._
import info.kwarc.mmt.api.symbols._
import info.kwarc.mmt.api.presentation._


trait SemiFormalModule extends Content {
   def governingPath = None
   def role = Role_value  
}

case class FormalDeclaration(decl : Declaration) extends SemiFormalModule {
  def components = List(decl)
  def toNode = decl.toNode
}

trait SemiFormalDecl extends SemiFormalModule

case class FormalComponent(comp : Obj) extends SemiFormalDecl {
  def components = List(comp)
  def toNode = comp.toNode
}

trait SemiFormalObject extends SemiFormalModule {
     def freeVars : List[LocalName]
}

case class Text(format: String, obj: String) extends SemiFormalObject {
   def components = List(StringLiteral(obj))
   def toNode = scala.xml.Text(obj)
   override def toString = obj
   def freeVars : List[LocalName] = Nil
}
case class XMLNode(obj: scala.xml.Node) extends SemiFormalObject {
   def components = List(XMLLiteral(obj))
   def toNode = obj
   override def toString = obj.toString
   def freeVars : List[LocalName] = Nil
}

case class Formal(obj: Term) extends SemiFormalObject {
   def components = List(obj)
   def toNode = obj.toNode
   override def toString = obj.toString
   def freeVars : List[LocalName] = obj.freeVars_
}

trait SemiFormalObjectList {
   val tokens: List[SemiFormalObject]
   def components = tokens
   override def toString = tokens.map(_.toString).mkString("", " ", "")
   def toNodeID(pos : Position) = <om:OMSF>{tokens.map(_.toNode)}</om:OMSF>
}