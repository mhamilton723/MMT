package info.kwarc.mmt.leo.datastructures

import scala.collection.immutable

/**
 * Created by mark on 6/27/15.
 */

class Message{

}

abstract class Agent[A](levelVar: Int) {
  val name:String
  val level =levelVar
  val interests : List[String]
  var messageBox: List[Message]

  def getTasks: List[Task[A]]
}

class SchedulingAgent extends Agent(2) {
  val name: String = "SchedulingAgent"
  val interests: List[String] = List("Change")
  var messageBox: List[Message] = Nil

  def getTasks = Nil
}
