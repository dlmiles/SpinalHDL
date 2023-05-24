package spinal.tester.simconfigtests

import org.scalatest.funsuite.AnyFunSuite
import spinal.tester.scalatest.SpinalTesterSimConfig

class SpinalTesterSimConfigTests extends AnyFunSuite {

  test("sanitizeStringForFilename") {
    val map = collection.immutable.ListMap(
      // through
      "" -> "",
      "abc" -> "abc",
      "rst" -> "rst",
      "0123456789" -> "0123456789",
      "A-B_C$D%E^F&G*H+I=J!K\"L\'M" -> "A-B_C$D%E^F&G*H+I=J!K\"L\'M",
      // underscore
      "a\tb\fc\rd\ne fg" -> "a_b_c_d_e_fg",  // \u001d is not in regex \s
      // removal
      "h(i)j[k]l{m}n,o" -> "hijklmno",
      // minus
      "w.x\\y/z" -> "w-x-y-z"
    )

    for((input,expect) <- map) {
      assert(SpinalTesterSimConfig.sanitizeStringForFilename(input) == expect) // ("sanitizeStringForFilename(\"" + input + "\")")
    }
  }

  test("computeHash") {
    val map = collection.immutable.ListMap(
      "" -> "d41d8cd98f00b204e9800998ecf8427e",
      "hello world" -> "5eb63bbbe01eeed093cb22bb8f5acdc3"
    )

    for((input,expect) <- map) {
      assert(SpinalTesterSimConfig.computeHash(input) == expect)
    }
  }

  test("toHex") {
    val map = collection.immutable.ListMap(
      Array[Byte]() -> "",
      Array[Byte](0.toByte) -> "00",
      Array[Byte](1.toByte) -> "01",
      "abcXYZ".getBytes -> "61626358595a",
      "0123456789".getBytes -> "30313233343536373839",
      Array[Byte](0.toByte, 1.toByte, 2.toByte, 126.toByte, 127.toByte, 128.toByte, 254.toByte, 255.toByte) -> "0001027e7f80feff"
    )

    for ((input, expect) <- map) {
      assert(SpinalTesterSimConfig.toHex(input) == expect)
    }
  }

}
