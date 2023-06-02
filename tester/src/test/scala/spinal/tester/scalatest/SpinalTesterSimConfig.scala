package spinal.tester.scalatest

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.SpinalConfig
import spinal.core.sim.SpinalSimConfig

import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

object SpinalTesterSimConfig {

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
//    val test: AnyFunSuite = testObject match {
//      case suite: AnyFunSuite => suite
//      case _ => null
//    }
    // It looked useful to have visibility on this
    val simTest: SpinalSimFunSuite = testObject match {
      case suite: SpinalSimFunSuite => suite
      case _ => null
    }

    var prefix = ""
    if(simTest != null) {
      if (simTest.tester.prefix.equals("verilator_")) {
        prefix = "V"
      } else if (simTest.tester.prefix.equals("ghdl_")) {
        prefix = "G"
      } else if (simTest.tester.prefix.equals("iverilog_")) {
        prefix = "I"
      }
    }

    //assert(test != null, "test != null")
    val testClassName = if(testObject != null) testObject.getClass.getSimpleName else "test"
    val suffixCanon = if(suffix != null) suffix else ""
    val suffixObjectToString = if(suffixObject != null) "_" + suffixObject.toString else ""

    var workspaceName: String = null
    val suffixCanonical = sanitizeStringForFilename(prefix + suffixCanon + suffixObjectToString)
    println(s"sanitizeStringForFilename(${prefix + suffixCanon + suffixObjectToString}) = ${suffixCanonical}")
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
      workspaceName = "test"  // fallback

    val config = if(simTest != null) simTest.SimConfig else SpinalSimConfig()   // simTest configured

    // This is configured in SpinalAnyFunSuite so we relocate under there ./simWorkspace
    if(testClassName != null) {
      val defaultTargetDirectory = getDefaultTargetDirectory()
      val cachePath = defaultTargetDirectory + File.separator + ".cache"  // don't relocate, share it
      val workspacePath = defaultTargetDirectory + File.separator + testClassName  // relocate

      println(s"MARK defaultTargetDirectory=${SpinalConfig.defaultTargetDirectory} => ${defaultTargetDirectory}")
      println(s"MARK cachePath=${cachePath}")
      println(s"MARK workspacePath=${workspacePath}")

      config.cachePath(cachePath)
      config.workspacePath(workspacePath)
    }

    if(workspaceName != null) {
      config.workspaceName(workspaceName)
      println(s"MARK workspaceName(${workspaceName})")
    }

    //config.withFstWave
    //config.withWave(49)
    //config.withVcdWave

    //config.withCoverage

    config
  }

}
