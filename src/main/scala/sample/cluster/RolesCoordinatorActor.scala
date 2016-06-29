package sample.cluster

import akka.actor._
import akka.cluster.ddata.Replicator.{GetFailure, NotFound, UpdateTimeout, _}
import akka.cluster.ddata._
import akka.cluster.{Cluster, Member}
import sample.cluster.RolesCoordinatorActor.CheckClusterState

import scala.concurrent.duration._

class RolesCoordinatorActor(singletonRoles: Set[String]) extends Actor with ActorLogging {

  val checkInterval = 5.seconds

  import context.dispatcher
  val checkClusterState = context.system.scheduler.schedule(checkInterval, checkInterval, self, CheckClusterState)

  implicit val cluster = Cluster(context.system)

  var rolesMapping: Map[String, Member] = Map.empty

  val replicator = DistributedData(context.system).replicator
  val DataKey = ORMultiMapKey[String]("singletonRoles")

  override def preStart(): Unit = {
    log.info("Roles coordinator started")
  }

  override def postStop(): Unit = {
    checkClusterState.cancel()
  }

  override def receive: Receive = {
    case CheckClusterState =>
      replicator ! Get(DataKey, ReadLocal)

    case g@GetSuccess(DataKey, req) =>
      val data = g.get(DataKey)
      val members = cluster.state.members.map(_.address.toString)

      val leavers: Set[String] = data.entries.keySet -- members
      leavers.foreach { member =>
        log.info(s"Node $member removed")
        replicator ! Update(DataKey, data, WriteLocal)(_ - member)
      }

      val rolesToRestore: Set[String] = singletonRoles.diff(data.entries.flatMap { case (key, values) => values }.toSet)

      if (rolesToRestore.nonEmpty) {
        log.info("Rebalancing cluster roles: {}", rolesToRestore.mkString("[", ", ", "]"))
        val redistribution = RolesCoordinatorActor.distribute(members, rolesToRestore, data.entries)

        log.info("Roles distribution:")
        redistribution.foreach { case (key, values) =>
          replicator ! Update(DataKey, ORMultiMap.empty[String], WriteLocal)(_ + (key -> values))

          if (values.nonEmpty) {
            log.info("Node {}: {}", key, values.mkString("[", ", ", "]"))
          }
        }
      }

    case NotFound(DataKey, req) =>
      replicator ! Update(DataKey, ORMultiMap.empty[String], WriteLocal)(r => r)
      log.warning(s"Data Key: $DataKey, does not exists, $req")
    case GetFailure(DataKey, req) =>
      log.warning(s"Cannot retrieve key: $DataKey, timeout occurred, $req")
    case UpdateTimeout(DataKey, req) =>
      log.warning(s"Cannot modify all nodes for key: $DataKey, within timeout $checkInterval, and $req")
  }
}

object RolesCoordinatorActor {
  type DataMap = Map[String, Set[String]]

  case object CheckClusterState

  def distribute(members: Set[String], roles: Set[String], originalMap: DataMap = Map()): DataMap = {
    require(members.nonEmpty, "Cluster members must be non empty")

    val newNodes: Set[String] = members.diff(originalMap.keySet)

    var result: DataMap = originalMap ++ newNodes.map(_ -> Set.empty[String]).toMap

    roles.foreach { role =>
      val (member, roles) = result.minBy {case (key, values) => values.size }
      val newRoles = roles + role
      result = result.updated(member, newRoles)
    }

    result
  }

  def props(singletonRoles: Set[String]): Props = Props(classOf[RolesCoordinatorActor], singletonRoles)
}