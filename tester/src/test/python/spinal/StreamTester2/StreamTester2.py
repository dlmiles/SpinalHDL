import random

import cocotb
from cocotblib.Phase import PhaseManager
from cocotblib.misc import set_timeout, ClockDomainAsyncReset, simulationSpeedPrinter, randBits

from cocotblib.Stream import Transaction, Stream, StreamFifoTester


def bundleAGen():
    trans = Transaction()
    trans.a = randBits(8)
    trans.b = randBits(1)
    return trans

@cocotb.test()
def test1(dut):
    set_timeout()
    random.seed(0)
    from cocotblib.misc import cocotbXHack
    cocotbXHack()

    cocotb.start_soon(ClockDomainAsyncReset(dut.clk, dut.reset))
    cocotb.start_soon(simulationSpeedPrinter(dut.clk))


    phaseManager = PhaseManager()
    phaseManager.setWaitTasksEndTime(1000*200)

    StreamFifoTester("fifoA",phaseManager,Stream(dut,"fifoAPush"),Stream(dut,"fifoAPop"),bundleAGen,3000,dut.clk,dut.reset).createInfrastructure()
    StreamFifoTester("fifoB",phaseManager,Stream(dut,"fifoBPush"),Stream(dut,"fifoBPop"),bundleAGen,3000,dut.clk,dut.reset).createInfrastructure()


    yield phaseManager.run()

