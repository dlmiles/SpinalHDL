import random
import cocotb
from cocotb.triggers import Timer
from cocotblib.misc import set_timeout

from Verilog_VCD.Verilog_VCD import parse_vcd
from spinal.SdramXdr.common.VcdLib import *


@cocotb.test()
def test1(dut):
    set_timeout()
    random.seed(0)
    from cocotblib.misc import cocotbXHack
    cocotbXHack()

    forks = []
    def map(component, net, apply, delay = 0):
        forks.append(cocotb.start_soon(stim(wave, component, net, apply, delay)))

    wave = parse_vcd("../../../../../../../simWorkspace/SdramXdrCtrlPlusRtlPhy/test.vcd")
    top = "TOP"

    yield Timer(0)
    phaseCount = getLastValue(wave, top, "phaseCount")
    clockPeriod = getClockPeriod(wave, top, "clk")

    cocotb.start_soon(genClock(dut.Clk, None, clockPeriod//phaseCount))

    list(map(top, "ADDR", lambda v : dut.Addr <= v))
    list(map(top, "BA", lambda v : dut.Ba <= v))
    list(map(top, "CASn", lambda v : dut.Cas_n <= v))
    list(map(top, "CKE", lambda v : dut.Cke <= v))
    list(map(top, "CSn", lambda v : dut.Cs_n <= v))
    list(map(top, "RASn", lambda v : dut.Ras_n <= v))
    list(map(top, "WEn", lambda v : dut.We_n <= v))


    for fork in forks:
        yield fork
