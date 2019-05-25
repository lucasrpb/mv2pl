import akka.actor.ActorRef
import scala.collection.concurrent.TrieMap

package object mv2pl {

  val TIMEOUT = 100

  trait Command

  case class Lock(t: String, var keys: Seq[String], var tmp: Long) extends Command
  case class Release(t: String) extends Command

  val partitions = TrieMap[String, ActorRef]()

}
