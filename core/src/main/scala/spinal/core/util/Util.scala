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
  def fixupVerilogDollarReadmemPath(input: String, workingDirectory: Path = null, targetDirectory: Path = null, backslashAlways: Boolean = autoBackslashAlways()): String = {
    val extracted = extractVerilogDollarReadmemPath(input)
    if(workingDirectory != null && !workingDirectory.path.equals(".")) {
      val pathCanonical = Path(extracted).toCanonical
      var newPath: String = null
      if(workingDirectory.path.equals("")) {
        // toCanonical will make a relative path absolute based around the JVM CWD not the one supplied as argument here
        // but on Windows it will no relativize a path that starts "C:\" and another that starts "/" which is a real headache
        //  for cross-platform unit testing
        newPath = Path(extracted).toCanonical.path
      } else {
        println(s"fixupVerilogDollarReadmemPath(input='${input}', workingDirectory=$workingDirectory, targetDirectory=$targetDirectory, backslashAlways=$backslashAlways)")
        println(s"fixupVerilogDollarReadmemPath()  pathCanonical=$pathCanonical")
        println(s"fixupVerilogDollarReadmemPath()  workingDirectory=$workingDirectory")
        println(s"fixupVerilogDollarReadmemPath()  toCanonical=${workingDirectory.toCanonical}")
        try {
          println(s"fixupVerilogDollarReadmemPath()  relativize=${workingDirectory.toCanonical.relativize(extracted)}")
        } catch {
          case e: Throwable => println(s"fixupVerilogDollarReadmemPath()  relativize=${e.getClass.getName}: ${e.getMessage}")
        }
        if(targetDirectory != null) {
          try {
            println(s"fixupVerilogDollarReadmemPath()  relativize1=${workingDirectory.toCanonical.relativize(targetDirectory)}")
          } catch {
            case e: Throwable => println(s"fixupVerilogDollarReadmemPath()  relativize1=${e.getClass.getName}: ${e.getMessage}")
          }
          try {
            println(s"fixupVerilogDollarReadmemPath()  relativize2=${targetDirectory.toCanonical.relativize(extracted)}")
          } catch {
            case e: Throwable => println(s"fixupVerilogDollarReadmemPath()  relativize1=${e.getClass.getName}: ${e.getMessage}")
          }
          try {
            println(s"fixupVerilogDollarReadmemPath()  relativize3=${targetDirectory.toCanonical.relativize(workingDirectory)}")
          } catch {
            case e: Throwable => println(s"fixupVerilogDollarReadmemPath()  relativize1=${e.getClass.getName}: ${e.getMessage}")
          }
        }
        try {
          newPath = workingDirectory.toCanonical.relativize(extracted).path
        } catch {
          case e: Throwable => {
            println(s"fixupVerilogDollarReadmemPath()  newPath=${e.getClass.getName}: ${e.getMessage}")
            try {
              newPath =workingDirectory.toCanonical.relativize(pathCanonical).path
            } catch {
              case e: Throwable => {
                println(s"fixupVerilogDollarReadmemPath()  newPath2=${e.getClass.getName}: ${e.getMessage}")
                newPath = "NOTSET"
              }
            }
          }
        }
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
