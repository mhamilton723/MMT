package info.kwarc.mmt.leo.datastructures
import datastructures.ProofTree
import scala.collection.mutable


/**
 * A blackboard is a central data collection object that supports
 * synchronized access between multiple processes.
 *
 * The implementation decides over the fairness and order of execution of the
 * processes.
 *
 * Taken heavily from the LeoPARD system
 */
class Blackboard[A](goal: ProofTree[A]) extends ProofTreeBlackboard[A] with EventBlackboard[A] {
  var agentList:List[Agent[A]] = Nil
  var proofTree= goal

  def registerAgent(a : Agent[A]) : Unit = List(a):::agentList
  def registerAgent(l : List[Agent[A]] ) : Unit = l:::agentList
  def unregisterAgent(a : Agent[A]) : Unit = agentList.diff(List(a))
  def unregisterAgent(l : List[Agent[A]]) : Unit = agentList.diff(l)

  /**
   *
   * Returns for debugging and interactive use the agent work
   *
   * @return all registered agents and their budget
   */

}

/**
 * This trait capsules the formulas responsible for the formula manipulation of the
 * blackboard.
 */
trait ProofTreeBlackboard[A] {

  var proofTree: ProofTree[A]
  /**
   * Adds a prooftree to the blackboard, if it does not exist. If it exists
   * the old formula is returned.
   *
   * @param root root node to attach new proof
   * @param tree tree to attach to root
   * @return true if successful addition
   */
  def addTree(root: ProofTree[A], tree : ProofTree[A]) : Unit = root.addChild(tree)

  /**
   * Removes a formula from the Set fo formulas of the Blackboard.
   */
  def removeTree(tree : ProofTree[A]) : Unit = tree.disconnect()

  /** Returns a List of all nodes of the Blackboard's proof tree 
   * @return All formulas of the blackboard.
   */
  def getNodes : Iterable[ProofTree[A]] = proofTree.preDepthFlatten

}

/**
 * This trait capsules the message handling for the blackboard
 */
trait EventBlackboard[A] {
  var eventSeq : Seq[Event[A]]=Nil
}

/**
 * Subtrait of the Blackboard, responsible for the
 * organization of tasks and agents. Not visible outside the
 * blackboard package except the agentRegistering.
 */
class SchedulingAgent[A](blackboard: Blackboard[A]) extends Agent[A] {

  val name = "SchedulingAgent"
  val level = 1
  val interests = Nil

  var agents = blackboard.agentList
  var events = blackboard.eventSeq

  /** Sends a message to an agent. */
  def sendEventTo(e: Event[A], to: Agent[A]) = to.eventQueue.enqueue(e)

  /**
   * Gives all agents the chance to react to an event
   * and adds the generated tasks.
   */
  def sendToAll(e: Event[A]) : Unit =
    agents.filter(_.hasInterest(e.flags)).foreach(_.eventQueue.enqueue(e))


  /**
   * Method that filters the whole Blackboard, if a new agent 'a' is added
   * to the context.
   *
   * @param a - New Agent.
   */
  def freshAgent(a : Agent[A]) : Unit =
    events.filter(e=>a.hasInterest(e.flags)).foreach(sendEventTo(_,a))

  /**
   * Starts a new auction for agents to buy computation time
   * for their tasks.
   *
   * The result is a set of tasks, that can be executed in parallel
   * and approximate the optimal combinatorical auction.
   *
   * @return Not yet executed noncolliding set of tasks
   */
  def getTask : Iterable[Task[A]] = {
    val allTasks = new mutable.Queue[Task[A]]()
    agents.foreach(allTasks++_.taskQueue)
    def removeColliding(nExec: Iterable[Task[A]]): Unit = allTasks.synchronized(allTasks.dequeueAll{tbe =>
      nExec.exists{e =>
        val rem = e.writeSet().intersect(tbe.writeSet()).nonEmpty ||
          e.writeSet().intersect(tbe.writeSet()).nonEmpty ||
          e == tbe // Remove only tasks depending on written (changed) data.
        if(rem && e != tbe) println("The task\n  $tbe\n collided with\n  $e\n and was removed.")
        rem
      }
    })
    removeColliding(allTasks)
    allTasks
  }

  /**
   * Checks through the current executing threads, if one is colliding
   *
   * @param t - Task that will be tested
   * @return true, iff no currently executing task collides
   */
 // def collision(t : Task[A]) : Boolean



}





  

