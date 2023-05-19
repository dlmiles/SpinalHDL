import cocotb
from cocotb.triggers import Timer
from cocotblib.misc import set_timeout

@cocotb.test()
def my_first_test(dut):
    """
    Try accessing the design
    """
    set_timeout()
    dut._log.info("Running test!")
    dut.incrementBy = 2
    dut.clear = 0
    dut.reset = 1
    yield Timer(1000)
    dut.reset = 0
    for cycle in range(100):
        dut.clk = 0
        yield Timer(1000)
        dut.clk = 1
        yield Timer(1000)
    dut._log.info("Running test!")