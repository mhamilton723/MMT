package info.kwarc.mmt.owl

import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.io._
import org.semanticweb.owlapi.model._
import org.semanticweb.owlapi.util.SimpleIRIMapper

import java.io.File
import java.net.URI

import scala.collection.mutable.Set // 
import scala.collection.JavaConversions._ //
import info.kwarc.mmt.api._
import info.kwarc.mmt.api.symbols.Constant
import info.kwarc.mmt.api.frontend._
import info.kwarc.mmt.api.modules._
import info.kwarc.mmt.api.libraries._
import info.kwarc.mmt.api.documents._
import info.kwarc.mmt.api.lf._
/*
import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException
import java.util.Map 
*/

import info.kwarc.mmt.api.objects._  //import jomdoc.objects.{Term,OMS,OMI}
import info.kwarc.mmt.api.utils._

object Test2 {
	var num : Int = 1
	//val OWL = new DPath(new xml.URI("http://cds.omdoc.org/logics/description/owl/owl.omdoc"))
	val OWL = new DPath(new xml.URI("http://cds.omdoc.org/logics/description/owl/owl2.omdoc")) 
	var currThy : MPath = null
	
/*	Puyoya sor
	http://cds.omdoc.org/tntbase/logics/description/owl/owl2.omdoc
	http://srv.tntbase.org/repos/cds/
	https://tnt.kwarc.info/repos/cds/	
*/
	val checker = new FoundChecker(DefaultFoundation) 	
	val controller = new Controller(checker, new ConsoleReport)
	
	
	def printClass(c : OWLClassExpression ){println(c)}
	def printIndividual(i : OWLIndividual){println(i)}
	def printProperty(p : OWLObjectPropertyExpression){println(p)} //boyle olmali def printProperty(p : OWLPropertyExpression ){println(p)} 
	def printObject(o: OWLPropertyAssertionObject){println(o)}
	
	def number() {
		print(num)
		num = num+1
	}
	
	def IRItoLocalPath(i: IRI) = {
		 LocalName(i.toString) 
	}
	
	def IRItoMMTURI(i: IRI) : MPath = {
				val dpath = DPath(new xml.URI(i.toURI)) 
				new MPath(dpath, LocalPath(List(i.toString + "?" + "_"))) // mmturi
	}
	
	//def OWLOMS (t: String, s: String) = {  OMID(OWL ? t ? s) }
	def OWLOMS (t: String, s: String) = {  OMID(OWL ? t ? s) }

	def CurrOMS(i: IRI) : Term = { OMID(currThy ? IRItoLocalPath(i))}
		
	def addConstant(name: LocalName, tp: Term) {
			val con = new Constant(OMMOD(currThy), name, Some(tp), None, null)
			//theory name: ex, class name:woman, type, none for definition
			controller.add(con)
			println(con.toString)
	}
		
