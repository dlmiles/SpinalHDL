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
        writeEnable = dut.bus_cmd_writeenable._path
        write = dut.bus_cmd_write._path
        expected = ""
        for i in range(8):
            if(writeEnable[i] == '0'):
                expected = expected + "z"
            else:
                expected = expected + write[i]
        assert expected == dut.bus_cmd_read._path.lower(), f"$expected == ${dut.bus_cmd_read_path._path.lower()}"
        assert expected == dut.bus_gpio._path.lower(), f"$expected == ${dut.bus_gpio._path.lower()}"



    dut._log.info("Cocotb test done")
