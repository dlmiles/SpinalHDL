package spinal.tester.scalatest

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.sim.{SimBoolPimper, SimConfig}
import spinal.core.{Bool, Bundle, Component, in, out}
import spinal.tester.scalatest.VerilatorEncodeNameTester.VerilatorEncodeName

import scala.language.postfixOps

object VerilatorEncodeNameTester {

  case class VerilatorEncodeName(setNameSeq: Seq[String] = Seq.empty[String]) extends Component {
    val COUNT = setNameSeq.size
    val io = new Bundle {
      val ii = in Bool()
      val oo = out Vec(Bool(), COUNT)
    }
    for(i <- setNameSeq.indices) {
      if(setNameSeq(i) != null)
        io.oo(i).setName(setNameSeq(i))
      io.oo(i) := io.ii
    }
  }
}

class VerilatorEncodeNameTester extends AnyFunSuite {
  test("verilator encodeName") {
    val cfg = SimConfig

    // try to compile once to keep testsuite speed up
    cfg
      .compile(VerilatorEncodeName(Seq(null, "abc123", "abc$234", "abc_345", "_abc456", "__abc567", "abc___678", "abc_$_789")))
      .doSim(dut => assert(dut.io.oo(0).toBoolean == dut.io.ii.toBoolean))

    // Verilog doesn't allow identifiers to start with a digit either,
    //  even though verilator has a corner case to handle exactly this.
    //cfg.compile(VerilatorEncodeName(Seq("123abc"))).doSim(dut => assert(dut.io.oo(0).toBoolean == dut.io.ii.toBoolean))
  }

}
