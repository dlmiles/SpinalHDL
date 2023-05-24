package spinal.tester.scalatest

import spinal.lib.bus.bmb.sim.BmbBridgeTester
import spinal.lib.bus.bmb.{BmbAccessParameter, BmbAlignedSpliter, BmbParameter, BmbSourceParameter}

class SpinalSimBmbLengthSpliterTester extends SpinalAnyFunSuite {
  for(w <- List(false, true); r <- List(false, true);   if w || r) {
    val header = "_" + (if (w) "w" else "") + (if (r) "r" else "")
    test("bypass" + header) {
      SpinalTesterSimConfig(this, "bypass" + header).compile {
        val c = BmbAlignedSpliter(
          ip = BmbAccessParameter(
            addressWidth = 16,
            dataWidth = 32
          ).addSources(16, BmbSourceParameter(
            lengthWidth = 6,
            contextWidth = 3,
            alignmentMin = 0,
            canRead = r,
            canWrite = w,
            alignment = BmbParameter.BurstAlignement.WORD
          )).toBmbParameter(),
          lengthMax = 4
        )
        c
      }.doSimUntilVoid("test") { dut =>
        new BmbBridgeTester(
          master = dut.io.input,
          masterCd = dut.clockDomain,
          slave = dut.io.output,
          slaveCd = dut.clockDomain,
          alignmentMinWidth = dut.ip.access.alignmentMin
        )
      }
    }

    test("8" + header) {
      SpinalTesterSimConfig(this, "8" + header).withWave.compile {
        val c = BmbAlignedSpliter(
          ip = BmbAccessParameter(
            addressWidth = 16,
            dataWidth = 32
          ).addSources(16, BmbSourceParameter(
            lengthWidth = 6,
            contextWidth = 8,
            alignmentMin = 0,
            canRead = r,
            canWrite = w,
            alignment = BmbParameter.BurstAlignement.WORD
          )).toBmbParameter(),
          lengthMax = 8
        )
        c
      }.doSimUntilVoid("test", 42) { dut =>
        new BmbBridgeTester(
          master = dut.io.input,
          masterCd = dut.clockDomain,
          slave = dut.io.output,
          slaveCd = dut.clockDomain,
          alignmentMinWidth = dut.ip.access.alignmentMin
        )
      }
    }

    test("16" + header) {
      SpinalTesterSimConfig(this, "16" + header).compile {
        val c = BmbAlignedSpliter(
          ip = BmbAccessParameter(
            addressWidth = 16,
            dataWidth = 32
          ).addSources(16, BmbSourceParameter(
            lengthWidth = 6,
            contextWidth = 8,
            alignmentMin = 0,
            canRead = r,
            canWrite = w,
            alignment = BmbParameter.BurstAlignement.WORD
          )).toBmbParameter(),
          lengthMax = 16
        )
        c
      }.doSimUntilVoid("test") { dut =>
        new BmbBridgeTester(
          master = dut.io.input,
          masterCd = dut.clockDomain,
          slave = dut.io.output,
          slaveCd = dut.clockDomain,
          alignmentMinWidth = dut.ip.access.alignmentMin
        )
      }
    }
  }
}
