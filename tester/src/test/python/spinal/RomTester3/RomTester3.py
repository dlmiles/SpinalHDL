import cocotb
from cocotb.triggers import Timer, RisingEdge

from cocotblib.misc import set_timeout, randSignal, assertEquals, truncUInt, ClockDomainAsyncReset
import random


@cocotb.test()
def test1(dut):
    set_timeout()
    dut._log.info("Cocotb test boot")
    #random.seed(0)
    table = [0x01234567,0x12345670,0x10293857,0x0abcfe23,0x02938571,0xabcfe230,0x717833aa,0x17833aa6]

    for i in range(1000):
        dut.address.value = random.getrandbits(3)
        yield Timer(10)
        assertEquals(dut.data,table[int(dut.address)],"1")



    dut._log.info("Cocotb test done")
