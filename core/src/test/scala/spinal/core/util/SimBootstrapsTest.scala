package spinal.core.util

import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import java.util.regex.Pattern
import scala.collection.mutable
import scala.reflect.io.Path

class SimBootstrapsTest extends AnyFunSuite {

  test("verilog_$readmem_path_replace_regex") {
    val map = mutable.LinkedHashMap(

        "   $readmemb(\"/work/path/dir/MyTestFile.v_toplevel_ram_foobar0.bin\",ram_symbol0);  " ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar0.bin\",ram_symbol0);  ",

        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar1.bin\",ram_symbol1);  " ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar1.bin\",ram_symbol1);  ",

        "   $readmemb(\"/work/path/dir/MyTestFile.v_toplevel_ram_foobar2.bin\");  //end" ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar2.bin\");  //end",

        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar3.bin\");  " ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar3.bin\");  ",

        // mixed
        "   $readmemb(\"/work\\path/dir\\MyTestFile.v_toplevel_ram_foobar4.bin\");  " ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar4.bin\");  ",

        "   $readmemb(\"\\MyTestFile.v_toplevel_ram_foobar5.bin\", ram_symbol5);  " ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar5.bin\", ram_symbol5);  ",

        // windows
        "$readmemb(\"C:\\work\\path\\dir\\MyTestFile.v_toplevel_ram_foobar6.bin\", ram_symbol6);  " ->
        "$readmemb(\"MyTestFile.v_toplevel_ram_foobar6.bin\", ram_symbol6);  ",

        // windows MSYS2
        "   $readmemb(\"/C:/work/path/dir/MyTestFile.v_toplevel_ram_foobar7.bin\", ram_symbol7);" ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar7.bin\", ram_symbol7);"

    )


    // NOTE this PATTERN exists at spinal.core.util.Util.PATTERN_extract_readmem_path
    //val exprPattern = """.*\$readmem.*\(\"(.+)\".+\).*""".r.pattern   // original
    val exprPattern = """.*\$\breadmem[bh]\b\(\"(.+)\".*\).*""".r.pattern
    //val PATTERN = exprPattern
    val PATTERN = Pattern.compile(".*\\$\\breadmem[bh]\\b\\(\"(.+)\".*\\).*")

    for((input,expected) <- map) {
      //println(s"INPUT: >>${input}<<")
      val extracted = testExtractVerilogDollarReadmemPath(PATTERN, input)
      val basename = Util.filepathBasename(extracted, backslashAlways = true)
      val replaced = input.replace(extracted, basename)
      //println(s"EXPCT: >>${expected}<<")
      assert(replaced == expected)

      // Now try with the real version: PATTERN_extract_readmem_path

      assert(Util.fixupVerilogDollarReadmemPath(input, backslashAlways = true) == expected)

      val canon = Path(extracted).toCanonical.path    // expect input but with canonical paths
      val inputAfterCanon = input.replace(extracted, Util.backslashEscapeForString(canon))
      assert(Util.fixupVerilogDollarReadmemPath(input, Path(""), backslashAlways = true) == inputAfterCanon)

      assert(Util.fixupVerilogDollarReadmemPath(input, Path("."), backslashAlways = true) == expected)
    }
  }

  test("verilog_$readmem_path_replace_regex_abs") {
    val map = mutable.LinkedHashMap(

      "   $readmemb(\"/work/path/dir/MyTestFile.v_toplevel_ram_foobar0.bin\",ram_symbol0);  " ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar0.bin\",ram_symbol0);  ",

      "   $readmemb(\"/work/path/dir/MyTestFile.v_toplevel_ram_foobar2.bin\");  //end" ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar2.bin\");  //end",

      // mixed
      "   $readmemb(\"/work\\path/dir\\MyTestFile.v_toplevel_ram_foobar4.bin\");  " ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar4.bin\");  ",

      // windows
      "$readmemb(\"C:\\work\\path\\dir\\MyTestFile.v_toplevel_ram_foobar6.bin\", ram_symbol6);  " ->
        "$readmemb(\"MyTestFile.v_toplevel_ram_foobar6.bin\", ram_symbol6);  ",

      // windows MSYS2
      "   $readmemb(\"/C:/work/path/dir/MyTestFile.v_toplevel_ram_foobar7.bin\", ram_symbol7);" ->
        "   $readmemb(\"MyTestFile.v_toplevel_ram_foobar7.bin\", ram_symbol7);"

    )

    for ((input, expected) <- map) {
      //println(s"INPUT: >>${input}<<")
      val extracted = testExtractVerilogDollarReadmemPath(Util.PATTERN_extract_readmem_path, input)
      val basename = Util.filepathBasename(extracted, backslashAlways = true)
      val replaced = input.replace(extracted, basename)
      //println(s"EXPCT: >>${expected}<<")
      assert(replaced == expected)

      val dirname = extracted.replace(basename, "")

      // Now try with the real version: PATTERN_extract_readmem_path

      val expectedPrefix = Util.backslashEscapeForString(".." + File.separator + "dir" + File.separator)
      assert(Util.fixupVerilogDollarReadmemPath(input, Path("/work/path/rtl"), backslashAlways = true) == expected.replace("(\"", "(\"" + expectedPrefix))

      assert(Util.fixupVerilogDollarReadmemPath(input, Path(dirname), backslashAlways = true) == expected)
    }

  }


  test("test_validate_working_example") {

    val workingWorkspace = Path("/work/spinalhdl/simWorkspace/SpinalSimWishboneArbiterTester/MemoryArbitration")
    val path = workingWorkspace.resolve("simulatorName")
    val currentWorkingDirectoryPath = path.toAbsolute.toFile
    val line = "  $readmemh(\"/work/spinalhdl/tmp/job_529/WishBoneRom.v_toplevel_ram_symbol0.bin\",ram_symbol0);   // end"
    val newline = Util.fixupVerilogDollarReadmemPath(line, currentWorkingDirectoryPath)

    assert(newline.contains("\"..\\\\..\\\\..\\\\..\\\\tmp\\\\job_529\\\\WishBoneRom.v_toplevel_ram_symbol0.bin\""))

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
