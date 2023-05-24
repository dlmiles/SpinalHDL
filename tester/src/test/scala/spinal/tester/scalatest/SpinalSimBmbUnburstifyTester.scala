package spinal.tester.scalatest

import spinal.lib.bus.bmb.sim.BmbBridgeTester
import spinal.lib.bus.bmb.{BmbAccessParameter, BmbLengthFixer, BmbParameter, BmbSourceParameter, BmbUnburstify}

class SpinalSimBmbUnburstifyTester extends SpinalAnyFunSuite {
  test("miaou") {
    SpinalTesterSimConfig(this, "miaou").compile {
      val c = BmbUnburstify(
        inputParameter = BmbAccessParameter(
          addressWidth = 16,
          dataWidth = 32
        ).addSources(16, BmbSourceParameter(
          lengthWidth = 6,
          contextWidth = 3,
          alignmentMin = 2,
          canRead = true,
          canWrite = true,
          alignment = BmbParameter.BurstAlignement.WORD
        )).toBmbParameter()
      )
      c
    }.doSimUntilVoid("test") { dut =>
      new BmbBridgeTester(
        master = dut.io.input,
        masterCd = dut.clockDomain,
        slave = dut.io.output,
        slaveCd = dut.clockDomain,
        alignmentMinWidth = dut.inputParameter.access.alignmentMin,
        rspCountTarget = 1000
      )
    }
  }

  test("miaou2") {
    SpinalTesterSimConfig(this, "miaou2").compile {
      val c = BmbUnburstify(
        inputParameter = BmbAccessParameter(
          addressWidth = 16,
          dataWidth = 32
        ).addSources(16, BmbSourceParameter(
          lengthWidth = 2,
          contextWidth = 3,
          alignmentMin = 2,
          canRead = true,
          canWrite = true,
          alignment = BmbParameter.BurstAlignement.WORD
        )).toBmbParameter()
      )
      c
    }.doSimUntilVoid("test") { dut =>
      new BmbBridgeTester(
        master = dut.io.input,
        masterCd = dut.clockDomain,
        slave = dut.io.output,
        slaveCd = dut.clockDomain,
        alignmentMinWidth = dut.inputParameter.access.alignmentMin,
        rspCountTarget = 1000
      )
    }
  }
}
