package test.scala


import datastructures.{ProofData, AndOrNode}
import org.scalatest._
/**
 * Created by mark on 6/25/15.
 */

class DatastructuresSpec extends FlatSpec with Matchers {

  def mkNode[A](data:A, cong:Boolean, sat: Option[Boolean]=None):AndOrNode[A]={
    val pd= new ProofData(data,cong,sat)
    new AndOrNode(pd)
  }


  val node0 = mkNode(0,true)
  val node1 = mkNode(1,true)
  val node2 = mkNode(2,true)
  val node3 = mkNode(3,true)
  val node4 = mkNode(4,true)
  node0.addChild(node1)
  node1.addChild(node2)
  node1.addChild(node3)
  node2.addChild(node4)
  //node1.setRoot(node0)

  "A Node" should "have a root and children and know its siblings" in {
    node1.meta should be (1)
    node1.siblings should be (Nil)
    node2.siblings should be (List(node3))
    node1.root should be (Some(node0))
    node0.root should be (None)
    node1.children should be (List(node2,node3))
    node2.children should be (List(node4))
    node0.depth should be (0)

  }

  it should "have a mapping function that preserves the structure of the tree" in {
    val tnode0 = mkNode(0.5,true)
    val tnode1 = mkNode(1.5,true)
    val tnode2 = mkNode(2.5,true)
    val tnode3 = mkNode(3.5,true)
    val tnode4 = mkNode(4.5,true)
    tnode0.addChild(tnode1)
    tnode1.addChild(tnode2)
    tnode1.addChild(tnode3)
    tnode2.addChild(tnode4)

    val fnode0 = node0.map(i => i.toDouble + .5)
    fnode0.meta should be (0.5)
    fnode0.children.head.isEquivTo(tnode1) should be (true)
  }

  it should "be able to properly trim the proof tree" in {


  }

/*  it should "have breadth and depth first search capabilities" in {
    var listB = List(-1)
    var listD = List(-1)
    node1.preorderDepth({n => listB=listB:::List(n.data) })
    listB should be (List(-1,1,2,3,4))
    node1.postorderDepth({n => listD=listD:::List(n.data) })
    listD should be (List(-1,1,2,4,3))
  }*/

  /*it should "throw NoSuchElementException if an empty stack is popped" in {
    val emptyStack = new Stack[Int]
    a [NoSuchElementException] should be thrownBy {
      emptyStack.pop()
    }
  }*/
}



