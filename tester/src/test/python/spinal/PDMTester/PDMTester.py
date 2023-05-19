import random
from queue import Queue

import cocotb
from cocotb.result import TestFailure
from cocotb.triggers import Timer, Edge, RisingEdge, Join, FallingEdge
from cocotblib.misc import set_timeout


@cocotb.coroutine
def genClock(dut, nCycles):
     for cycle in range(nCycles):
        dut.clk.value = 0
        yield Timer(10)
        dut.clk.value = 1
        yield Timer(10)

@cocotb.coroutine
def testDensity(dut, density):
    dut.io_enable.value = 0
    yield genClock(dut, 1)
    dut.io_enable.value = 1
    dut.io_density.value = density
    yield genClock(dut, 1)
    pulseCounter = 0
    for cycle in range(256):
        dut.clk.value = 0
        pulseCounter = pulseCounter + 1 if (dut.io_output == 1) else pulseCounter
        yield Timer(10)
        dut.clk.value = 1
        yield Timer(10)
    dut._log.info("Pulse Count : %s expected %s" % (str(pulseCounter),str(density)))
    if pulseCounter != density:
        raise TestFailure("FAIL: Pulse Count : %d != %d" % (int(pulseCounter), int(density)))

@cocotb.test()
def main_test(dut):
    set_timeout()
    dut.reset.value = 1
    yield genClock(dut, 2)
    dut.reset.value = 0

    for i in range(50):
        yield testDensity(dut, random.randint(0,256))

    yield testDensity(dut, 0)
    yield testDensity(dut, 256)