	def classToLF(c : OWLClassExpression) : Term = {   
		c match {
			
			case c : OWLClass =>
		        CurrOMS(c.getIRI)
		        
		    case c : OWLObjectIntersectionOf =>
		        val args = c.getOperandsAsList.map(classToLF)
		        ApplySpine(OWLOMS("OWL2SUB", "objectIntersectionOf"), args : _*)
		             		         
		    case c : OWLObjectUnionOf =>  
		        val args = c.getOperandsAsList.map(classToLF)
		        ApplySpine(OWLOMS("OWL2QL", "objectUnionOf"), args : _*)
		    		     
		    case c : OWLObjectComplementOf =>
		        val arg = c.getOperand
		        ApplySpine(OWLOMS("OWL2QL", "objectComplementOf"), classToLF(arg) )
		  				       		        
		    case c : OWLObjectAllValuesFrom => //hasChild son
		        val arg1 = c.getProperty
		        val arg2 = c.getFiller
		        ApplySpine(OWLOMS("OWL2QL", "OWLObjectAllValuesFrom"), propertyToLF(arg1), classToLF(arg2) )  
		        
		    case c : OWLObjectSomeValuesFrom => //hasPet dog
		        val arg1 = c.getProperty
		        val arg2 = c.getFiller
		        ApplySpine(OWLOMS("OWL2QL", "OWLObjectSomeValuesFrom"), propertyToLF(arg1), classToLF(arg2) )
		      	
   	     /* case c : OWLDataAllValuesFrom => */ 
		       		        
		 /* case c : OWLDataSomeValuesFrom => */ 
      	    
		        
     		//	Arbitrary (Full) cardinality   
		    case c : OWLObjectCardinalityRestriction =>
		        val name = c match {
		        	case c: OWLObjectExactCardinality => "objectExactCardinality"
		        	case c: OWLObjectMinCardinality => "objectMinCardinality"
		        	case c: OWLObjectMaxCardinality => "objectMaxCardinality"
		        }
		     	val arg1 = intToLF(c.getCardinality) //non-negative integer
		        val arg2 = propertyToLF(c.getProperty)
		        
		      if(c.isQualified) { // isQualified???????!!!!! Quantified ?????
		        val arg3 = c.getFiller
		        ApplySpine(OWLOMS("OWL2SUB", name  + "Qualified"), arg1, arg2, classToLF(arg3) )
		      } 
		      else {
		     	  ApplySpine(OWLOMS("OWL2SUB", name), arg1, arg2)
		      }
		     	
		 /* case c : OWLDataCardinalityRestriction =>
		        val name = c match {
		        	case c: OWLDataExactCardinality => "dataExactCardinality"
		        	case c: OWLDataMinCardinality => "dataMinCardinality"
		        	case c: OWLDataMaxCardinality => "dataMaxCardinality"
		        }
		        if (c.isQualified)
		 */		        		     	
		     
	        
	       case c : OWLObjectOneOf =>
		        val args = c.getIndividuals.map(individualToLF)
		        ApplySpine(OWLOMS("OWL2QL", "objectOneOf"), args.toList : _*)
		    
		   case c : OWLObjectHasValue =>
		    	val arg1 = c.getProperty
		        val arg2 = c.getValue
		        ApplySpine(OWLOMS("OWL2SUB", "objectHasValue"), propertyToLF(arg1), individualToLF(arg2))
		        
				    	
		   case c : OWLObjectHasSelf =>
		        val arg = c.getProperty
		        ApplySpine(OWLOMS("OWL2SUB", "objectHasSelf"), propertyToLF(arg))
		        

		 }
	}
		
	def individualToLF (i : OWLIndividual) : Term = { 
		i match {
			case i : OWLNamedIndividual =>
		        CurrOMS(i.getIRI)
		        
		 /* case i : OWLAnonymousIndividual => i.getIDI */
		       
		}
	}

	def propertyToLF (p : OWLObjectPropertyExpression ) : Term = {  
		p match {
			case p : OWLObjectProperty =>
				CurrOMS(p.getIRI)
/*				
			case p : OWLObjectInverseOf =>
		        val arg = p.getInverse
		        ApplySpine(OWLOMS("OWL2QL", "objectInverseOf"), propertyToLF(arg) )
*/
		}
	}

	def dataPropertyToLF (dp : OWLDataPropertyExpression ) : Term = {  
		dp match {
			case dp : OWLDataProperty =>
				CurrOMS(dp.getIRI)
		}
	}
	
/*	
	def propertyToLF (p : OWLPropertyExpression ) : Term = {  // PropertyExpression altinda object property ve data property var
		p match {
			case p : OWLObjectPropertyExpression =>
			p match {
				case p : OWLObjectProperty =>
					CurrOMS(p.getIRI)
				
				case p : OWLObjectInverseOf =>
		        val arg = p.getInverse
		        ApplySpine(OWLOMS("OWL2QL", "objectInverseOf"), propertyToLF(arg) )
			}
			
			case p : OWLDataPropertyExpression =>
			p match {
				case p : OWLDataProperty =>
					CurrOMS(p.getIRI)
		   }
		   
		}
	}
*/	
	
