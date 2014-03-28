package info.kwarc.mmt.api.symbols
import info.kwarc.mmt.api._
import objects._
import notations._
import modules._
import moc._
import presentation.{StringLiteral, Omitted}

/**
 * A Constant represents an MMT constant.<p>
 * 
 * @param home the {@link info.kwarc.mmt.api.objects.Term} representing the parent theory
 * @param name the name of the constant
 * @param tp the optional type
 * @param df the optional definiens
 * @param rl the role of the constant
 */
class Constant(val home : Term, val name : LocalName, val alias: Option[LocalName],
               val tpC : TermContainer, val dfC : TermContainer, val rl : Option[String], val notC: NotationContainer) extends Declaration {
  override val alternativeName = alias
  def toTerm = OMID(path)

  def role = Role_Constant(rl)
  
  def tp = tpC.get
  def df = dfC.get
  def not = notC.parsing
  
  override def compNames = List((TypeComponent,1), (DefComponent, 2))
  def components = List(StringLiteral(name.toPath), tp.getOrElse(Omitted), df.getOrElse(Omitted),
                                    rl.map(StringLiteral(_)).getOrElse(Omitted))
  def getComponents = List((TypeComponent, tpC), (DefComponent, dfC)) ::: notC.getComponents
  def getDeclarations = Nil
  def children = List(tp,df, not).flatten
  def toNode =
     <constant name={name.toPath} alias={alias.map(_.toPath).getOrElse(null)} role={rl.getOrElse(null)}>
       {getMetaDataNode}
       {if (tp.isDefined) <type>{tp.get.toOBJNode}</type> else Nil}
       {if (df.isDefined) <definition>{df.get.toOBJNode}</definition> else Nil}
       {notC.toNode}
     </constant>
  override def toString = name.toString + alias.map(" @ " + _).getOrElse("") +
     tp.map(" : " + _).getOrElse("") + df.map(" = " + _).getOrElse("") + notC.toString
}

/** helper object */
object Constant {
   /** factory that hides the TermContainer's
    * 
    * all arguments are as in the primary constructor, except the terms, which are wrapped in the 
    * TermContainer factory
    */
   def apply(home : Term, name : LocalName, alias: Option[LocalName], tp: Option[Term], df: Option[Term],
             rl : Option[String], not: NotationContainer = NotationContainer()) =
      new Constant(home, name, alias, TermContainer(tp), TermContainer(df), rl, not)
}