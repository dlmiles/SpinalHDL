package spinal.tester.scalatest.formal.cvc5

import spinal.core._
import spinal.lib.formal._
import spinal.tester.scalatest.SpinalTesterSimConfig

import scala.language.postfixOps

class FormalSimpleCvc5Tester extends SpinalFormalFunSuite {
  import spinal.core.formal._

  val engines = Seq(SmtBmc(solver = SmtBmcSolver.cvc5))

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

  test("Formal_cvc5_Simple_pass") {
    assume(SpinalTesterSimConfig.pathContainsBinary("cvc5"), "Skipping: cvc5 missing from $PATH")
    startFormalWithBMC()
  }
  test("Formal_cvc5_Simple_fail") {
    assume(SpinalTesterSimConfig.pathContainsBinary("cvc5"), "Skipping: cvc5 missing from $PATH")
    shouldFail(startFormalWithBMC(1))
  }
}