	def objectToLF (o: OWLPropertyAssertionObject) : Term = {
		o match {
			case o : OWLNamedIndividual =>
				CurrOMS(o.getIRI)
		}
	}

/*
	def literalToLF (lt: OWLLiteral) : Term = {
	    if(lt.isBoolean())
	    {	lt.getLiteral()
	    	lt.getDatatype()
	    	lt.parseBoolean()
	    }
	    else if (lt.isDouble())
	    {	lt.getLiteral()
	    	lt.getDatatype()
	    	lt.parseDouble()
	    }
	    else if (lt.isFloat())
	    {	lt.getLiteral()
	    	lt.getDatatype()
	    	lt.parseFloat()
	    }
	    else if (lt.isInteger())
	    {	lt.getLiteral()
	    	lt.getDatatype()
	    	lt.parseIneger()
	    }
	    else if (lt.isRDFPlainLiteral)
	    {	if(lt.hasLang)
	    	{	lt.getLiteral()
	    		lt.getLang()
	    	}
	    	else
	    		lt.getLiteral()
	    }
	    else println("none")		 		  
	
	}
*/
	
/* Buna gerek kalmadi - dataRangeToLF icinde zaten var
	def dataTypeToLF(d: OWLDatatype) : Term = {
			CurrOMS(d.getIRI)
	}
*/
	def dataRangeToLF(dr : OWLDataRange ) : Term = {
		dr match {
			 case dr : OWLDatatype =>
			 	 CurrOMS(dr.getIRI)
			 	 
			 case dr : OWLDataIntersectionOf =>
			 	  val args = dr.getOperands.map(dataRangeToLF)
		          ApplySpine(OWLOMS("OWL2SUB", "dataIntersectionOf"), args.toList : _*)
		          
			 case dr : OWLDataUnionOf =>
			 	  val args = dr.getOperands.map(dataRangeToLF)
		          ApplySpine(OWLOMS("OWL2QL", "dataUnionOf"), args.toList : _*)
		          
			 case dr : OWLDataComplementOf =>
			 	  val arg = dr.getDataRange()
		          ApplySpine(OWLOMS("OWL2QL", "dataComplementOf"), dataRangeToLF(arg) )
		          
		  // case dr : OWLDataOneOf =>  takes literals (lt1,...,ltn)
		 	    
		  // case dr : OWLDatatypeRestriction => takes (datatype, F1 lt1, ...,Fn ltn)
		          
		}
		
		
	}
	
	def ontologyToLF(manager : OWLOntologyManager, controller : Controller, ontology : OWLOntology) {
	  
		val documentIRI : IRI  = manager.getOntologyDocumentIRI(ontology)
		println("from: " + documentIRI)
			
		val dpath = DPath(new xml.URI(documentIRI.toURI)) 
	    val document = new Document(dpath)
		controller.add(document)
		
		val theory = new DeclaredTheory(dpath, LocalPath(List("_")), Some(OWL ? "OWL2Full")) //constructor. doc, name, meta
		controller.add(theory)
		currThy = theory.path
		controller.add(MRef(dpath, currThy, true))
		
		//manager.method
		//java.util.Set<OWLOntology> 	getImportsClosure(OWLOntology ontology) 
		
		//java.util.List<OWLOntology> getSortedImportsClosure(OWLOntology ontology)
		//A list that represents a topological ordering of the imports closure. The first element in the list will be the specified ontology.
				
		//ontology.method
		//java.util.Set<OWLOntology> 	getDirectImports() 
		//java.util.Set<IRI> 	getDirectImportsDocuments() 
		
/* 2		val imports : java.util.List[OWLOntology] = manager.getSortedImportsClosure(ontology) // var type ?
		imports.foreach {imp =>  // first element is the ontology itself ?
				val from = IRItoMMTURI(imp.getOntologyID.getOntologyIRI())
				controller.add(PlainImport(from, currThy))
		}
		
*/
	
		// val imports : Set[IRI] = ontology.getDirectImportsDocuments()
		// imports.foreach { i => 
	    // cok onemli //if (not in controller zet) ontologyToLF(i)
        //      val from = IRItoMMTURI(i)
		//		controller.add(PlainImport(from, currThy))
		// }
			
				val axioms : Set[OWLAxiom] = ontology.getAxioms
				axioms.foreach(axiom => axiomToLF(axiom)) //axioms.foreach(axiom => controller.add(axiomToLF(axiom)))
	}

