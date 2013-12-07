package info.kwarc.mmt.api.documents
import info.kwarc.mmt.api._
import info.kwarc.mmt.api.presentation.{XMLLiteral}
import objects._


object Narration {
  def parseNarrativeObject(n : scala.xml.Node)(implicit dpath : DPath) : NarrativeObject = n.label match {
    case "#PCDATA" => new NarrativeText(n.toString)
    case "OMOBJ" => new NarrativeTerm(Obj.parseTerm(n, dpath))
    case "ref" if n.prefix == "omdoc" => 
      val targetS = (n \ "@target").text
      val target = Path.parse(targetS, dpath)
      val text = n.child.mkString(" ")
      val self = (n \ "@self").text match {
        case "true" => true 
        case _ => false
      }
      new NarrativeRef(target, text, self)
    case _ => 
      val child = n.child.map(parseNarrativeObject)
      new NarrativeNode(n, child.toList)
  } 
}

trait NarrativeObject extends Content {
  def toNode : scala.xml.Node 
  def role = Role_NarrativeObject
  def governingPath = None
}

class NarrativeText(val text : String) extends NarrativeObject {
  def toNode = scala.xml.Text(text)
  def components = presentation.StringLiteral(text) :: Nil
}

class NarrativeTerm(val term : Term) extends NarrativeObject {
  def toNode = new scala.xml.Elem("om", "OMOBJ", new scala.xml.PrefixedAttribute("xmlns","om", "http://www.openmath.org/OpenMath", scala.xml.Null), scala.xml.TopScope, false, term.toNode)
  def components = term :: Nil
}

class NarrativeRef(val target : Path, val text : String, val self : Boolean = false) extends NarrativeObject { //self is true e.g. for subjects of definitions "A 'prime number' p is ..."
  def toNode = <omdoc:ref target={target.toPath} self={self.toString}> {text} </omdoc:ref>
  def toHTML = self match {
    case false => <span jobad:href={target.toPath}> {text} </span>
    case true => <u><i> <span jobad:href={target.toPath}> {text} </span> </i></u>
  }
  
  def components = presentation.StringLiteral(text) :: Nil
}

class NarrativeNode(val node : scala.xml.Node,val child : List[NarrativeObject]) extends NarrativeObject {
  def toNode = new scala.xml.Elem(node.prefix, node.label, node.attributes, node.scope, false, child.map(_.toNode) :_ *)
  def components = child
}


/* A Narration instance represents unstructured narrative content
 * such as sentences and paragraphs.
 * The tokens it contains are words, sentences, or mathematical objects. 
 */
abstract class Narration(val dpath : DPath, val content : NarrativeObject) extends NarrativeElement with DocumentItem {
  def role : Role
  def path = dpath
  def parent = dpath
}

class PlainNarration(dpath : DPath, content : NarrativeObject) extends Narration(dpath, content) {
  def role = Role_Narration
  def toNode = 
    <plain-narration> 
      {getMetaDataNode}
      {content.toNode}
    </plain-narration>
  def components = content :: Nil //TODO
}

class Definition(dpath : DPath, val targets : List[GlobalName], content : NarrativeObject) extends Narration(dpath, content) {
  def role = Role_Narrative_Def
  def toNode = 
    <definition for={targets.mkString(" ")}> 
      {getMetaDataNode}
      {content.toNode} 
    </definition>  
  def components =  content :: Nil //TODO  
}

class Example(dpath : DPath, val targets : List[GlobalName], content : NarrativeObject) extends Narration(dpath, content) {
  def role = Role_Narration
  def toNode = 
    <example for="target"> 
      {getMetaDataNode}
      {content.toNode} 
    </example>  
  def components =  XMLLiteral(content.toNode) :: Nil //TODO      
}

class Assertion(dpath : DPath, val targets : List[GlobalName], content : NarrativeObject) extends Narration(dpath, content) {
  def role = Role_Narration
  def toNode = 
    <assertion for="target"> 
      {getMetaDataNode}
      {content.toNode} 
    </assertion>
  def components =  XMLLiteral(content.toNode) :: Nil //TODO      
}