package sample.cluster

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, ORMultiMap, ORMultiMapKey}
import sample.cluster.RoleManager.{BindRole, RoleAction, StartRole}

/**
  * @author Anton Gnutov
  */
class RoleManager extends Actor with ActorLogging {
  var roleBinding: Map[String, RoleAction] = Map.empty
  var startedRoles: Set[String] = Set.empty

  implicit val cluster = Cluster(context.system)

  val replicator = DistributedData(context.system).replicator
  val DataKeyRun = ORMultiMapKey[String]("runRoles")
  val DataKeyBound = ORMultiMapKey[String]("boundRoles")

  override def preStart(): Unit = {
    replicator ! Subscribe(DataKeyRun, self)
    log.info("RoleManager Started")
  }

  override def postStop(): Unit = {
    replicator ! Unsubscribe(DataKeyRun, self)
    log.info("RoleManager Stopped")
  }

  override def receive: Receive = {
    case c@Changed(DataKeyRun) =>
      c.get(DataKeyRun).entries.get(cluster.selfAddress.toString).foreach { entries =>
        entries.foreach(self ! StartRole(_))
      }

    case BindRole(role, action) =>
      log.info("Binding role {} ...", role)
      roleBinding += (role -> action)

      val address = cluster.selfAddress.toString
      replicator ! Update(DataKeyBound, ORMultiMap.empty[String], WriteLocal)(_ + (address -> roleBinding.keySet))

    case StartRole(role) if startedRoles(role) =>
      log.debug("Role {} already started", role)

    case StartRole(role) =>
      roleBinding.get(role) match {
        case Some(action: RoleAction) =>
          action()
          startedRoles += role
        case None =>
          log.warning("Role {} is not bound", role)
      }
  }
}

object RoleManager {
  type RoleAction = Unit => Unit

  case class BindRole(role: String, f: RoleAction)
  case class StartRole(role: String)

  def props(): Props = Props(classOf[RoleManager])
}