	def intToLF (i: Int) : Term = { OMI(i) }
		 
	def doubleToLF (d: Double) : Term = { OMF(d) }
	
	def stringToLF (s: String) : Term = { OMSTR(s) }

	def axiomToLF(ax : OWLAxiom) = { // def axiomToLF(ax : OWLAxiom) : Constant = {} constant donduruyor mu kontrol et
		ax match {

// Declaration
				case ax : OWLDeclarationAxiom =>
				 	println(ax.getAxiomType)
				 	var entity : OWLEntity = ax.getEntity
				 	
				 	if(entity.isBuiltIn())
				 		   println(entity.toStringID())
				 		   
				 	else if(entity.isOWLAnnotationProperty())
					       println(entity.toStringID() + ":" + "annotationProperty" + "." )
						   					       
					else if(entity.isOWLClass) {
						   //println("old: " + entity.toStringID()+ " : " + "class" + "." )
						   val name = IRItoLocalPath(entity.getIRI)
						   // woman  
						   val tp  = OWLOMS("OWLBase", "class")     // type , module and name //OWLBase or OWL2SUB?????? 
						   addConstant(name, tp)
					} 
					
					else if(entity.isOWLDataProperty) {
				    	   //println("old: " + entity.toStringID() + " : " + "dataProperty" + "." )
				    	   
				    	   val name = IRItoLocalPath(entity.getIRI) 
						   val tp  = OWLOMS("OWL2SUB", "dataProperty")  
						   addConstant(name, tp)
					}
				 	
				    else if(entity.isOWLDatatype) {
				    	   //println(entity.toStringID() + " : " + "dataType" + "." )
				    	   
				    	   val name = IRItoLocalPath(entity.getIRI) 
						   val tp  = OWLOMS("D2", "dataType")    
		  			       addConstant(name, tp)
				    }   
						
				    else if(entity.isOWLNamedIndividual){
				    	   // println("old: " + entity.toStringID()+ " : " + "individual" + "." )
						   
				    	   val name = IRItoLocalPath(entity.getIRI)  // John
						   val tp  = OWLOMS("OWLBase", "individual") // type , module and name
						   addConstant(name, tp)
				    }
				    else if(entity.isOWLObjectProperty){
				    	   // println("old: " + entity.toStringID()+ " : " + "objectProperty" + "." )
				    	   
				    	   val name = IRItoLocalPath(entity.getIRI) // hasWife
						   val tp  = OWLOMS("OWLBase", "objectProperty")     // type , module and name
   						   addConstant(name, tp)
				    }
				    else  println("none")   
				    
				/* en match {
				 	  case en : OWLIndividual =>
				 	     //ax.toString
				 	     // println(ax.name + " : " + "individual" + ".")
				 	    println("works")
				   }
				*/
				    
// ClassAxiom		   
				case ax : OWLSubClassOfAxiom =>
				  /*println(ax.getAxiomType)
				  	printClass(ax.getSubClass)
				  	printClass(ax.getSuperClass)
				  	println("_" + " : " + "(" + "subClassOf" + ax.getSubClass + ax.getSuperClass + ")" + ".")
				  */
				  val name = LocalName("ax" + number()) 
				  //num = num+1
				  val tp = ApplySpine(OWLOMS("OWL2SUB", "subClassOf"), classToLF(ax.getSubClass), classToLF(ax.getSuperClass)) 
				  addConstant(name, tp)
			     
				 //Apply(Apply(f,a),b),  ApplySpine(f,a,b)
				 //con //p* //println
				  
			/*	  if(isAnnotated())
				 	  AnnotationToLF(ax)
			*/	 	  
				 
				case ax : OWLEquivalentClassesAxiom =>	
				   val name = LocalName("ax" + number()) 
				   val args = ax.getClassExpressionsAsList.map(classToLF) 
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "equivalentClasses"), args : _*) 
				   addConstant(name, tp) 
				
