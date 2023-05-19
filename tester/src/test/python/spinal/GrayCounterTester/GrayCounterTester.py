import cocotb
from cocotb.triggers import Timer, RisingEdge

from cocotblib.misc import set_timeout, randSignal, assertEquals, truncUInt, ClockDomainAsyncReset


class Ref:
    def __init__(self,dut):
        self.dut = dut
        self.value = 0
        cocotb.start_soon(self.clockProcess())

    @cocotb.coroutine
    def clockProcess(self):
        while True:
            yield RisingEdge(self.dut.clk)
            if int(self.dut.enable) == 1:
                yield Timer(1)
                self.value = truncUInt(self.value + 1, self.dut.gray)

    def getGray(self):
        return (self.value >> 1) ^ self.value

@cocotb.test()
def test1(dut):
    set_timeout()
    dut._log.info("Cocotb test boot")
    #random.seed(0)

    cocotb.start_soon(ClockDomainAsyncReset(dut.clk, dut.reset))

    ref = Ref(dut)

    for i in range(0,1000):
        randSignal(dut.enable)
        yield RisingEdge(dut.clk)
        assertEquals(ref.getGray(),dut.gray,"gray")
    dut._log.info("Cocotb test done")
