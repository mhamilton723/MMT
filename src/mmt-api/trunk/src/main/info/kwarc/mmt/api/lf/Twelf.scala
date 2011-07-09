package info.kwarc.mmt.api.lf
import info.kwarc.mmt.api._
import backend._
import utils.File
import utils.FileConversion._
import info.kwarc.mmt.lf._

import java.io._

import scala.collection.mutable.HashSet

/** helper methods for Twelf */
object Twelf {
   /** parses filename:col.line-col.line */
   def parseRegion(s: String) = {
      val i = s.lastIndexOf(":")
      val file = File(s.substring(0,i))
      val numbers = s.substring(i+1).split("[-\\.]")
      Region(file, numbers(0).toInt, numbers(1).toInt, numbers(2).toInt, numbers(3).toInt)
   }
}

/** Utility for starting the catalog and calling the Twelf compiler
  * @param path the Twelf compiler executable
  */
class Twelf(path : File) extends Compiler {
   def isApplicable(src: String) = src == "twelf"
   
   /** Twelf setting "set unsafe ..." */
   var unsafe : Boolean = true
   /** Twelf setting "set chatter ..." */
   var chatter : Int = 5
//   var catalog : Option[Catalog] = None
   
   /** creates and intializes a Catalog
     */
   override def init {
  //    val cat = new Catalog(new HashSet[String]()+path.toJava.getPath, new HashSet[String]+"*.elf", new HashSet[String]+".svn", 8081)
    //  catalog.init    //  throws PortUnavailable
      //catalog = Some(cat)
   }
   override def destroy {
      //catalog.foreach(_.destroy)
   }

   /** 
     * Compile a Twelf file to OMDoc
     * @param in the input Twelf file 
     * @param targetdir the directory in which to put a generated OMDoc file with the same name as the input file
     */
   def compile(in: File, out: File) : List[CompilerError] = {
      val procBuilder = new java.lang.ProcessBuilder(path.toString)
      procBuilder.redirectErrorStream()
      val proc = procBuilder.start()
      val input = new PrintWriter(proc.getOutputStream(), true)
      val output = new BufferedReader(new InputStreamReader(proc.getInputStream()))
      input.println("set chatter " + chatter)
      input.println("set unsafe " + unsafe)
      /* catalogOpt foreach {cat =>
         input.println("set catalog " + cat.catalogURI)
      }*/
      input.println("loadFile " + in)
      input.println("Print.OMDoc.printDoc " + in + " " + out)
      input.println("OS.exit")
      var line : String = null
      var errors : List[CompilerError] = Nil
      while ({line = output.readLine; line != null}) {
         line = line.trim
         val (treat, dropChars, warning) =
            if (line.endsWith("Warning:")) (true, 9, true)
            else if (line.endsWith("Error:")) (true, 7, false)
            else (false, 0, false)
         if (treat) {
            val r = Twelf.parseRegion(line.substring(0, line.length - dropChars))
            val msg = List(output.readLine) //TODO should read multi-line message
            errors ::= CompilerError(r, msg, warning)
         }
      }
      errors.reverse
   }
}

/*
object TwelfTest {
   def main(args: Array[String]) {
      val twelf = new Twelf(File("e:\\other\\twelf-mod\\bin\\twelf-server.bat"))
      twelf.check(File("e:\\other\\twelf-mod\\examples-mod\\test.elf"), File(".")) 
   }
}
*/