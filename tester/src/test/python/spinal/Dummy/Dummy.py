import cocotb
from cocotb.triggers import Timer
from cocotblib.misc import set_timeout


@cocotb.test()
def test1(dut):
    set_timeout()
    dut._log.info("Cocotb test boot")
    yield Timer(1000)
    dut._log.info("Cocotb test done")