				case ax : OWLDisjointClassesAxiom =>
				   val name = LocalName("ax" + number()) 
				   val args = ax.getClassExpressionsAsList.map(classToLF) 
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "disjointClasses"), args : _*) 
				   addConstant(name, tp)
				   
				/*	case ax : OWLDisjointUnionAxiom => */
				   
// ObjectPropertyAxiom
				   
				case ax : OWLSubObjectPropertyOfAxiom =>
				  // println("_" + " : " + "(" + "subObjectPropertyOf" + ax.getSubProperty + ax.getSuperProperty + ")" + ".")
				  val name = LocalName("ax" + number()) 
				  val tp = ApplySpine(OWLOMS("OWL2SUB", "subObjectPropertyOf"), propertyToLF(ax.getSubProperty), propertyToLF(ax.getSuperProperty))
				  addConstant(name, tp)
				  
			    case ax : OWLEquivalentObjectPropertiesAxiom =>	
			       val name = LocalName("ax" + number()) 
				   val args = ax.getProperties.map(propertyToLF) 
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "equivalentObjectProperty"), args.toList : _*)
				   addConstant(name, tp)
			   
			    case ax : OWLDisjointObjectPropertiesAxiom =>
				   val name = LocalName("ax" + number()) 
				   val args = ax.getProperties.map(propertyToLF) 
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "disjointObjectProperty"), args.toList : _*)
				   addConstant(name, tp)
				   
			    case ax : OWLInverseObjectPropertiesAxiom => 
				 /*println("inverseObjectProperties")
				   printProperty(ax.getFirstProperty)
				   printProperty(ax.getSecondProperty)
				 */  				   
				   //println("_" + " : " + "(" + "inverseObjectProperties" + ax.getFirstProperty + ax.getSecondProperty + ")" + ".")
				   val name = LocalName("ax" + number())
				   val tp = ApplySpine(OWLOMS("OWL2QL", "inverseObjectProperties"), propertyToLF(ax.getFirstProperty), propertyToLF(ax.getSecondProperty)) //?
				   addConstant(name, tp)
				   
				case ax : OWLObjectPropertyDomainAxiom =>
				  //println("_" + " : " + "(" + "objectPropertyDomain" + ax.getProperty + ax.getDomain + ")" + ".")
				  val name = LocalName("ax" + number()) 
				  val tp = ApplySpine(OWLOMS("OWL2SUB", "objectPropertyDomain"), propertyToLF(ax.getProperty), classToLF(ax.getDomain)) 
			      addConstant(name, tp)
			  
			/*	case ax : OWLObjectPropertyRangeAxiom =>
			      //println("_" + " : " + "(" + "objectPropertyRange" + ax.getProperty + ax.getRange + ")" + ".")
				  val name =  LocalName("ax" + num)
			      num = num+1
				  //?R val tp = ApplySpine(OWLOMS("OWL2SUB", "objectPropertyRange"), propertyToLF(ax.getProperty), classToLF(ax.getRange))
			   	  addConstant(name, tp)
			*/
			    case ax : OWLFunctionalObjectPropertyAxiom =>	
				   // println("_" + " : " + "(" + "functionalObjectProperty" + ax.getProperty +  ")" + ".")
				   val name = LocalName("ax" + number())
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "functionalObjectProperty"), propertyToLF(ax.getProperty)) 
				   addConstant(name, tp)
				   
			    case ax : OWLInverseFunctionalObjectPropertyAxiom =>	
				   // println("_" + " : " + "(" + "inverseFunctionalObjectProperty" + ax.getProperty +  ")" + ".")
				   val name = LocalName("ax" + number()) 
				   val tp = ApplySpine(OWLOMS("OWL2QL", "inverseFunctionalObjectProperty"), propertyToLF(ax.getProperty)) 
				   addConstant(name, tp)
				   
				case ax : OWLReflexiveObjectPropertyAxiom =>	
				   // println("_" + " : " + "(" + "reflexiveObjectProperty" + ax.getProperty +  ")" + ".")
				   val name = LocalName("ax" + number()) 
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "reflexiveObjectProperty"), propertyToLF(ax.getProperty)) 
				   addConstant(name, tp)	
				   
				case ax : OWLIrreflexiveObjectPropertyAxiom =>	
				   // println("_" + " : " + "(" + "irreflexiveObjectProperty" + ax.getProperty +  ")" + ".")
				   val name = LocalName("ax" + number()) 
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "irreflexiveObjectProperty"), propertyToLF(ax.getProperty)) 
				   addConstant(name, tp)   
				
				case ax : OWLSymmetricObjectPropertyAxiom =>	
				   // println("_" + " : " + "(" + "symmetricObjectProperty" + ax.getProperty +  ")" + ".")
				   val name = LocalName("ax" + number()) 
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "symmetricObjectProperty"), propertyToLF(ax.getProperty)) 
				   addConstant(name, tp)
							     
				case ax : OWLAsymmetricObjectPropertyAxiom =>	
				   // println("_" + " : " + "(" + "asymmetricObjetProperty" + ax.getProperty +  ")" + ".")
				   val name = LocalName("ax" + number()) 
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "asymmetricObjetProperty"), propertyToLF(ax.getProperty)) 
				   addConstant(name, tp)
				   
				case ax : OWLTransitiveObjectPropertyAxiom =>	
				   // println("_" + " : " + "(" + "transitiveObjectProperty" + ax.getProperty +  ")" + ".")
				   val name = LocalName("ax" + number()) //p*
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "transitiveObjectProperty"), propertyToLF(ax.getProperty)) 
				   addConstant(name, tp)
			
