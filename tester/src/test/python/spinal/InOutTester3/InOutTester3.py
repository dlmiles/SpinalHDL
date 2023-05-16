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
        writeEnable = str(dut.bus_cmd_writeenable.value)
        write = str(dut.bus_cmd_write.value)
        expected = ""
        for i in range(8):
            if(writeEnable[i] == '0'):
                expected = expected + "Z"
            else:
                expected = expected + write[i]
        dut._log.info("bus_cmd_read={} bus_gpio={} expected={}".format(
            dut.bus_cmd_read.value,
            dut.bus_gpio.value,
            expected
        ))
        assert expected == dut.bus_cmd_read.value, f"{expected} == {dut.bus_cmd_read.value}"
        assert expected == dut.bus_gpio.value, f"{expected} == {dut.bus_gpio.value}"



    dut._log.info("Cocotb test done")
