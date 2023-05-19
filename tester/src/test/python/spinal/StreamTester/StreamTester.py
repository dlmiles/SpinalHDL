import random
from queue import Queue

import cocotb
from cocotb.result import TestFailure
from cocotb.triggers import RisingEdge, FallingEdge

from cocotblib.misc import set_timeout, randSignal, assertEquals, ClockDomainAsyncReset, BoolRandomizer, StreamRandomizer,StreamReader, FlowRandomizer
from functools import reduce


class FifoPacket:
    def __init__(self,a,b):
        self.a = a
        self.b = b

class Fifo:
    def __init__(self,dut):
        self.queue = Queue()
        self.dut = dut

    @cocotb.coroutine
    def run(self):
        cocotb.start_soon(self.push())
        yield self.pop()

    @cocotb.coroutine
    def push(self):
        dut = self.dut
        queue = self.queue
        validRandomizer = BoolRandomizer()
        dut.io_slave0_valid.value = 0
        while True:
            yield RisingEdge(dut.clk)
            if int(dut.io_slave0_valid) == 1 and int(dut.io_slave0_ready) == 1:
                queue.put(FifoPacket(int(dut.io_slave0_payload_a), int(dut.io_slave0_payload_b)))
            dut.io_slave0_valid.value = validRandomizer.get()
            randSignal(dut.io_slave0_payload_a)
            randSignal(dut.io_slave0_payload_b)

    @cocotb.coroutine
    def pop(self):
        dut = self.dut
        queue = self.queue
        readyRandomizer = BoolRandomizer()
        dut.io_master0_ready.value = 0
        for i in range(0,1000):
            while True:
                yield RisingEdge(dut.clk)
                dut.io_master0_ready.value = readyRandomizer.get()
                if int(dut.io_master0_valid) == 1 and int(dut.io_master0_ready) == 1:
                    break
            pop = queue.get()
            assertEquals(pop.a, dut.io_master0_payload_a,"io_master0_payload_a")
            assertEquals(pop.b, dut.io_master0_payload_b, "io_master0_payload_b")



class Fork:
    def __init__(self,dut):
        self.queues = [Queue() for i in range(0,3)]
        self.counters = [0 for i in range (0,3)]
        self.dut = dut

    def onInput(self,payload,handle):
        for queue in self.queues:
            queue.put(payload)

    def onOutput(self,payload,portId):
        assertEquals(payload,self.queues[portId].get(),"fork error")
        self.counters[portId] += 1

    @cocotb.coroutine
    def run(self):
        cocotb.start_soon(StreamRandomizer("forkInput", self.onInput,None, self.dut, self.dut.clk))
        for idx in range(0,3):
            cocotb.start_soon(StreamReader("forkOutputs_" + str(idx), self.onOutput, idx, self.dut, self.dut.clk))

        while not reduce(lambda x,y: x and y, [x > 1000 for x in self.counters]):
            yield RisingEdge(self.dut.clk)



class DispatcherInOrder:
    def __init__(self,dut):
        self.queue = Queue()
        self.counter = 0
        self.nextPort = 0
        self.dut = dut

    def onInput(self,payload,handle):
        self.queue.put(payload)

    def onOutput(self,payload,portId):
        assertEquals(payload,self.queue.get(),"DispatcherInOrder payload error")
        assertEquals(portId,self.nextPort,"DispatcherInOrder order error")
        self.nextPort = (self.nextPort + 1) % 3
        self.counter += 1

    @cocotb.coroutine
    def run(self):
        cocotb.start_soon(StreamRandomizer("dispatcherInOrderInput", self.onInput,None, self.dut, self.dut.clk))
        for idx in range(0,3):
            cocotb.start_soon(StreamReader("dispatcherInOrderOutputs_" + str(idx), self.onOutput, idx, self.dut, self.dut.clk))

        while self.counter < 1000:
            yield RisingEdge(self.dut.clk)

class StreamFlowArbiter:
    def __init__(self,dut):
        self.inputStreamCounter = 0
        self.inputFlowCounter = 0
        self.dut = dut

    def onInputStream(self,payload,handle):
        pass

    def onInputFlow(self,payload,handle):
        pass


    @cocotb.coroutine
    def run(self):
        dut = self.dut
        cocotb.start_soon(StreamRandomizer("streamFlowArbiterInputStream", self.onInputStream, None, dut, dut.clk))
        cocotb.start_soon(FlowRandomizer("streamFlowArbiterInputFlow", self.onInputFlow, None, dut, dut.clk))

        while not (self.inputFlowCounter > 1000 and self.inputStreamCounter > 1000):
            yield RisingEdge(dut.clk)
            if int(dut.streamFlowArbiterOutput_valid) == 1:
                if int(dut.streamFlowArbiterInputFlow_valid) == 1:
                    assertEquals(dut.streamFlowArbiterOutput_payload,dut.streamFlowArbiterInputFlow_payload,"StreamFlowArbiter payload error")
                    assertEquals(0, dut.streamFlowArbiterInputStream_ready, "StreamFlowArbiter arbitration error")
                    self.inputFlowCounter += 1
                else:
                    assertEquals(dut.streamFlowArbiterOutput_payload,dut.streamFlowArbiterInputStream_payload,"StreamFlowArbiter payload error")
                    assertEquals(0, dut.streamFlowArbiterInputFlow_valid, "StreamFlowArbiter arbitration error")
                    self.inputStreamCounter += 1




