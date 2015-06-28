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
  def run(): Unit

  /** This function runs the specific agent on the registered Blackboard. */
  def taskToResult(t:Task[A]): Result[A]



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

  /** Removes all Tasks */
  def clearTasks(): Unit = taskQueue.synchronized(taskQueue.clear())

  /**
   * As getTasks with an infinite budget
   * @return - All Tasks that the current agent wants to execute.
   */
  def getAllTasks: Iterable[Task[A]] = taskQueue.synchronized(taskQueue.iterator.toIterable)


  /**
   * Returns a a list of Tasks, the Agent can afford with the given budget.
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
  var newEvents: Seq[Event[A]]  = Nil
  val auctioningAgent: Agent[A]=agents.filter(_.name=="AuctioningAgent").head

  /** higher order agents do not use tasks yet*/
  def taskToResult(t:Task[A]) = {new EmptyResult[A]}

  def addNewEvents(): Unit = {
    val out = blackboard.eventSeq.filter(!_.wasReadBy(this))
    out.foreach(_.readBy++List(this))
    newEvents++out
  }

  /** Sends a message to an agent. */
  def sendEventTo(e: Event[A], to: Agent[A]) = to.eventQueue.enqueue(e)

  /**
   * Gives all agents the chance to react to an event
   * and adds the generated tasks.
   */
  def sendToAll(e: Event[A]) : Unit =
    agents.filter(_.hasInterest(e.flags)).foreach(_.eventQueue.enqueue(e))


  def sendNewEvents():Unit = {
    newEvents.foreach(sendToAll(_))
    newEvents=Nil
  }

  /** This function runs the specific agent on the registered Blackboard. */
  def run():Unit = {
    isActive=true
    addNewEvents()
    sendNewEvents()
    auctioningAgent.run()
    isActive=false
  }

  /**
   * Method that filters the whole Blackboard, if a new agent 'a' is added
   * to the context.
   *
   * @param a - New Agent.
   */
  def freshAgent(a : Agent[A]) : Unit =
    events.filter(e=>a.hasInterest(e.flags)).foreach(sendEventTo(_,a))


  /**
   * Checks through the current executing threads, if one is colliding
   *
   * @param t - Task that will be tested
   * @return true, iff no currently executing task collides
   */
  // def collision(t : Task[A]) : Boolean
    
}

class AuctioningAgent[A](blackboard: Blackboard[A]) extends Agent[A] {
  val name = "AuctioningAgent"
  val level = 1
  val interests = Nil

  var agents = blackboard.agentList
  val executingAgent: Agent[A]=agents.filter(_.name=="ExecutingAgent").head

  def runAgents() = agents.filter(_.level==0).foreach(_.run())

  /** Higher order agents do not use tasks yet*/
  def taskToResult(t:Task[A]) = {new EmptyResult[A]}

  override def getAllTasks() : mutable.Queue[Task[A]] ={
    val allTasks = new mutable.Queue[Task[A]]()
    agents.foreach(allTasks++_.taskQueue)
    allTasks
  }
  
  
  def run():Unit ={
    isActive=true
    runAgents()
    getTask.foreach(executingAgent.taskQueue.enqueue(_))
    executingAgent.run()
    isActive=false
  }


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
  
}


class ExecutingAgent[A](blackboard: Blackboard[A]) extends Agent[A] {
  val name = "ExecutingAgent"
  val level = 1
  val interests = Nil

  var agents = blackboard.agentList
  var events = blackboard.eventSeq
  val resultsQueue: mutable.Queue[Result[A]] = new mutable.Queue[Result[A]]()
  val schedulingAgent: Agent[A]=agents.filter(_.name=="SchedulingAgent").head

  /** Higher order agents do not use tasks yet*/
  def taskToResult(t:Task[A]) = {new EmptyResult[A]}

  def tasksToResults():Unit = { taskQueue.foreach(t=> resultsQueue.enqueue(t.byAgent.taskToResult(t)))}

  def executeResults():Unit ={
    resultsQueue.foreach(executeResult(_))
  }

  def executeResult(t: Result[A]) ={
    t.newFormula().foreach(pair=>pair._1.addChild(pair._2))
    t.updateFormula().foreach(pair=>
      pair._1.root match {
        case Some(p) if pair._1 != pair._2 => p.addChild(pair._2);p.disconnectChild(pair._1)
        case None => blackboard.proofTree = pair._2; print("Overwriting Proof Root")
        case _ =>
      })

    t.removeFormula().foreach(_.disconnect())
  }

  def run() ={
    isActive=true
    tasksToResults()
    executeResults()
    schedulingAgent.run()
    isActive=false
  }
}