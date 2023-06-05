package spinal.core.util

import java.io.File
import java.util.regex.Pattern

object Util {

  val PATTERN_extract_readmem_path = Pattern.compile(".*\\$\\breadmem.*\\b\\(\"(.+)\".*\\).*")

  def fixupVerilogDollarReadmemPath(input: String, backslashAlways: Boolean = autoBackslashAlways()): String = {
    val extracted = extractVerilogDollarReadmemPath(input)
    val basename = Util.filepathBasename(extracted, backslashAlways)
    input.replace(extracted, basename)
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

}
