package spinal.tester.scalatest.formal.mathsat

import spinal.core._
import spinal.lib.formal._
import spinal.tester.scalatest.SpinalTesterSimConfig

import scala.language.postfixOps

class FormalSimpleMathsatTester extends SpinalFormalFunSuite {
  import spinal.core.formal._

  val engines = Seq(SmtBmc(solver = SmtBmcSolver.mathsat))

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

  test("Formal_mathsat_Simple_pass") {
    assume(SpinalTesterSimConfig.pathContainsBinary("mathsat"), "Skipping: mathsat missing from $PATH")
    startFormalWithBMC()
  }
  test("Formal_mathsat_Simple_fail") {
    assume(SpinalTesterSimConfig.pathContainsBinary("mathsat"), "Skipping: mathsat missing from $PATH")
    shouldFail(startFormalWithBMC(1))
  }
}
