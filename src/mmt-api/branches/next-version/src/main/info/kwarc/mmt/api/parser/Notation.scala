package info.kwarc.mmt.api.parser

import scala.Int
import info.kwarc.mmt.api.metadata.HasMetaData
import info.kwarc.mmt.api.{Content, ImplementationError, GlobalName}
import info.kwarc.mmt.api.modules._
import info.kwarc.mmt.api.symbols._
import info.kwarc.mmt.api.objects._
import info.kwarc.mmt.api._

/**
 * An operator is something that takes arguments, e.g. and, or, forall
 */
class Operator(val name : GlobalName, val notation : Option[Notation]) {
  def this(name : GlobalName, not : Notation) = this(name, Some(not))
  
  def precedence = notation match {
    case None => 0
    case Some(not) => not.precedence
  }
  
  
  def toString(args : List[Formal]) = notation match { 
    case None => 
        var s = name.last
        args foreach {
          s += " " + _.toString
        }
        s
    case Some(not) => 
      var s = ""
      not.mrks.foreach {
        case Delimiter(value) => s += value + " "
        case arg : Argument => s += args(arg.pos).toString
      }
      s
  }

  override def toString = notation match {
    case Some(not) => not.toString
    case None => name.last
  }
}

/**
 * A notation is a list of markers which represent either strings to be matched or argument positions to be filled.
 * It also has notation properties encoding things such as associativity or precedence.
 * e.g. given the binary operator log(nr,base) one can define the notation with markers List("log",2,1)
 *      where "log" is a string marker, 2 is a argument marker for argument number 2 of log (base)
 *      and 1 for argument number 1 (nr). Then, log 2 16 is parsed as log(16,2)
 * @param mrks the list of markers
 * @param precedence  the precedence
 */
case class Notation(mrks : List[NotationElement], precedence : Int, isBinder : Boolean = false) extends HasMetaData {
  
  def markers : List[Delimiter] = mrks collect {case s : Delimiter => s}
  def args : List[List[Argument]] = mrks.foldLeft[List[List[Argument]]](Nil :: Nil)((r,m) => m match {case s : Delimiter => Nil :: (r.head.reverse :: r.tail) case a : Argument => (a :: r.head) :: r.tail}).reverse

  val argNr = args.flatten.length

  def getStrMarkers : List[String] = markers.flatMap( _  match {case Delimiter(s) => List(s) case _ => Nil})

  override def toString = mrks.mkString(" ") + "," + precedence

  def toNode =
    <text-notation isBinder={isBinder.toString}>
      <markers>
        {mrks.map(m => m.toNode)}
      </markers>
      <precedence>
        {precedence}
      </precedence>
    </text-notation>

}


object TextNotation {
  def parse(s : scala.xml.Node) : Notation = s match {
    case <text-notation>{xmlMarkers}{xmlPrecedence}</text-notation> =>
      val markers = parseMarkers(xmlMarkers)
      val precedence = parsePrecedence(xmlPrecedence)
      new Notation(markers, precedence, (s \ "@isBinder").text == "true")
    case _ => throw ParseError("Invalid XML representation of notation in \n" + s)
  }

  private def parsePrecedence(n : scala.xml.Node) : Int = n match {
    case <precedence>{value}</precedence> =>
      try {
        value.toString.toInt
      } catch {
        case _ => throw ParseError("Invalid XML representation of precedence (not an integer) in: \n" + n)
      }
    case _ => throw ParseError("Invalid XML representation of Precedence in: \n" + n)
  }

  private def parseMarkers(n : scala.xml.Node) : List[NotationElement] = n match {
    case <markers>{xmlMarkers @ _*}</markers> =>
      xmlMarkers.map(x => parseMarker(x)).toList
    case _ => throw ParseError("Invalid XML representation of marker list in: \n" + n)
  }

  private def parseMarker(n : scala.xml.Node) : NotationElement = n match {
    case <delimiter>{s}</delimiter> => Delimiter(s.toString)

    case <std-arg>{s}</std-arg> =>
      try {
        StdArg(s.toString.toInt)
      } catch {
        case _ => throw ParseError("Invalid XML representation of StdArg in: \n" + n)
      }
 
    case <seq-arg><pos>{s}</pos><delimiter>{delValue @ _*}</delimiter></seq-arg> =>
      try {
        SeqArg(s.toString.toInt, Delimiter(delValue.text))
      } catch {
        case _ => throw ParseError("Invalid XML representation of SeqArg in: \n" + n)
      }
    
    case _ => throw ParseError("Invalid XML representation of notation element in: \n" + n)

  }

  def parse(str : String) : Notation = {
    val isBinder = str.charAt(0) == '#'
    val s = if (isBinder) {
      str.substring(1)
    } else { 
      str
    }
    
    s.split(",").toList match {
      case not :: prec :: Nil =>        
        val precedence = prec.toInt        
        Notation(parseMarkers(not), precedence, isBinder) 
      case not :: Nil => 
        Notation(parseMarkers(not), 0, isBinder)
      case _ =>
        throw ParseError("Invalid notation declaration : " + s)
    }
  }
  
