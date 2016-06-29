package sample.cluster

import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import sample.cluster.RoleManager.BindRole

import scala.collection.JavaConverters._

/**
  * @author Anton Gnutov
  */
object Main extends App {
  val log = LoggerFactory.getLogger(Main.getClass)
  val config = ConfigFactory.load()


  log.debug("Starting actor system ...")
  val system = ActorSystem("sample")

  val roleManager = system.actorOf(RoleManager.props(), "roleManager")
  val roles = config.getStringList("sample.roles").asScala

  roles.foreach { role =>
    roleManager ! BindRole(role, _ => {
      log.info(s"Role $role started!!!")
    })
  }

  system.actorOf(ClusterSingletonManager.props(
    singletonProps = RolesCoordinatorActor.props(roles.toSet),
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)),
    name = "clusterManager")

  Cluster(system).registerOnMemberRemoved {
    log.warn("Removed from cluster, terminating actor system ...")
    system.terminate()
  }

  sys.addShutdownHook {
    system.terminate()
  }
}