#
#  This project is used by:
#   source ./tool.sh
#   purge_cocotb
#
# So the VPI interface is built ready tp run parallel unit testing
#
import cocotb
from cocotb.triggers import Timer


@cocotb.test()
def test1(dut):
    dut._log.info("Cocotb test boot")
    yield Timer(1000)
    dut._log.info("Cocotb test done")
