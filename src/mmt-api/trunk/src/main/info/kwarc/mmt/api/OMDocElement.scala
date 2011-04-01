package info.kwarc.mmt.api
import info.kwarc.mmt.api.presentation._
import scala.xml.Node

/** 
 * A StructuralElement is any knowledge item on the document, module, or symbol level.
 * The structural elements are subdivided according to their dimension: content, presentation, or narration.
 */
trait StructuralElement extends Content with MetaData {
   /** the MMT URI of the element */
   def path : Path
   /** the containing knowledge item, a URL if none */
   def parent : Path
   /** XML representation */
   def toNode : Node
   /** the role, the non-terminal in the MMT grammar producing this item */  
   def role : Role
   /** the components are an abstract definition of the children of a knowledge item */
   def components : List[Content]
   /** The presentation is determined by Notations according to role and components. */ 
   def presentation(lpar : LocalParams) = ByNotation(NotationKey(Some(path), role), components, lpar)
   /** If a StructuralElement has been generated (as opposed to being physically present in the document),
    * this gives its origin.
    * The origin must be set by overriding the field when creating the ContentElement. 
    */
   private var origin : Option[Origin] = None
   def setOrigin(o: Origin) {origin = Some(o)}
   def getOrigin = origin
   def isGenerated = origin.isDefined
}

/**
 * A ContentElement is any knowledge item that is used to represent mathematical content.
 * These are the core MMT items such as modules, and symbols.
 * This includes virtual knowledge items.
 */
trait ContentElement extends StructuralElement {
}

/**
 * A PresentationElement is any knowledge item element that is used to represent notations.
 * These includes styles and notations.
 */
trait PresentationElement extends StructuralElement

/**
 * A NarrativeElement is any OMDoc element that is used to represent narration and document structure.
 * These include documents and cross-references.
 */
trait NarrativeElement extends StructuralElement

/** A RelationalElement is any element that is used in the relational representation of MMT content.
 * These include the unary and binary predicates occurring in an MMT ABox.
 * They do not correspond to XML elements in an OMDoc element and thus do not extend StructuralElement. 
 */
trait RelationalElement {
   /** the URL from which this item originated, currently not implemented */
   //val parent : Path = null //TODO origin of relational items
   /** the MMTURI of the "about" item in the RDF sense */
   val path : Path
   /** XML representation */
   def toNode : scala.xml.Node
}

/**
 * The trait Content is mixed into any class that can be rendered using notations.
 */
trait Content {
   /** returns instructions how to present this item based on the current presentation context */
   def presentation(lpar : LocalParams) : PresentationData
   /** XML representation */
   def toNode : Node
}

/**
 * The trait MetaData is mixed into any class that can carry metadata (not used yet)
 */
trait MetaData {
   /** the key-value set of metadata items */
   val metadata = new scala.collection.mutable.HashMap[GlobalName,String]
}