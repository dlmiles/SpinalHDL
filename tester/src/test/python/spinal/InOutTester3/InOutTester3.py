import random

import cocotb
from cocotb.triggers import Timer, RisingEdge

from cocotblib.misc import randSignal, assertEquals, truncUInt, ClockDomainAsyncReset, Bundle


@cocotb.test()
def test1(dut):

    dut._log.info("Cocotb test boot")

    for i in range(100):
        randSignal(dut.bus_cmd_writeenable)
        randSignal(dut.bus_cmd_write)
        yield Timer(10)
        writeEnable = dut.bus_cmd_writeenable.value
        write = dut.bus_cmd_write.value
        expected = ""
        for i in range(8):
            if(writeEnable[i] == '0'):
                expected = expected + "z"
            else:
                expected = expected + write[i]
        dut._log.info("cread={} gpio={} expected={}".format(
            dut.bus_cmd_read.value,
            dut.bus_gpio.value,
            expected
        ))
        assert expected == dut.bus_cmd_read.value.lower(), f"{expected} == {dut.bus_cmd_read.value.lower()}"
        assert expected == dut.bus_gpio.value.lower(), f"{expected} == {dut.bus_gpio.value.lower()}"



    dut._log.info("Cocotb test done")