class ArbiterInOrder:
    def __init__(self,dut):
        self.queues = [Queue() for i in range(0,3)]
        self.counter = 0
        self.nextPort = 0
        self.dut = dut

    def onInput(self,payload,portId):
        self.queues[portId].put(payload)

    def onOutput(self,payload,portId):
        assertEquals(payload,self.queues[self.nextPort].get(),"ArbiterInOrder payload error")
        self.nextPort = (self.nextPort + 1) % 3
        self.counter += 1

    @cocotb.coroutine
    def run(self):
        for idx in range(0,3):
            cocotb.start_soon(StreamRandomizer("arbiterInOrderInputs_" + str(idx), self.onInput ,idx, self.dut, self.dut.clk))

        cocotb.start_soon(StreamReader("arbiterInOrderOutput", self.onOutput, idx, self.dut, self.dut.clk))

        while self.counter < 1000:
            yield RisingEdge(self.dut.clk)

class ArbiterLowIdPortFirst:
    def __init__(self,dut):
        self.queues = [Queue() for i in range(0,3)]
        self.nextPort = -1
        self.counter = 0
        self.dut = dut

    def onInput(self,payload,portId):
        self.queues[portId].put(payload)

    def onOutput(self,payload,dummy):
        assertEquals(payload,self.queues[self.nextPort].get(),"ArbiterLowIdPortFirst payload error")
        self.counter += 1
        self.nextPort = -1

    @cocotb.coroutine
    def arbitration(self):
        while True:
            yield FallingEdge(self.dut.clk)
            if self.nextPort == -1:
                if int(self.dut.arbiterLowIdPortFirstInputs_0_valid) == 1:
                    self.nextPort = 0
                elif int(self.dut.arbiterLowIdPortFirstInputs_1_valid) == 1:
                    self.nextPort = 1
                elif int(self.dut.arbiterLowIdPortFirstInputs_2_valid) == 1:
                    self.nextPort = 2

    @cocotb.coroutine
    def run(self):
        dut = self.dut
        for idx in range(0,3):
            cocotb.start_soon(StreamRandomizer("arbiterLowIdPortFirstInputs_" + str(idx), self.onInput ,idx, self.dut, self.dut.clk))
        cocotb.start_soon(StreamReader("arbiterLowIdPortFirstOutput", self.onOutput, idx, self.dut, self.dut.clk))
        cocotb.start_soon(self.arbitration())

        while self.counter < 1000:
            yield RisingEdge(dut.clk)

class ArbiterRoundRobin:
    def __init__(self,dut):
        self.queues = [Queue() for i in range(0,3)]
        self.nextPort = -1
        self.previousPort = 2
        self.counter = 0
        self.dut = dut

    def onInput(self,payload,portId):
        self.queues[portId].put(payload)

    def onOutput(self,payload,dummy):
        if self.queues[self.nextPort].empty():
            raise TestFailure("ArbiterRoundRobin Empty queue")
        assertEquals(payload,self.queues[self.nextPort].get(),"ArbiterRoundRobin payload error")
        self.counter += 1
        self.previousPort = self.nextPort
        self.nextPort = -1


    @cocotb.coroutine
    def arbitration(self):
        while True:
            yield FallingEdge(self.dut.clk)
            if self.nextPort == -1:
                if self.previousPort < 1 and int(self.dut.arbiterRoundRobinInputs_1_valid) == 1:
                    self.nextPort = 1
                elif self.previousPort < 2 and int(self.dut.arbiterRoundRobinInputs_2_valid) == 1:
                    self.nextPort = 2
                elif int(self.dut.arbiterRoundRobinInputs_0_valid) == 1:
                    self.nextPort = 0
                elif int(self.dut.arbiterRoundRobinInputs_1_valid) == 1:
                    self.nextPort = 1
                elif int(self.dut.arbiterRoundRobinInputs_2_valid) == 1:
                    self.nextPort = 2

    @cocotb.coroutine
    def run(self):
        dut = self.dut
        for idx in range(0,3):
            cocotb.start_soon(StreamRandomizer("arbiterRoundRobinInputs_" + str(idx), self.onInput ,idx, self.dut, self.dut.clk))
        cocotb.start_soon(StreamReader("arbiterRoundRobinOutput", self.onOutput, idx, self.dut, self.dut.clk))
        cocotb.start_soon(self.arbitration())

        while self.counter < 1000:
            yield RisingEdge(dut.clk)


