package info.kwarc.mmt.leo.datastructures

import scala.collection.immutable
import scala.collection.mutable


/**
 * <p>
 * Interface for all Agent Implementations.
 *
 * Taken Heavily from the LeoPARD project
 */
abstract class Agent[A] {
  /** @return the name of the agent */
  def name: String

  /** the level of the agent in the hierarchy */
  val level: Int

  /** whether the agent is active or not */
  var isActive: Boolean = false

  def setActive(a: Boolean) = {
    isActive = a
  }

  /** Specifies what an agents interests are: "ADD", "CHANGE", "REMOVE", "CLOSE", "DELETE"*/
  val interests: List[String]
  def hasInterest(i: String): Boolean = {interests.contains(i)}
  def hasInterest(l: List[String]): Boolean = {l.exists(interests.contains(_))}

  /** Queue holding the Events relieved */
  val eventQueue: mutable.Queue[Event[A]] = new mutable.Queue[Event[A]]()

  /** Queue holding the tasks to be bid on */
  val taskQueue: mutable.Queue[Task[A]] = new mutable.Queue[Task[A]]()

  /** @return number of tasks, the agent can currently work on */
  def numTasks: Int = taskQueue.size

  /** This function runs the specific agent on the registered Blackboard. */
  //def run(t: Task[A]): Result[A]

  /**
   * In this method the Agent gets the Blackboard it will work on.
   * Registration for Triggers should be done in here.
   */
  def register(blackboard: Blackboard[A]) {
    blackboard.registerAgent(this)
    setActive(true)
  }

  def unregister(blackboard: Blackboard[A]): Unit = {
    blackboard.unregisterAgent(this)
    setActive(false)
    taskQueue.synchronized(taskQueue.clear())
  }

  /**
   * This method is called when an agent is killed by the scheduler
   * during execution. This method does standardized nothing.
   *
   * In the case an external Process / Thread is created during the
   * execution of the agent, this method can clean up the processes.
   */
  def kill(): Unit = {}


  /**
   * This method should be called, whenever a formula is added to the blackboard.
   *
   * The filter then checks the blackboard if it can generate tasks from it,
   * that will be stored in the Agent.
   *
   * @param event - Newly added or updated formula
   */
 // def respond(event: Event[A]): Unit

  /**
   * Removes all Tasks
   */
  def clearTasks(): Unit = taskQueue.synchronized(taskQueue.clear())

  /**
   * As getTasks with an infinite budget.
   *
   * @return - All Tasks that the current agent wants to execute.
   */
  def getAllTasks: Iterable[Task[A]] = taskQueue.synchronized(taskQueue.iterator.toIterable)


  /**
   * Returns a a list of Tasks, the Agent can afford with the given budget.
   *
   * @param budget - Budget that is granted to the agent.
   */
  def getTasks(budget : Double) : Iterable[Task[A]] = {
    var erg = List[Task[A]]()
    var costs : Double = 0
    taskQueue.synchronized {
      for (t <- taskQueue) {
        if (costs > budget) return erg
        else {
          costs += t.bid(budget)
          erg = t :: erg
        }
      }
    }
    erg
  }


  /**
   * Given a set of (newly) executing tasks, remove all colliding tasks.
   *
   * @param nExec - The newly executing tasks
   */


  def removeColliding(nExec: Iterable[Task[A]]): Unit = taskQueue.synchronized(taskQueue.dequeueAll{tbe =>
    nExec.exists{e =>
      val rem = e.writeSet().intersect(tbe.writeSet()).nonEmpty ||
        e.writeSet().intersect(tbe.writeSet()).nonEmpty ||
        e == tbe // Remove only tasks depending on written (changed) data.
      if(rem && e != tbe) println("The task\n  $tbe\n collided with\n  $e\n and was removed.")
      rem
    }
  })
}


