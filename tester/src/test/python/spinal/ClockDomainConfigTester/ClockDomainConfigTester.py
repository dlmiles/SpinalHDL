import random

import cocotb
from cocotb.triggers import Timer, RisingEdge, FallingEdge

from cocotblib.misc import set_timeout, assertEquals


@cocotb.coroutine
def ClockDomainGen(dut):
    dut.clk.value = 0
    dut.clkn.value = 1
    dut.asyncReset.value = 0
    dut.asyncResetn.value = 1
    dut.syncReset.value = 0
    dut.syncResetn.value = 1
    dut.softReset.value = 0
    dut.softResetn.value = 1
    dut.enable.value = 0
    dut.enablen.value = 1

    while True:
        yield Timer(1000)
        if random.random() < 0.5:
            dut.clk.value = 1-int(dut.clk)
        if random.random() < 0.5:
            dut.clkn.value = 1 - int(dut.clkn)
        yield Timer(1000)
        if random.random() < 0.1:
            dut.syncReset.value = 1 - int(dut.syncReset)
        if random.random() < 0.1:
            dut.syncResetn.value = 1 - int(dut.syncResetn)
        if random.random() < 0.1:
            dut.softReset.value = 1 - int(dut.softReset)
        if random.random() < 0.1:
            dut.softResetn.value = 1 - int(dut.softResetn)
        if random.random() < 0.1:
            dut.enable.value = 1 - int(dut.enable)
        if random.random() < 0.1:
            dut.enablen.value = 1 - int(dut.enablen)
        if random.random() < 0.1:
            dut.asyncReset.value = 1 - int(dut.asyncReset)
        if random.random() < 0.1:
            dut.asyncResetn.value = 1 - int(dut.asyncResetn)