// DataPropertyAxiom
				   
			/*	case ax : OWLSubDataPropertyOfAxiom => */   
				     
			/*	case ax : OWLEquivalentDataPropertiesAxiom => */
				
			/*	case ax : OWLDisjointDataPropertiesAxiom => */
				   
			/*    case ax : OWLDataPropertyDomainAxiom =>
				  //println("_" + " : " + "(" + "dataPropertyDomain" + ax.getProperty + ax.getDomain + ")" + ".")
				  val name = LocalName("ax" + number()) 
				  val tp = ApplySpine(OWLOMS("OWL2SUB", "dataPropertyDomain"), propertyToLF(ax.getProperty), classToLF(ax.getDomain)) 
			      addConstant(name, tp) 
			*/
				   
			/*	case ax : OWLDataPropertyRangeAxiom =>
			      //println("_" + " : " + "(" + "dataPropertyRange" + ax.getProperty + ax.getRange + ")" + ".")
				  val name = LocalName("ax" + num)
			      num = num+1
				  //?R val tp = ApplySpine(OWLOMS("OWL2SUB", "dataPropertyRange"), propertyToLF(ax.getProperty), classToLF(ax.getRange))
			   	  addConstant(name, tp)
 			*/
			
			/*  case ax : OWLFunctionalDataPropertyAxiom => */
			 	   
// DatatypeDefinition

			/*	case ax : OWLDatatypeDefinitionAxiom => */ 
			  	   
// HasKey 
 			 
 			/*  case ax : OWLHasKeyAxiom =>  */
 			 
 			 
 
