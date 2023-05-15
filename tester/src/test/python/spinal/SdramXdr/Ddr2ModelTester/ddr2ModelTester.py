import random
import cocotb
from cocotb.triggers import Timer

from Verilog_VCD.Verilog_VCD import parse_vcd
from spinal.SdramXdr.common.VcdLib import *


@cocotb.test()
def test1(dut):
    random.seed(0)
    from cocotblib.misc import cocotbXHack
    cocotbXHack()

    forks = []
    def map(component, net, apply, delay = 0):
        forks.append(cocotb.start_soon(stim(wave, component, net, apply, delay)))

    def assignSignalValueClosure(signal) -> function:
        # Capture: signal
        def assignSignalValue(value):
            signal.value = value
        return assignSignalValue

    wave = parse_vcd("../../../../../../../simWorkspace/SdramXdrCtrlPlusRtlPhy/test.vcd")
    phy = "TOP.SdramXdrCtrlPlusRtlPhy"
    top = "TOP"

    yield Timer(0)
    phaseCount = getLastValue(wave, top, "phaseCount")
    dataRate = 2
    phaseDelay = 0
    clockPeriod = getClockPeriod(wave, top, "clk")

    cocotb.start_soon(genClock(dut.ck, dut.ck_n, clockPeriod//phaseCount))

    list(map(top, "ADDR", assignSignalValueClosure(dut.addr)))
    list(map(top, "BA", assignSignalValueClosure(dut.ba)))
    list(map(top, "CASn", assignSignalValueClosure(dut.cas_n)))
    list(map(top, "CKE", assignSignalValueClosure(dut.cke)))
    list(map(top, "CSn", assignSignalValueClosure(dut.cs_n)))
    list(map(top, "RASn", assignSignalValueClosure(dut.ras_n)))
    list(map(top, "WEn", assignSignalValueClosure(dut.we_n)))
    list(map(top, "ODT", assignSignalValueClosure(dut.odt)))

    cocotb.start_soon(stimPulse(wave, top, "writeEnable", lambda v : cocotb.start_soon(genDqs(dut.dqs, dut.dqs_n, 1+v/clockPeriod*phaseCount*dataRate//2, clockPeriod//(phaseCount*dataRate)*(phaseCount*dataRate-1), clockPeriod//phaseCount))))

    for fork in forks:
        yield fork
