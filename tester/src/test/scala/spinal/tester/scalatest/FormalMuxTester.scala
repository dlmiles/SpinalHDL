package spinal.tester.scalatest

import spinal.core._
import spinal.core.formal._
import spinal.lib._
import spinal.lib.formal._

import scala.language.postfixOps

class FormalMuxTester extends SpinalFormalFunSuite {
  def formalmux(selWithCtrl: Boolean = false, label: String = null) = {
    SpinalTesterFormalConfig(this, label)
      .withBMC(20)
      .withProve(20)
      .withCover(20)
      // .withDebug
      .doVerify(new Component {
        val portCount = 5
        val dataType = Bits(8 bits)
        val dut = FormalDut(new StreamMux(dataType, portCount))

        val reset = ClockDomain.current.isResetActive

        assumeInitial(reset)

        val muxSelect = anyseq(UInt(log2Up(portCount) bit))
        val muxInputs = Vec(slave(Stream(dataType)), portCount)
        val muxOutput = master(Stream(dataType))

        dut.io.select := muxSelect
        muxOutput << dut.io.output

        assumeInitial(muxSelect < portCount)
        val selStableCond = if (selWithCtrl) past(muxOutput.isStall) else null

        when(reset || past(reset)) {
          for (i <- 0 until portCount) {
            assume(muxInputs(i).valid === False)
          }
        }

        if (selWithCtrl) {
          cover(selStableCond)
          when(selStableCond) {
            assume(stable(muxSelect))
          }
        }
        muxOutput.formalAssertsMaster()
        muxOutput.formalCovers(5)

        for (i <- 0 until portCount) {
          muxInputs(i) >> dut.io.inputs(i)
          muxInputs(i).formalAssumesSlave()
        }

        cover(muxOutput.fire)

        for (i <- 0 until portCount) {
          cover(dut.io.select === i)
          muxInputs(i).formalAssumesSlave()
        }

        when(muxSelect < portCount) {
          assert(muxOutput === muxInputs(muxSelect))
        }
      })
  }
  test("mux_sel_with_control") {
    formalmux(true, label="mux_sel_with_control")
  }
  test("mux_sel_without_control") {
    shouldFail(formalmux(false, label="mux_sel_without_control"))
  }

  test("mux_with_selector") {
    SpinalTesterFormalConfig(this, "mux_with_selector")
      .withProve(20)
      .withCover(20)
      .doVerify(new Component {
        val portCount = 5
        val dataType = Bits(8 bits)
        val dut = FormalDut(new StreamMux(dataType, portCount))
        val selector = dut.io.createSelector()

        val reset = ClockDomain.current.isResetActive
        assumeInitial(reset)

        val muxSelector = slave(cloneOf(selector))
        muxSelector >> selector
        val muxInputs = Vec(slave(Stream(dataType)), portCount)
        for (i <- 0 until portCount) {
          muxInputs(i) >> dut.io.inputs(i)
        }
        val muxOutput = master(Stream(dataType))
        muxOutput << dut.io.output

        assumeInitial(muxSelector.payload < portCount)
        muxSelector.formalAssumesSlave()

        when(reset || past(reset)) {
          for (i <- 0 until portCount) {
            assume(muxInputs(i).valid === False)
          }
          assume(muxSelector.valid === False)
        }

        muxOutput.formalAssertsMaster()
        muxOutput.formalCovers(5)
        cover(dut.io.select =/= muxSelector.payload)
        cover(changed(muxSelector.payload) & stable(muxSelector.valid) & muxSelector.valid)
        val readyBits = muxInputs.map(_.ready).asBits()
        assert((readyBits === 0) || (CountOne(readyBits) === 1))

        for (i <- 0 until portCount) {
          cover(dut.io.select === i)
          muxInputs(i).formalAssumesSlave()
        }

        when(dut.io.select < portCount) {
          assert(muxOutput === muxInputs(dut.io.select))
        }
      })

  }
}
