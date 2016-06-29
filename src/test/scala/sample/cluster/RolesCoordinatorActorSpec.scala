package sample.cluster

import org.scalatest.{FlatSpec, Matchers}

/**
  * @author Anton Gnutov
  */
class RolesCoordinatorActorSpec extends FlatSpec with Matchers {
  val roles = Set("role1", "role2", "role3", "role4", "role5", "role6")

  "RolesCoordinatorActor" should "distribute roles at start" in {
    val distribution = RolesCoordinatorActor.distribute(Set("node1", "node2"), roles)
    distribution.size shouldBe 2
    distribution("node1").size shouldBe 3
    distribution("node2").size shouldBe 3
  }

  it should "distribute roles 1" in {
    val distribution = RolesCoordinatorActor.distribute(Set("node1", "node2"), roles - "role1", Map("node1" -> Set("role1")))
    distribution.size shouldBe 2
    distribution("node1")("role1") shouldBe true
    distribution("node1").size shouldBe 3
    distribution("node2").size shouldBe 3
  }

  it should "distribute roles 2" in {
    val distribution = RolesCoordinatorActor.distribute(Set("node1", "node2"), Set("role4", "role5", "role6"), Map("node1" -> Set("role1", "role2", "role3")))
    distribution shouldBe Map("node1" -> Set("role1", "role2", "role3"), "node2" -> Set("role4", "role5", "role6"))
  }
}
