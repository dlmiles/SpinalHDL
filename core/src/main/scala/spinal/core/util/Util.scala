package spinal.core.util

import java.io.File
import java.util.regex.Pattern
import scala.reflect.io.Path

object Util {

  val PATTERN_extract_readmem_path = Pattern.compile(".*\\$\\breadmem[bh]\\b\\(\"(.+)\".*\\).*")

  /**
    *
    * @param input The verbatim Verilog line to rewrite the path of a $readmem[bh] directive.
    * @param workingDirectory null will convert input to basename (directory components removed)
    *   Path("") will ensure an absolute path to returned
    *   Path(".") will make path relative as-if we are in the correct directory
    *   Path(anyOtherValue) will emit a relative path to target file base on this working directory.
    * @param backslashAlways default to true on windows and false on linux, or can be overriden.
    * @return The Verilog line transformed and rewritten.
    */
  def fixupVerilogDollarReadmemPath(input: String, workingDirectory: Path = null, backslashAlways: Boolean = autoBackslashAlways()): String = {
    val extracted = extractVerilogDollarReadmemPath(input)
    if(workingDirectory != null && !workingDirectory.path.equals(".")) {
      val pathCanonical = Path(extracted).toCanonical
      var newPath: String = null
      if(workingDirectory.path.equals("")) {
        newPath = pathCanonical.path
      } else {
        println(s"fixupVerilogDollarReadmemPath(input=$input, workingDirectory=$workingDirectory, backslashAlways=$backslashAlways)")
        println(s"fixupVerilogDollarReadmemPath()  pathCanonical=$pathCanonical")
        println(s"fixupVerilogDollarReadmemPath()  workingDirectory=$workingDirectory")
        println(s"fixupVerilogDollarReadmemPath()  toCanonical=${workingDirectory.toCanonical}")
        println(s"fixupVerilogDollarReadmemPath()  relativize=${workingDirectory.toCanonical.relativize(pathCanonical)}")
        newPath = workingDirectory.toCanonical.relativize(pathCanonical).path
        println(s"fixupVerilogDollarReadmemPath()  newPath=$newPath")
      }
      val relPathEscaped = backslashEscapeForString(newPath)
      input.replace(extracted, relPathEscaped)
    } else {
      val basename = Util.filepathBasename(extracted, backslashAlways)
      // no separator to escape
      input.replace(extracted, basename)
    }
  }

  def extractVerilogDollarReadmemPath(str: String): String = {
    assert(str.contains('$' + "readmem")) // Check the $readmem was not string formatted out of input data
    val matcher = PATTERN_extract_readmem_path.matcher(str)
    assert(matcher.matches())
    assert(matcher.groupCount() == 1)
    val res = matcher.group(1)
    res
  }

  def filepathBasename(input: String, backslashAlways: Boolean = autoBackslashAlways()): String = {
    var bn = input

    if (backslashAlways) { // technically on Linux this is part of the filename
      val i = bn.lastIndexOf('\\')
      if (i >= 0)
        bn = bn.substring(i + 1)
    }

    val i = bn.lastIndexOf('/')
    if (i >= 0)
      bn = bn.substring(i + 1)

    bn
  }

  def autoBackslashAlways(): Boolean = {
    // isWindows()
    File.separator.equals("\\")
  }

  def backslashEscapeForString(s: String): String = {
    s.replace("\\", "\\\\")
  }

}