// Assertion 

				case ax : OWLSameIndividualAxiom =>	
				   val name = LocalName("ax" + number()) 
				   val args = ax.getIndividualsAsList.map(individualToLF) 
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "sameIndividual"), args : _*) 
				   addConstant(name, tp)
				
				case ax : OWLDifferentIndividualsAxiom =>	
				   val name = LocalName("ax" + number()) 
				   val args = ax.getIndividualsAsList.map(individualToLF) 
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "differentIndividuals"), args : _*)
				   addConstant(name, tp)
				   
				case ax : OWLClassAssertionAxiom => 
				 /*println("class assertion")
				   printIndividual(ax.getIndividual)
			       printClass(ax.getClassExpression)
			     */
			       //println("_" + " : " + "(" + "classAssertion" + ax.getClassExpression + ax.getIndividual + ")" + ".")
				  val name = LocalName("ax" + number()) 
				  val tp = ApplySpine(OWLOMS("OWL2SUB", "classAssertion"), classToLF(ax.getClassExpression), individualToLF(ax.getIndividual)) 
				  addConstant(name, tp)
				  
			    case ax : OWLObjectPropertyAssertionAxiom => 
				 /*println("property assertion")
				   printIndividual(ax.getSubject)
				   printProperty(ax.getProperty)
				   printObject(ax.getObject)
				 */   
				   // println("_" + " : " + "(" + "objectPropertyAssertion" + ax.getProperty + ax.getSubject + ax.getObject + ")" + ".")
				   val name = LocalName("ax" + number()) 
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "objectPropertyAssertion"), propertyToLF(ax.getProperty), individualToLF(ax.getSubject), objectToLF(ax.getObject))
				   addConstant(name, tp)
				   
				case ax : OWLNegativeObjectPropertyAssertionAxiom => 
				 /*println("negativeObjectPropertyAssertion")
				   printIndividual(ax.getSubject)
				   printProperty(ax.getProperty)
				   printObject(ax.getObject)
				 */  
				   // println("_" + " : " + "(" + "negativeObjectPropertyAssertion" + ax.getProperty + ax.getSubject + ax.getObject + ")" + ".")
				   val name = LocalName("ax" + number())
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "negativeObjectPropertyAssertion"), propertyToLF(ax.getProperty), individualToLF(ax.getSubject), objectToLF(ax.getObject)) //?
				   addConstant(name, tp)
				      
			/*  case ax : OWLDataPropertyAssertionAxiom =>
				   println("dataPropertyAssertion")
				   printIndividual(ax.getSubject)
				   printProperty(ax.getProperty)
				   printObject(ax.getObject)
				   
				   println("_" + " : " + "(" + "dataPropertyAssertion" + ax.getProperty + ax.getSubject + ax.getObject + ")" + ".")
				   val name = LocalName("ax" + number())
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "dataPropertyAssertion"), propertyToLF(ax.getProperty), individualToLF(ax.getIndividual), objectToLF(ax.getObject)) //?
                
				case ax : OWLNegativeDataPropertyAssertionAxiom => 
				 /*println("negativeDataPropertyAssertion")
				   printIndividual(ax.getSubject)
				   printProperty(ax.getProperty)
				   printObject(ax.getObject)
				 */  
				   // println("_" + " : " + "(" + "negativeDataPropertyAssertion" + ax.getProperty + ax.getSubject + ax.getObject + ")" + ".")
				   val name = LocalName("ax" + number())
				   val tp = ApplySpine(OWLOMS("OWL2SUB", "negativeDataPropertyAssertion"), propertyToLF(ax.getProperty), individualToLF(ax.getSubject), objectToLF(ax.getObject)) //?
				   addConstant(name, tp)
								   
			 */
               
// AnnotationAxiom
							    
			 /* case ax : OWLAnnotationAssertionAxiom => */

			 /*	case ax : OWLSubAnnotationPropertyOfAxiom => */
				   
			 /*	case ax : OWLAnnotationPropertyDomainAxiom => */ 
				
			 /* case ax : OWLAnnotationPropertyRangeAxiom  => */ 
				   
			    case _ => println("else")	
			    
		}
	}
