package mv2pl

import akka.actor.Actor
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import akka.pattern._

class Partition(val id: String) extends Actor {

  var transactions = TrieMap[String, Lock]()

  override def receive: Receive = {
    case cmd: Lock =>

      val now = System.currentTimeMillis()

      transactions = transactions.filter { case (id, t) =>
         now - t.tmp < TIMEOUT
      }

      val keys = transactions.map(_._2.keys).flatten.toSeq

      if(!cmd.keys.exists(keys.contains(_))){

        cmd.tmp = now

        transactions.put(cmd.t, cmd)
        sender ! true
      } else {
        sender ! false
      }

    case cmd: Release => transactions.remove(cmd.t)
    case _ =>
  }
}