class ArbiterLowIdPortNoLockFirst:
    def __init__(self,dut):
        self.queues = [Queue() for i in range(0,3)]
        self.nextPort = -1
        self.counter = 0
        self.dut = dut

    def onInput(self,payload,portId):
        self.queues[portId].put(payload)

    def onOutput(self,payload,portId):
        assertEquals(payload,self.queues[self.nextPort].get(),"ArbiterLowIdPortNoLockFirst payload error")
        self.counter += 1
        self.nextPort = -1


    @cocotb.coroutine
    def arbitration(self):
        while True:
            yield FallingEdge(self.dut.clk)
            if int(self.dut.arbiterLowIdPortFirstNoLockInputs_0_valid) == 1:
                self.nextPort = 0
            elif int(self.dut.arbiterLowIdPortFirstNoLockInputs_1_valid) == 1:
                self.nextPort = 1
            elif int(self.dut.arbiterLowIdPortFirstNoLockInputs_2_valid) == 1:
                self.nextPort = 2
            else:
                self.nextPort = -1

    @cocotb.coroutine
    def run(self):
        dut = self.dut
        for idx in range(0,3):
            cocotb.start_soon(StreamRandomizer("arbiterLowIdPortFirstNoLockInputs_" + str(idx), self.onInput ,idx, self.dut, self.dut.clk))
        cocotb.start_soon(StreamReader("arbiterLowIdPortFirstNoLockOutput", self.onOutput, idx, self.dut, self.dut.clk))
        cocotb.start_soon(self.arbitration())

        while self.counter < 1000:
            yield RisingEdge(dut.clk)


class ArbiterLowIdPortFragmentLockFirst:
    def __init__(self,dut):
        self.queues = [Queue() for i in range(0,3)]
        self.nextPort = -1
        self.counter = 0
        self.dut = dut

    def onInput(self,payload,portId):
        self.queues[portId].put(payload.fragment)

    def onOutput(self,payload,dummy):
        if self.queues[self.nextPort].empty():
            raise TestFailure("ArbiterLowIdPortFragmentLockFirst Empty queue")
        assertEquals(payload.fragment,self.queues[self.nextPort].get(),"ArbiterLowIdPortFragmentLockFirst payload error")
        self.counter += 1
        if payload.last == 1:
            self.nextPort = -1


    @cocotb.coroutine
    def arbitration(self):
        while True:
            yield FallingEdge(self.dut.clk)
            if self.nextPort == -1:
                if int(self.dut.arbiterLowIdPortFirstFragmentLockInputs_0_valid) == 1:
                    self.nextPort = 0
                elif int(self.dut.arbiterLowIdPortFirstFragmentLockInputs_1_valid) == 1:
                    self.nextPort = 1
                elif int(self.dut.arbiterLowIdPortFirstFragmentLockInputs_2_valid) == 1:
                    self.nextPort = 2

    @cocotb.coroutine
    def run(self):
        dut = self.dut
        for idx in range(0,3):
            cocotb.start_soon(StreamRandomizer("arbiterLowIdPortFirstFragmentLockInputs_" + str(idx), self.onInput ,idx, self.dut, self.dut.clk))
        cocotb.start_soon(StreamReader("arbiterLowIdPortFirstFragmentLockOutput", self.onOutput, idx, self.dut, self.dut.clk))
        cocotb.start_soon(self.arbitration())

        while self.counter < 1000:
            yield RisingEdge(dut.clk)

@cocotb.test()
def test1(dut):
    set_timeout()
    dut._log.info("Cocotb test boot")
    random.seed(0)
    from cocotblib.misc import cocotbXHack
    cocotbXHack()

    cocotb.start_soon(ClockDomainAsyncReset(dut.clk, dut.reset))

    threads = []
    threads.append(cocotb.start_soon(Fifo(dut).run()))
    threads.append(cocotb.start_soon(Fork(dut).run()))
    threads.append(cocotb.start_soon(DispatcherInOrder(dut).run()))
    threads.append(cocotb.start_soon(StreamFlowArbiter(dut).run()))
    threads.append(cocotb.start_soon(ArbiterInOrder(dut).run()))
    threads.append(cocotb.start_soon(ArbiterLowIdPortFirst(dut).run()))
    threads.append(cocotb.start_soon(ArbiterRoundRobin(dut).run()))
    threads.append(cocotb.start_soon(ArbiterLowIdPortNoLockFirst(dut).run()))
    threads.append(cocotb.start_soon(ArbiterLowIdPortFragmentLockFirst(dut).run()))

    for thread in threads:
        yield thread.join()

    #
    #
    # yield fork


    dut._log.info("Cocotb test done")