/*	
	def AnnotationToLF(ax : OWLAxiom) : Term = {
		val annots = ax.getAnnotations()
		annots.foreach { ann =>
		}
	
	}	
*/
	
	def main(args: Array[String]) {
     
		    //Get hold of an ontology manager
			val manager : OWLOntologyManager = OWLManager.createOWLOntologyManager()
			controller.handle(ExecFile(new java.io.File("startup.mmt"))) 

			/*	
		    //Load an ontology from the web
			val iri : IRI = IRI.create("http://www.co-ode.org/ontologies/pizza/pizza.owl");
			val pizzaOntology : OWLOntology  = manager.loadOntologyFromOntologyDocument(iri); 
			println("Loaded ontology: " + pizzaOntology);
			
			//Remove the ontology so that we can load a local copy.
			manager.removeOntology(pizzaOntology);
		
			//Load ontologies from files.  Download the pizza ontology from
			//http://www.co-ode.org/ontologies/pizza/pizza.owl and put it somewhere on your hard drive
			//Create a file object that points to the local copy
			*/
			
			val file : File = new File("E:\\Fall10\\CompSem\\Project\\class.owl");
			//val file : File = new File("E:\\Fall10\\CompSem\\Project\\deneme2.owl");
			//val file : File = new File("E:\\Fall10\\CompSem\\Project\\MMTtrunk\\implementation\\OWLMMT\\examples\\import.owl");
			//val file : File = new File("E:\\Fall10\\CompSem\\Project\\MMTtrunk\\implementation\\OWLMMT\\examples\\ex2.owl");
			
			//Now load the local copy
			val test : OWLOntology  = manager.loadOntologyFromOntologyDocument(file);
			println("Loaded ontology: " + test);
			
/*			val arg = args(0)
			val file : File = new File(arg)
			val test : OWLOntology  = manager.loadOntologyFromOntologyDocument(file)
			println("Loaded ontology: " + test)
*/
			
// 1		val imports : java.util.List[OWLOntology] = manager.getSortedImportsClosure(test) 
			
			//manager.getOntologies.foreach {onto => ontologyToLF(manager, controller, onto )} sor ?
			ontologyToLF(manager, controller, test )
			
		    //println("%" + "sig" + "ex" + "=" + "{" )
		    //%include OWL2SUB %open.
	
		/*	val axioms : Set[OWLAxiom] = test.getAxioms
			//lambda ax.F  ax => F
			axioms.foreach(ax => ax match {
				case ax : OWLDeclarationAxiom => ...
		*/	
			println(controller.get(currThy).toNode)
			println(controller.get(currThy).toString)

		  /*
			// We can always obtain the location where an ontology was loaded from
			val documentIRI : IRI  = manager.getOntologyDocumentIRI(localPizza);
			println("    from: " + documentIRI);
			
			// Remove the ontology again so we can reload it later
			manager.removeOntology(pizzaOntology);
		
			// In cases where a local copy of one of more ontologies is used, an ontology IRI mapper can be used
			// to provide a redirection mechanism.  This means that ontologies can be loaded as if they were located
			// on the web.
			// In this example, we simply redirect the loading from http://www.co-ode.org/ontologies/pizza/pizza.owl
			// to our local copy above.
			 
			manager.addIRIMapper(new SimpleIRIMapper(iri, IRI.create(file)));
			// Load the ontology as if we were loading it from the web (from its ontology IRI)
			pizzaOntologyIRI : IRI = IRI.create("http://www.co-ode.org/ontologies/pizza/pizza.owl");
			redirectedPizza : OWLOntology = manager.loadOntology(pizzaOntologyIRI);
			println("Loaded ontology: " + redirectedPizza);
			println("    from: " + manager.getOntologyDocumentIRI(redirectedPizza));
			
			// Note that when imports are loaded an ontology manager will be searched for mappings
 		 */
	}

} //object Test2 nin parantezi
