import random

import cocotb
from cocotb.triggers import Timer, RisingEdge

from cocotblib.misc import randSignal, assertEquals, truncUInt, ClockDomainAsyncReset, Bundle


@cocotb.test()
def test1(dut):

    dut._log.info("Cocotb test boot")

    def assertGpio(value):
        assert(dut.cmd_read._path.lower() == value, f"{dut.cmd_read._path.lower()} == {value}")
        assert(dut.bus_gpio._path.lower() == value, f"{dut.bus_gpio._path.lower()} == {value}")
        assert(dut.bus_cmd_read._path.lower() == value, f"{dut.bus_cmd_read._path.lower()} == {value}")
        assert(dut.buscpy_gpio_readed._path.lower() == value, f"{dut.buscpy_gpio_readed._path.lower()} == {value}")

    @cocotb.coroutine
    def stim(drivers):
        for i in range(100):
            for toidle in drivers:
                randSignal(toidle.write)
                toidle.writeenable.value <= 0
            driver = random.choice(drivers)
            randSignal(driver.writeenable)
            yield Timer(10)
            if driver.writeenable == False:
                assertGpio("zzzzzzzz")
            else:
                assertGpio(driver.write._path)


    drivers = [Bundle(dut,"bus_cmd"),Bundle(dut,"cmd"),Bundle(dut,"cmdbb")]
    yield stim(drivers)



    dut._log.info("Cocotb test done")
