package mv2pl

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

import akka.actor.{ActorSystem, Props}
import org.scalatest.FlatSpec
import akka.pattern._
import akka.util.Timeout

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

class MainSpec extends FlatSpec {

  "money " should " be equal after transactions" in {

    case class Account(val id: String, var balance: Int)

    val rand = ThreadLocalRandom.current()
    val system = ActorSystem("transactions")

    for(i<-0 until 10){
      val p = system.actorOf(Props(classOf[Partition], i.toString), s"$i")
      partitions.put(i.toString, p)
    }

    val np = partitions.size

    var accounts = Seq.empty[Account]

    for(i<-0 until 100){
      val balance = rand.nextInt(0, 1000)
      val a = Account(UUID.randomUUID.toString, balance)

      accounts = accounts :+ a
    }

    implicit val timeout = Timeout(400 milliseconds)

    def transaction(keys: Seq[String])(f: => Unit): Future[Boolean] = {
      val id = UUID.randomUUID.toString
      val requests = TrieMap[String, Lock]()

      keys.foreach { k =>
        val p = (requests.computeHash(k).abs % np).toString

        if(requests.isDefinedAt(p)){
          val lock = requests(p)
          lock.keys = lock.keys :+ k
        } else {
          requests.put(p, Lock(id, Seq(k), System.currentTimeMillis()))
        }
      }

      val start = System.currentTimeMillis()
      Future.sequence(requests.map{case (p, l) => (partitions(p) ? l).mapTo[Boolean]})
        .map { locks =>
        if(locks.exists(_ == false)){
          false
        } else {
          f
          true
        }
      }.recover{case _ => false}
      .map { ok =>

        val elapsed = System.currentTimeMillis() - start

        requests.map{case (p, l) => (partitions(p) ? Release(id))}

        println(s"tx ${id} done => $ok elapsed: ${elapsed} ms")

        ok
      }
    }

    var tasks = Seq.empty[Future[Boolean]]
    val len = accounts.length

    for(i<-0 until 1000){

      val p0 = rand.nextInt(len)
      val p1 = rand.nextInt(len)

      if(!p0.equals(p1)){

        val from = accounts(p0)
        val to = accounts(p1)

        tasks = tasks :+ transaction(Seq(from.id, to.id)){

          var b0 = from.balance
          var b1 = to.balance

          if(b0 > 0){
            val amount = rand.nextInt(0, b0)

            b0 = b0 - amount
            b1 = b1 + amount
          }

          from.balance = b0
          to.balance = b1
        }
      }
    }

    val start = System.currentTimeMillis()
    val results = Await.result(Future.sequence(tasks), 10 seconds)
    val elapsed = System.currentTimeMillis() - start

    val n = results.length
    val hits = results.count(_ == true)

    val rps = (n * 1000)/elapsed
    val avg = elapsed.toDouble/n

    println(s"\nn: ${n} hits: ${hits} rate: ${hits*100/n} % rps: ${rps}\n")
  }

}
