import cocotb
from cocotb.triggers import Timer

from cocotblib.misc import set_timeout, randSignal, assertEquals, ClockDomainAsyncReset


class Ref:
    def __init__(self,dut):
        pass

@cocotb.test()
def test1(dut):
    set_timeout()
    dut._log.info("Cocotb test boot")
    from cocotblib.misc import cocotbXHack
    cocotbXHack()
    #random.seed(0)

    cocotb.start_soon(ClockDomainAsyncReset(dut.clk, None))

    for i in range(0,1000):
        randSignal(dut.io_inSIntA)
        randSignal(dut.io_inSIntB)
        yield Timer(1000)
        ref = Ref(dut)
        assertEquals(dut.io_outSInt, dut.io_outSIntRef, "io_outSInt")

    dut._log.info("Cocotb test done")
