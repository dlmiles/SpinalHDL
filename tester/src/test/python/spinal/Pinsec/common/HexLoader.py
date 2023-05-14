import cocotb
from cocotb.triggers import Timer


@cocotb.coroutine
def loadIHexCallback(address,array,dut,clk):
    uut = dut.uut
    assert(address & 3 == 0)
    assert(len(array) & 3 == 0)
    if address < 0x40000000:
        data = 0
        for b in array:
            data |= b << ((address & 3) * 8)
            if (address & 3 == 3):
                yield Timer(5)
                uut.axi_ram.ram_port0_write.value = 1
                uut.axi_ram.ram_port0_enable.value = 1
                uut.axi_ram.ram_port0_mask.value = 0xF
                uut.axi_ram.ram_port0_address.value = (address) >> 2
                uut.axi_ram.ram_port0_writeData.value = data
                data = 0
                clk.value = 0
                yield Timer(5)
                clk.value = 1
                yield Timer(5)
                uut.axi_ram.ram_port0_enable.value = 0
                yield Timer(5)
            address += 1
    else:
        data = 0
        for b in array:
            data |= b << ((address & 1) * 8)
            if (address & 1 == 1):
                # yield Timer(5)
                # print("%x" % address)
                dut.loader_valid.value = 0
                dut.loader_data.value = data
                dut.loader_bank.value = ((address >> (1+10)) & 0x3)
                dut.loader_address.value = ((address >> 1) & 0x3FF) + (((address >> (1+10+2)) & 0x1FFF) << 10)
                data = 0
                yield Timer(5)
                dut.loader_valid.value = 1
                yield Timer(5)
            address += 1


@cocotb.coroutine
def loadIHex(dut,hexPath,clk,reset):

    reset.value = 1
    clk.value = 0
    yield Timer(5)
    clk.value = 1
    yield Timer(5)
    clk.value = 0
    yield Timer(5)
    clk.value = 1
    yield Timer(5)
    writeBuffer     = int(dut.uut.axi_ram.ram_port0_write)
    enableBuffer    = int(dut.uut.axi_ram.ram_port0_enable)
    maskBuffer      = int(dut.uut.axi_ram.ram_port0_mask)
    addressBuffer   = 0x00 #int(dut.uut.axi_ram.ram_port0_address)
    writeDataBuffer = int(dut.uut.axi_ram.ram_port0_writeData)


    # readIHex(hexPath,loadIHexCallback,dut)
    with open(hexPath) as f:
        offset = 0
        for line in f:
            if len(line) > 0:
                assert line[0] == ':'
                byteCount = int(line[1:3], 16)
                nextAddr = int(line[3:7], 16) + offset
                key = int(line[7:9], 16)
                if key == 0:
                    array = [int(line[9 + i * 2:11 + i * 2], 16) for i in range(0, byteCount)]
                    yield loadIHexCallback(nextAddr,array,dut,clk)
                elif key == 2:
                    offset = int(line[9:13], 16) << 4
                elif key == 4:
                    offset = int(line[9:13], 16) << 16
                else:
                    pass
    reset.value = 0
    yield Timer(5)
    reset.value = 1

    dut.uut.axi_ram.ram_port0_write.value = writeBuffer
    dut.uut.axi_ram.ram_port0_enable.value = enableBuffer
    dut.uut.axi_ram.ram_port0_mask.value = maskBuffer
    dut.uut.axi_ram.ram_port0_address.value = addressBuffer
    dut.uut.axi_ram.ram_port0_writeData.value = writeDataBuffer
