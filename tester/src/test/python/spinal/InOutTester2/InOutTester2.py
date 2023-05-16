import random

import cocotb
from cocotb.triggers import Timer, RisingEdge

from cocotblib.misc import randSignal, assertEquals, truncUInt, ClockDomainAsyncReset, Bundle


@cocotb.test()
def test1(dut):

    dut._log.info("Cocotb test boot")

    def assertGpio(value):
        dut._log.info("cmd_read={} bus_gpio={} bus_cmd_read={} buscpy_gpio_readed={} value={}".format(
            dut.cmd_read.value,
            dut.bus_gpio.value,
            dut.bus_cmd_read.value,
            dut.buscpy_gpio_readed.value,
            value
        ))
        assert dut.cmd_read.value == value, f"{dut.cmd_read.value} == {value}"
        assert dut.bus_gpio.value == value, f"{dut.bus_gpio.value} == {value}"
        assert dut.bus_cmd_read.value == value, f"{dut.bus_cmd_read.value} == {value}"
        assert dut.buscpy_gpio_readed.value == value, f"{dut.buscpy_gpio_readed.value} == {value}"

    @cocotb.coroutine
    def stim(drivers):
        for i in range(100):
            for toidle in drivers:
                randSignal(toidle.write)
                toidle.writeenable.value = 0
            driver = random.choice(drivers)
            randSignal(driver.writeenable)
            dut._log.info("driver={} write={} writeenable={}".format(
                driver,
                driver.write.value,
                driver.writeenable.value
            ))
            yield Timer(10)
            if driver.writeenable.value == False:
                assertGpio("zzzzzzzz")
            else:
                assertGpio(driver.write.value)


    drivers = [Bundle(dut,"bus_cmd"), Bundle(dut,"cmd"), Bundle(dut,"cmdbb")]
    yield stim(drivers)



    dut._log.info("Cocotb test done")