  private def parseMarkers(not : String) : List[NotationElement] = {
    val tokens = not.split(" ").toList
    val markers = tokens map {tk =>
      tk.split("/").toList match {
        case Nil => throw ImplementationError("unexpected error: string split returned empty result list")
        case value :: Nil => //std arg or delimiter
          try {
            StdArg(value.toInt)
          } catch {
            case _ => Delimiter(value)
          }
        case pos :: sep :: Nil => //seq arg
          try {
            SeqArg(pos.toInt, Delimiter(sep))
          } catch {
             case _ => throw ParseError("Invalid arg position in seq notation : " + not)
          }
      }
    }
    markers
  }
  

  //TODO add logging instead of print
  def present(con : Content, operators : List[Operator]) : String = {
    println("current con : " + con.toString)
    con match {
      case d : DeclaredTheory =>
        println(d.path)
        "%sig " + d.path.last + " = {\n" + d.components.map(c => "  " + present(c, operators)).filterNot(_ == "  ").mkString("\n")+ "\n}."
      
      case OMID(meta : MPath) => 
        "%meta " + meta.doc.last + "?" + meta.name + "."
      case c : Constant =>
        println("constant : " + operators)
        val tp = c.tp match {
          case None => ""
          case Some(t) => " : " + presentTerm(t, operators)
        }
        val df = c.df match {
          case None => ""
          case Some(t) => " = " + presentTerm(t, operators)
        }
        val not = c.not match {
          case None => ""
          case Some(n) => " # " + n.toString
        }
        c.path.last + tp + df + not + "."

//      case s : Structure => "%include " + s.from.toString
        
      case _ =>
        println("unsupported content element for text presentation " + con.toNode)
        ""
    }
  }


  private def presentTerm(t : Term, operators : List[Operator]) : String = t match {
    case OMA(OMID(p), args) =>
      println("found : " + t.toString)
      operators.find(op => op.name == p) match {
        case None =>
          throw PresentationError("Operator not in scope : " + p.toPath)
          
         case Some(op) =>
           op.notation match {
             case None => 
               println("not found notation for constant with path " + p)
               println("using : " + p.last + "  " + args.map(x => presentTerm(x, operators)).mkString(" "))
               "(" + p.last + "  " + args.map(x => presentTerm(x, operators)).mkString(" ") + ")"
             case Some(notation) => 
               println("found notation : " + notation.toString)
          
               val l =  notation.mrks map {
                 case a : Argument => presentTerm(args(a.pos), operators)
                 case Delimiter(s) => s
               }
               println("using : " + l.mkString(" "))
               l.mkString("("," ",")")
           }
      }

    case OMBINDC(OMID(p), context, None, body) =>
      val tmpargs = context.variables collect {
        case VarDecl(s, Some(tp),_,_) => s :: presentTerm(tp, operators) :: Nil
      }
      val args = tmpargs.flatten
      operators.find(op => op.name == p) match {
        case None => presentTerm(body, operators) //assuming implicit binder
        case Some(op) =>
          op.notation match {
            case None => 
             "(" + p.last + " " + context.toString + " " + body + ")"
            case Some(notation) =>
              val l =  notation.mrks map {
                case a : Argument => args(a.pos)
                case Delimiter(s) => s
              }
              l.mkString("("," ",")")
          }
      }

    case OMV(s) => s.toPath

    case OMID(p) =>
      println("got here with " + p.toString)
      p.last
    case _ =>
      println("unsupported content element for text presentation " + t.toString)
      ""
  }
}


/**
 * A Marker is an element of a notation
 */
sealed trait NotationElement {
  def toNode : scala.xml.Node
}

/**
 * An Argument Marker is a marker that represents an argument for the operator encoded by the notation.
 * It should be filled with a term during parsing and that term will become the argument at position pos for the operator
 *@param pos the number of the operator argument
 */
sealed abstract class Argument(val pos : Int) extends NotationElement

case class StdArg(override val pos : Int) extends Argument(pos) {
  def matches(tk : Token, isBinder : Boolean = false) : Boolean = isBinder match { 
    case true =>
      tk match {
        case s : StrTk => true
        case _ => false 
      }
    case false => 
      tk match {
        case t : TermTk => true
        case e : ExpTk => true
        case _ => false
      }
  }
  
  override def toString = pos.toString 
  
  def toNode = 
    <std-arg>{pos}</std-arg>
}

case class SeqArg(override val pos : Int, delimiter : Delimiter) extends Argument(pos) {
  override def toString = pos.toString + "/" + delimiter.toString 

  def toNode = 
    <seq-arg><pos>{pos}</pos>{delimiter.toNode}</seq-arg>        
}


/**
 * A String Marker is a marker that represents a fixed part of a notation.
 * @param value the string that the marker represents
 */
case class Delimiter(value : String) extends NotationElement {
  def matches(tk : Token) : Boolean = tk match {
    case StrTk(s, _) => s == value
    case _ => false
  }
  
  override def toString = value
  def toNode =
    <delimiter> {value} </delimiter>
}


