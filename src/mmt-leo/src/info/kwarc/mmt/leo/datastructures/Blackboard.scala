package info.kwarc.mmt.leo.datastructures
import datastructures.ProofTree

/**
 * A blackboard is a central data collection object that supports
 * synchronized access between multiple processes.
 *
 * The implementation decides over the fairness and order of execution of the
 * processes.
 *
 * Taken heavily from the LeoPARD system
 */
trait Blackboard[A] extends TaskOrganize[A] with ProofTreeBlackboard[A] with EventBlackboard[A] {

}

/**
 * Subtrait of the Blackboard, responsible for the
 * organization of tasks and agents. Not visible outside the
 * blackboard package except the agentRegistering.
 */
trait TaskOrganize[A] {

  var agentList:List[Agent[A]]

  /**
   * Gives all agents the chance to react to an event
   * and adds the generated tasks.
   *
   * @param t - Function that generates for each agent a set of tasks.
   */
  def filterAll(t : Agent[A] => Unit) : Unit

  /**
   * Method that filters the whole Blackboard, if a new agent 'a' is added
   * to the context.
   *
   * @param a - New Agent.
   */
  def freshAgent(a : Agent[A]) : Unit

  /**
   * Starts a new auction for agents to buy computation time
   * for their tasks.
   *
   * The result is a set of tasks, that can be executed in parallel
   * and approximate the optimal combinatorical auction.
   *
   * @return Not yet executed noncolliding set of tasks
   */
  def getTask : Iterable[(Agent[A],Task[A])]

  /**
   * Tells the task set, that one task has finished computing.
   *
   * @param t - The finished task.
   */
  def finishTask(t : Task[A]) : Unit

  /** Signal Task is called, when a new task is available. */
  def signalTask() : Unit

  /**
   * Checks through the current executing threads, if one is colliding
   *
   * @param t - Task that will be tested
   * @return true, iff no currently executing task collides
   */
  def collision(t : Task[A]) : Boolean

  /**
   * Registers an agent to the blackboard, should only be called by the agent itself
   *
   * @param a - the new agent
   */
  def registerAgent(a : Agent[A]) : Unit

  /**
   * Removes an agent from the notification lists.
   *
   * Recommended if the agent will be used nevermore. Otherwise
   * a.setActive(false) should be used.
   *
   */
  def unregisterAgent(a : Agent[A]) : Unit

  /**
   * Returns for debugging and interactive use the agent work
   *
   * @return all registered agents and their budget
   */
  def getAgents : Iterable[(Agent[A],Double)]

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
  /** Sends a message to an agent. */
  def send(e: Event[A], to: Agent[A])
}






  