class Ref:
    def __init__(self,dut):
        self.dut = dut

        self.test_clk_regWithoutReset = int(dut.test_clk_regWithoutReset)
        self.test_clkn_regWithoutReset = int(dut.test_clkn_regWithoutReset)
        self.test_clk_boot_regWithoutReset = int(dut.test_clk_boot_regWithoutReset)
        self.test_clk_boot_regWithReset    = 42
        self.test_async_reset_regWithoutReset = int(dut.test_async_reset_regWithoutReset)
        self.test_async_reset_regWithReset    = int(dut.test_async_reset_regWithReset)
        self.test_async_resetn_regWithoutReset = int(dut.test_async_resetn_regWithoutReset)
        self.test_async_resetn_regWithReset = int(dut.test_async_resetn_regWithReset)
        self.test_sync_reset_regWithoutReset = int(dut.test_sync_reset_regWithoutReset)
        self.test_sync_reset_regWithReset = int(dut.test_sync_reset_regWithReset)
        self.test_sync_resetn_regWithoutReset = int(dut.test_sync_resetn_regWithoutReset)
        self.test_sync_resetn_regWithReset = int(dut.test_sync_resetn_regWithReset)
        self.test_enable_regWithoutReset = int(dut.test_enable_regWithoutReset)
        self.test_enablen_regWithoutReset = int(dut.test_enablen_regWithoutReset)
        self.test_sync_reset_enable_regWithoutReset = int(dut.test_sync_reset_enable_regWithoutReset)
        self.test_sync_reset_enable_regWithReset = int(dut.test_sync_reset_enable_regWithReset)
        self.test_softReset_regWithoutReset = int(dut.test_softReset_regWithoutReset)
        self.test_softReset_regWithReset = int(dut.test_softReset_regWithReset)
        self.test_softResetn_regWithoutReset = int(dut.test_softResetn_regWithoutReset)
        self.test_softResetn_regWithReset = int(dut.test_softResetn_regWithReset)
        self.test_async_reset_softReset_regWithoutReset = int(dut.test_async_reset_softReset_regWithoutReset)
        self.test_async_reset_softReset_regWithReset = int(dut.test_async_reset_softReset_regWithReset)
        self.test_sync_reset_softReset_regWithoutReset = int(dut.test_sync_reset_softReset_regWithoutReset)
        self.test_sync_reset_softReset_regWithReset = int(dut.test_sync_reset_softReset_regWithReset)

        cocotb.start_soon(self.applyAsyncHighReset())
        cocotb.start_soon(self.applyAsyncLowReset())
        cocotb.start_soon(self.applyFallingEdge())
        cocotb.start_soon(self.applyRisingEdge())



    @cocotb.coroutine
    def applyAsyncHighReset(self):
        while True:
            yield RisingEdge(self.dut.asyncReset)
            yield Timer(100)
            self.test_async_reset_regWithReset = 42
            self.test_async_reset_softReset_regWithReset = 42

    @cocotb.coroutine
    def applyAsyncLowReset(self):
        while True:
            yield FallingEdge(self.dut.asyncResetn)
            yield Timer(100)
            self.test_async_resetn_regWithReset = 42


    @cocotb.coroutine
    def applyFallingEdge(self):
        while True:
            yield FallingEdge(self.dut.clkn)
            assertEquals(self.dut.test_clkn_regWithoutReset, self.test_clkn_regWithoutReset, "test_clkn_regWithoutReset")
            self.test_clkn_regWithoutReset = (self.test_clkn_regWithoutReset + 1) & 0xFF

    @cocotb.coroutine
    def applyRisingEdge(self):
        dut = self.dut
        while True:
            yield RisingEdge(dut.clk)
            assertEquals(dut.test_clk_regWithoutReset, self.test_clk_regWithoutReset, "test_clk_regWithoutReset")
            assertEquals(dut.test_clk_boot_regWithoutReset, self.test_clk_boot_regWithoutReset, "test_clk_boot_regWithoutReset")
            assertEquals(dut.test_clk_boot_regWithReset, self.test_clk_boot_regWithReset, "test_clk_boot_regWithReset")
            assertEquals(dut.test_async_reset_regWithoutReset, self.test_async_reset_regWithoutReset, "test_async_reset_regWithoutReset")
            assertEquals(dut.test_async_reset_regWithReset, self.test_async_reset_regWithReset, "test_async_reset_regWithReset")
            assertEquals(dut.test_async_resetn_regWithoutReset, self.test_async_resetn_regWithoutReset, "test_async_resetn_regWithoutReset")
            assertEquals(dut.test_async_resetn_regWithReset, self.test_async_resetn_regWithReset, "test_async_resetn_regWithReset")
            assertEquals(dut.test_sync_reset_regWithoutReset, self.test_sync_reset_regWithoutReset, "test_sync_reset_regWithoutReset")
            assertEquals(dut.test_sync_reset_regWithReset, self.test_sync_reset_regWithReset, "test_sync_reset_regWithReset")
            assertEquals(dut.test_sync_resetn_regWithoutReset, self.test_sync_resetn_regWithoutReset, "test_sync_resetn_regWithoutReset")
            assertEquals(dut.test_sync_resetn_regWithReset, self.test_sync_resetn_regWithReset, "test_sync_resetn_regWithReset")
            assertEquals(dut.test_enable_regWithoutReset, self.test_enable_regWithoutReset, "test_enable_regWithoutReset")
            assertEquals(dut.test_enablen_regWithoutReset, self.test_enablen_regWithoutReset, "test_enablen_regWithoutReset")
            assertEquals(dut.test_sync_reset_enable_regWithoutReset, self.test_sync_reset_enable_regWithoutReset, "test_sync_reset_enable_regWithoutReset")
            assertEquals(dut.test_sync_reset_enable_regWithReset, self.test_sync_reset_enable_regWithReset, "test_sync_reset_enable_regWithReset")
            assertEquals(dut.test_softReset_regWithoutReset, self.test_softReset_regWithoutReset, "test_softReset_regWithoutReset")
            assertEquals(dut.test_softReset_regWithReset, self.test_softReset_regWithReset, "test_softReset_regWithReset")
            assertEquals(dut.test_softResetn_regWithoutReset, self.test_softResetn_regWithoutReset, "test_softResetn_regWithoutReset")
            assertEquals(dut.test_softResetn_regWithReset, self.test_softResetn_regWithReset, "test_softResetn_regWithReset")
            assertEquals(dut.test_async_reset_softReset_regWithoutReset, self.test_async_reset_softReset_regWithoutReset, "test_async_reset_softReset_regWithoutReset")
            assertEquals(dut.test_async_reset_softReset_regWithReset, self.test_async_reset_softReset_regWithReset, "test_async_reset_softReset_regWithReset")
            assertEquals(dut.test_sync_reset_softReset_regWithoutReset, self.test_sync_reset_softReset_regWithoutReset, "test_sync_reset_softReset_regWithoutReset")
            assertEquals(dut.test_sync_reset_softReset_regWithReset, self.test_sync_reset_softReset_regWithReset, "test_sync_reset_softReset_regWithReset")



            self.test_clk_regWithoutReset = (self.test_clk_regWithoutReset + 1) & 0xFF
            self.test_clk_boot_regWithoutReset = (self.test_clk_boot_regWithoutReset + 1) & 0xFF
            self.test_clk_boot_regWithReset = (self.test_clk_boot_regWithReset + 1) & 0xFF
            self.test_async_reset_regWithoutReset = (self.test_async_reset_regWithoutReset + 1) & 0xFF
            if int(dut.asyncReset) == 0:
                self.test_async_reset_regWithReset = (self.test_async_reset_regWithReset + 1) & 0xFF
            self.test_async_resetn_regWithoutReset = (self.test_async_resetn_regWithoutReset + 1) & 0xFF
            if int(dut.asyncResetn) == 1:
                self.test_async_resetn_regWithReset = (self.test_async_resetn_regWithReset + 1) & 0xFF
            self.test_sync_reset_regWithoutReset = (self.test_sync_reset_regWithoutReset + 1) & 0xFF
            self.test_sync_resetn_regWithoutReset = (self.test_sync_resetn_regWithoutReset + 1) & 0xFF
            if int(dut.syncReset) == 1:
                self.test_sync_reset_regWithReset = 42
            else:
                self.test_sync_reset_regWithReset = (self.test_sync_reset_regWithReset + 1) & 0xFF

            if int(dut.syncResetn) == 0:
                self.test_sync_resetn_regWithReset = 42
            else:
                self.test_sync_resetn_regWithReset = (self.test_sync_resetn_regWithReset + 1) & 0xFF

            if int(dut.enable) == 1:
                self.test_enable_regWithoutReset = (self.test_enable_regWithoutReset + 1) & 0xFF
            if int(dut.enablen) == 0:
                self.test_enablen_regWithoutReset = (self.test_enablen_regWithoutReset + 1) & 0xFF

            if int(dut.enable) == 1:
                self.test_sync_reset_enable_regWithoutReset = (self.test_sync_reset_enable_regWithoutReset + 1) & 0xFF

            if int(dut.enable) == 1:
                if int(dut.syncReset) == 1:
                    self.test_sync_reset_enable_regWithReset = 42
                else:
                    self.test_sync_reset_enable_regWithReset = (self.test_sync_reset_enable_regWithReset + 1) & 0xFF

            self.test_softReset_regWithoutReset = (self.test_softReset_regWithoutReset + 1) & 0xFF
            if int(dut.softReset) == 1:
                self.test_softReset_regWithReset = 42
            else:
                self.test_softReset_regWithReset = (self.test_softReset_regWithReset + 1) & 0xFF


            self.test_softResetn_regWithoutReset = (self.test_softResetn_regWithoutReset + 1) & 0xFF
            if int(dut.softResetn) == 0:
                self.test_softResetn_regWithReset = 42
            else:
                self.test_softResetn_regWithReset = (self.test_softResetn_regWithReset + 1) & 0xFF


            self.test_async_reset_softReset_regWithoutReset = (self.test_async_reset_softReset_regWithoutReset + 1) & 0xFF
            if int(dut.asyncReset) == 0:
                if int(dut.softReset) == 1:
                    self.test_async_reset_softReset_regWithReset = 42
                else:
                    self.test_async_reset_softReset_regWithReset = (self.test_async_reset_softReset_regWithReset + 1) & 0xFF

            self.test_sync_reset_softReset_regWithoutReset = (self.test_sync_reset_softReset_regWithoutReset + 1) & 0xFF
            if int(dut.softReset) == 1 or int(dut.syncReset) == 1:
                self.test_sync_reset_softReset_regWithReset = 42
            else:
                self.test_sync_reset_softReset_regWithReset = (self.test_sync_reset_softReset_regWithReset + 1) & 0xFF

@cocotb.test()
def test1(dut):
    set_timeout()
    dut._log.info("Cocotb test boot")
    #random.seed(0)

    cocotb.start_soon(ClockDomainGen(dut))
    yield Timer(100)
    ref = Ref(dut)

    yield Timer(1000*2000)
    dut._log.info("Cocotb test done")
