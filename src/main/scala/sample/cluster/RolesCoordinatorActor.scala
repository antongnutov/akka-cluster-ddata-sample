package sample.cluster

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator.{GetFailure, NotFound, UpdateTimeout, _}
import akka.cluster.ddata._
import sample.cluster.RolesCoordinatorActor.{CheckClusterState, DataMap}

import scala.concurrent.duration._

class RolesCoordinatorActor(roles: Set[String]) extends Actor with ActorLogging {

  val checkInterval = 5.seconds

  import context.dispatcher
  val checkClusterState = context.system.scheduler.schedule(checkInterval, checkInterval, self, CheckClusterState)

  implicit val cluster = Cluster(context.system)

  var rolesBound: DataMap = Map.empty

  val replicator = DistributedData(context.system).replicator
  val DataKeyRun = ORMultiMapKey[String]("runRoles")
  val DataKeyBound = ORMultiMapKey[String]("boundRoles")

  override def preStart(): Unit = {
    replicator ! Subscribe(DataKeyBound, self)
    log.info("Roles coordinator started")
  }

  override def postStop(): Unit = {
    replicator ! Unsubscribe(DataKeyBound, self)
    checkClusterState.cancel()
  }

  override def receive: Receive = {
    case CheckClusterState =>
      replicator ! Get(DataKeyRun, ReadLocal)
      replicator ! Get(DataKeyBound, ReadLocal)

    case g@GetSuccess(DataKeyBound, req) =>
      val data = g.get(DataKeyBound)
      val members = cluster.state.members.map(_.address.toString)
      val leavers: Set[String] = data.entries.keySet -- members
      leavers.foreach { member =>
        replicator ! Update(DataKeyBound, data, WriteLocal)(_ - member)
      }

    case c@Changed(DataKeyBound) =>
      rolesBound = c.get(DataKeyBound).entries
      log.info("Bound roles: {}", rolesBound)

    case g@GetSuccess(DataKeyRun, req) =>
      val data = g.get(DataKeyRun)
      val members = cluster.state.members.map(_.address.toString)

      val leavers: Set[String] = data.entries.keySet -- members
      leavers.foreach { member =>
        log.info(s"Node $member removed")
        replicator ! Update(DataKeyRun, data, WriteLocal)(_ - member)
      }

      val allBoundRoles = rolesBound.flatMap { case (key, values) => values }.toSet
      val rolesToRestore: Set[String] = roles.diff(data.entries.flatMap { case (key, values) => values }.toSet).filter(allBoundRoles(_))

      if (rolesToRestore.nonEmpty) {
        log.info("Rebalancing cluster roles: {}", rolesToRestore.mkString("[", ", ", "]"))
        val redistribution = RolesCoordinatorActor.distribute(members, rolesToRestore, rolesBound, data.entries)

        log.info("Roles distribution: {}", redistribution)
        redistribution.foreach { case (key, values) =>
          replicator ! Update(DataKeyRun, ORMultiMap.empty[String], WriteLocal)(_ + (key -> values))
        }
      }

    case NotFound(DataKeyRun, req) =>
      replicator ! Update(DataKeyRun, ORMultiMap.empty[String], WriteLocal)(r => r)
      log.warning(s"Data Key: $DataKeyRun, does not exists, $req")
    case GetFailure(DataKeyRun, req) =>
      log.warning(s"Cannot retrieve key: $DataKeyRun, timeout occurred, $req")
    case UpdateTimeout(dataKey, req) =>
      log.warning(s"Cannot modify all nodes for key: $dataKey, within timeout $checkInterval, and $req")
  }
}

object RolesCoordinatorActor {
  type DataMap = Map[String, Set[String]]

  case object CheckClusterState

  def distribute(members: Set[String], roles: Set[String], rolesBound: DataMap, originalMap: DataMap = Map()): DataMap = {
    require(members.nonEmpty, "Cluster members must be non empty")

    val newNodes: Set[String] = members.diff(originalMap.keySet)

    var result: DataMap = originalMap ++ newNodes.map(_ -> Set.empty[String]).toMap

    roles.foreach { role =>
      val nodesWithRole = result.filter { case (key, values) => rolesBound.get(key).exists(_ (role)) }
      if (nodesWithRole.nonEmpty) {
        val (member, roles) = nodesWithRole.minBy { case (key, values) => values.size }
        val newRoles = roles + role
        result = result.updated(member, newRoles)
      }
    }

    result
  }

  def props(roles: Set[String]): Props = Props(classOf[RolesCoordinatorActor], roles)
}