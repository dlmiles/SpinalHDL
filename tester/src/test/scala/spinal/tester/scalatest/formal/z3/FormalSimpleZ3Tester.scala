package spinal.tester.scalatest.formal.z3

import spinal.core._
import spinal.lib.formal._
import spinal.tester.scalatest.SpinalTesterSimConfig

import scala.language.postfixOps

class FormalSimpleZ3Tester extends SpinalFormalFunSuite {
  import spinal.core.formal._

  val engines = Seq(SmtBmc(solver = SmtBmcSolver.Z3))

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
    FormalConfig.withEngies(engines).withBMC(15).doVerify(new Component {
      val dut = FormalDut(new LimitedCounterEmbedded(initialValue))
      assumeInitial(ClockDomain.current.isResetActive)
    })
  }

  test("Formal_Z3_Simple_pass") {
    assume(SpinalTesterSimConfig.pathContainsBinary("z3"), "Skipping: z3 missing from $PATH")
    startFormalWithBMC()
  }
  test("Formal_Z3_Simple_fail") {
    assume(SpinalTesterSimConfig.pathContainsBinary("z3"), "Skipping: z3 missing from $PATH")
    shouldFail(startFormalWithBMC(1))
  }
}
