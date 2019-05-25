package mv2pl

import java.util.{Comparator, Timer, TimerTask}
import java.util.concurrent.{ConcurrentLinkedQueue, PriorityBlockingQueue}

import akka.actor.ActorRef
import akka.dispatch.{Envelope, MessageQueue}

class MyMessageQueue extends MessageQueue {

  val queue = new ConcurrentLinkedQueue[Envelope]()
  //var work = Seq.empty[Envelope]

  val work = new PriorityBlockingQueue[Envelope](1, (o1: Envelope, o2: Envelope) => {
    val m1 = o1.message
    val m2 = o2.message

    if(m1.isInstanceOf[Lock] && m2.isInstanceOf[Lock]){
       m1.asInstanceOf[Lock].t.compareTo(m2.asInstanceOf[Lock].t)
    } else {
      //Int.MinValue
      0
    }
  })

  val timer = new Timer(true)

  timer.schedule(new TimerTask {
    override def run(): Unit = {
      while(!queue.isEmpty){
        work.add(queue.poll())
      }
    }
  }, 10L, 10L)

  // these should be implemented; queue used as example
  def enqueue(receiver: ActorRef, handle: Envelope): Unit = {
    //queue.offer(handle)

    handle.message match {
      case cmd: Lock => queue.add(handle)
      case _ => work.add(handle)
    }
  }

  def dequeue(): Envelope = {
    //queue.poll
    work.poll()
  }

  def numberOfMessages: Int = work.size

  def hasMessages: Boolean = !work.isEmpty

  def cleanUp(owner: ActorRef, deadLetters: MessageQueue) {
    while (hasMessages) {
      deadLetters.enqueue(owner, dequeue())
    }
  }
}
