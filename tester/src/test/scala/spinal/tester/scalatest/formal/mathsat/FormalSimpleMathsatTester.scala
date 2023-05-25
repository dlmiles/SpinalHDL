package spinal.tester.scalatest.formal.mathsat

import spinal.core._
import spinal.lib.formal._
import spinal.tester.scalatest.SpinalTesterFormalConfig

import scala.language.postfixOps

class FormalSimpleMathsatTester extends SpinalFormalFunSuite {
  import spinal.core.formal._

  val engines = Seq(SmtBmc(solver = SmtBmcSolver.mathsat))

  class LimitedCounterEmbedded(initialValue: Int = 2, label: String = null) extends Component {
    val value = Reg(UInt(4 bits)) init (initialValue)
    when(value < 10) {
      value := value + 1
    }

    GenerationFlags.formal {
      assert(value >= 2)
      assert(value <= 10)
    }
  }

  def startFormalWithBMC(initialValue: Int = 2, label: String = null) = {
    SpinalTesterFormalConfig(this, label).withEngies(engines).withBMC(15).doVerify(new Component {
      val dut = FormalDut(new LimitedCounterEmbedded(initialValue))
      assumeInitial(ClockDomain.current.isResetActive)
    })
  }

  test("Formal_mathsat_Simple_pass") {
    assume(SpinalTesterFormalConfig.pathContainsBinary("mathsat"), "Skipping: mathsat missing from $PATH")
    startFormalWithBMC(label="Formal_mathsat_Simple_pass")
  }
  test("Formal_mathsat_Simple_fail") {
    assume(SpinalTesterFormalConfig.pathContainsBinary("mathsat"), "Skipping: mathsat missing from $PATH")
    shouldFail(startFormalWithBMC(1, label="Formal_mathsat_Simple_fail"))
  }
}
