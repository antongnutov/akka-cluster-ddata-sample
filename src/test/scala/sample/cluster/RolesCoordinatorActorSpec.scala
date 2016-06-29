package sample.cluster

import org.scalatest.{FlatSpec, Matchers}

/**
  * @author Anton Gnutov
  */
class RolesCoordinatorActorSpec extends FlatSpec with Matchers {
  val roles = Set("role1", "role2", "role3", "role4", "role5", "role6")
  val boundRoles = Map("node1" -> Set("role1", "role2", "role3"), "node2" -> Set("role4", "role5", "role6"))

  "RolesCoordinatorActor" should "distribute roles at start" in {
    val distribution = RolesCoordinatorActor.distribute(Set("node1", "node2"), roles, boundRoles)
    distribution shouldBe boundRoles
  }

  it should "distribute roles 1" in {
    val distribution = RolesCoordinatorActor.distribute(Set("node1", "node2"), roles - "role1", boundRoles, Map("node1" -> Set("role1")))
    distribution shouldBe boundRoles
  }

  it should "distribute roles 2" in {
    val distribution = RolesCoordinatorActor.distribute(Set("node1", "node2"), Set("role4", "role5", "role6"), boundRoles, Map("node1" -> Set("role1", "role2", "role3")))
    distribution shouldBe boundRoles
  }
}
