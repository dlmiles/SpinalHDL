package spinal.tester.scalatest.formal.boolector

import spinal.core._
import spinal.lib.formal._
import spinal.tester.scalatest.SpinalTesterFormalConfig

import scala.language.postfixOps

class FormalSimpleBoolectorTester extends SpinalFormalFunSuite {
  import spinal.core.formal._

  val engines = Seq(SmtBmc(solver = SmtBmcSolver.Boolector))

  class LimitedCounterEmbedded(initialValue: Int = 2) extends Component {
    val value = Reg(UInt(4 bits)) init (initialValue)
    when(value < 10) {
      value := value + 1
    }

    GenerationFlags.formal {
      assert(value >= 2)
      assert(value <= 10)
    }
  }

  def startFormalWithBMC(initialValue: Int = 2) = {
    SpinalTesterFormalConfig(this).withEngies(engines).withBMC(15).doVerify(new Component {
      val dut = FormalDut(new LimitedCounterEmbedded(initialValue))
      assumeInitial(ClockDomain.current.isResetActive)
    })
  }

  test("Formal_Boolector_Simple_pass") {
    assume(SpinalTesterFormalConfig.pathContainsBinary("boolector"), "Skipping: boolector missing from $PATH")
    startFormalWithBMC()
  }
  test("Formal_Boolector_Simple_fail") {
    assume(SpinalTesterFormalConfig.pathContainsBinary("boolector"), "Skipping: boolector missing from $PATH")
    shouldFail(startFormalWithBMC(1))
  }
}
