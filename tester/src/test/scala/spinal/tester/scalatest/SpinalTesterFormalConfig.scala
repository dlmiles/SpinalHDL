package spinal.tester.scalatest

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.SpinalConfig
import spinal.core.formal.SpinalFormalConfig
import spinal.lib.DoCmd.isWindows
import spinal.lib.formal.SpinalFormalFunSuite

import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
//import spinal.tester.scalatest.SpinalFormalFunSuite

object SpinalTesterFormalConfig {

  def pathContainsBinary(exe: String): Boolean = {
    assert(!exe.contains('/') && !exe.contains('\\') && !exe.contains(".."))

    val PATH = System.getenv("PATH")
    PATH.split(File.pathSeparator).map(pathElement => new File(pathElement)).foreach(parentDir => {
      val filepath = new File(parentDir, exe)
      if(filepath.isFile)
        return true

      if(isWindows) {
        val filepath_exe = new File(parentDir, exe + ".exe")
        if (filepath_exe.isFile)
          return true
      }
    })

    false
  }

  def toHex(bytes: Array[Byte]): String = {
    bytes.map(x => (x & 0xFF).toHexString.reverse.padTo(2, '0').reverse).mkString("")
  }

  def computeHash(input: String): String = {
    val bytes = input.getBytes(Charset.forName("UTF8"))
    val md = MessageDigest.getInstance("MD5")
    val result = toHex(md.digest(bytes))
    //println(s"MD5 ${input} => ${result}") // Unit Test comparison
    result
  }

  def sanitizeStringForFilename(filename: String): String = {
    filename
      .replaceAll("[\\./\\\\]", "-")
      .replaceAll("[\\s]", "_")
      .replaceAll("[(){}\\[\\],]", "")
  }

  private val defaultTargetDirectory = new AtomicReference[String]()
  // Get the global value only once
  def getDefaultTargetDirectory() = {
    val current = SpinalConfig.defaultTargetDirectory // this is static global
    synchronized {  // load barrier
      val value = defaultTargetDirectory.get()
      if (value == null)
        defaultTargetDirectory.compareAndSet(null, current)
    }
    defaultTargetDirectory.get()
  }

  /**
    *
    * @param testObject
    *  used to obtain the context of the test for providing a namespace (directory)
    * @param suffix
    *  provide a unique-id directly ?
    * @param suffixObject
    *  crc/hash the toString() result for a unique-id ?
    * @return SpinalSimConfig
    */
  def apply(testObject: Any, suffix: String = null, suffixObject: Any = null) = {
    val test: AnyFunSuite = testObject match {
      case suite: AnyFunSuite => suite
      case _ => null
    }
    // It looked useful to have visibility on this
    val formalTest: SpinalFormalFunSuite = testObject match {
      case suite: SpinalFormalFunSuite => suite
      case _ => null
    }

    assert(test != null)
    val testClassName = if(test != null) test.getClass.getSimpleName else "test"
    val suffixCanon = if(suffix != null) suffix else ""
    val suffixObjectToString = if(suffixObject != null) "_" + suffixObject.toString else ""

    var workspaceName: String = null
    val suffixCanonical = sanitizeStringForFilename(suffixCanon + suffixObjectToString)
    println(s"sanitizeStringForFilename(${suffixCanon + suffixObjectToString}) = ${suffixCanonical}")
    val DIRECTORY_NAME_LENGTH = 32
    val HASH_TRUNCATED_LENGTH = 8
    if(suffixCanonical.length > DIRECTORY_NAME_LENGTH) {
      // prune down the total length to something identifiable, unique enough and manageable
      val hashAsString = computeHash(suffixCanonical)
      workspaceName = suffixCanonical.substring(0, DIRECTORY_NAME_LENGTH-HASH_TRUNCATED_LENGTH) + hashAsString.substring(hashAsString.length - HASH_TRUNCATED_LENGTH)
    } else {
      workspaceName = suffixCanonical
    }
    if(workspaceName == null || workspaceName.isEmpty)
      workspaceName = "formal"  // fallback

    val config = SpinalFormalConfig()

    // This is configured in SpinalAnyFunSuite so we relocate under there ./simWorkspace
    if(testClassName != null) {
      val defaultTargetDirectory = getDefaultTargetDirectory()
      var workspacePath = defaultTargetDirectory + File.separator + testClassName  // relocate

      if(!workspacePath.endsWith("/formal"))
        workspacePath += "/formal"    // follows "./simWorkspace/formal"

      println(s"MARK defaultTargetDirectory=${SpinalConfig.defaultTargetDirectory} => ${defaultTargetDirectory}")
      println(s"MARK workspacePath=${workspacePath}")

      config.workspacePath(workspacePath)
    }

    if(workspaceName != null) {
      config.workspaceName(workspaceName)
      println(s"MARK workspaceName(${workspaceName})")
    }

    config
  }

}
