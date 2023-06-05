package spinal.core.sim

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.util.Util

import java.util.regex.Pattern

class SimBootstraps extends AnyFunSuite {

  test("verilog_$readmem_path_replace_regex") {
    val map = Map(

        "   $readmemb(\"/work/path/dir/MyTestFile.v_toplevel_ram_foobar0.bin\",ram_symbol0);  " ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar0.bin\",ram_symbol0);  ",

        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar1.bin\",ram_symbol1);  " ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar1.bin\",ram_symbol1);  ",

        "   $readmemb(\"/work/path/dir/MyTestFile.v_toplevel_ram_foobar2.bin\");  //end" ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar2.bin\");  //end",

        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar3.bin\");  " ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar3.bin\");  ",

        "   $readmemb(\"/work\\path/dir\\MyTestFile.v_toplevel_ram_foobar4.bin\");  " ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar4.bin\");  ",

        "   $readmemb(\"\\MyTestFile.v_toplevel_ram_foobar5.bin\", ram_symbol5);  " ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar5.bin\", ram_symbol5);  ",

        "$readmemb(\"C:\\dir\\MyTestFile.v_toplevel_ram_foobar6.bin\", ram_symbol6);  " ->
        "$readmemb(\"MyTestFile.v_toplevel_ram_foobar6.bin\", ram_symbol6);  "
    )


    // NOTE this PATTERN exists at spinal.core.util.Util.PATTERN_extract_readmem_path
    //val exprPattern = """.*\$readmem.*\(\"(.+)\".+\).*""".r.pattern   // original
    val exprPattern = """.*\$\breadmem.*\b\(\"(.+)\".*\).*""".r.pattern
    //val PATTERN = exprPattern
    val PATTERN = Pattern.compile(".*\\$\\breadmem.*\\b\\(\"(.+)\".*\\).*")

    for((input,expected) <- map) {
      //println(s"INPUT: >>${input}<<")
      val extracted = testExtractVerilogDollarReadmemPath(PATTERN, input)
      val basename = Util.filepathBasename(extracted, true)
      val replaced = input.replace(extracted, basename)
      //println(s"EXPCT: >>${expected}<<")
      assert(expected == replaced)

      // Now try with the real version: PATTERN_extract_readmem_path
      assert(expected == Util.fixupVerilogDollarReadmemPath(input))
    }
  }

  def testExtractVerilogDollarReadmemPath(pattern: Pattern, str: String): String = {
    assert(str.contains('$' + "readmem")) // Check the $readmem was not string formatted out of input data
    val matcher = pattern.matcher(str)
    assert(matcher.matches())
    assert(matcher.groupCount() == 1)
    val res = matcher.group(1)
    res
  }

}
