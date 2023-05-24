package spinal.tester.scalatest

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.SpinalConfig
import spinal.core.sim.SpinalSimConfig

import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
//import spinal.tester.scalatest.SpinalSimFunSuite

object SpinalTesterSimConfig {

  def toHex(bytes: Array[Byte]): String = {
    bytes.map(x => (x & 0xFF).toHexString.reverse.padTo(2, '0').reverse).mkString("")
  }

  def computeHash(input: String): String = {
    val bytes = input.getBytes(Charset.forName("UTF8"))
    val md5 = MessageDigest.getInstance("MD5")
    val result = toHex(md5.digest(bytes))
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
    * @param test
    *  used to obtain the context of the test for naming
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
//    var simTest: SpinalSimFunSuite = testObject match {
//      case suite: SpinalSimFunSuite => suite
//      case _ => null
//    }

    val stackTrace = Thread.currentThread().getStackTrace
    for(i <- 0 until stackTrace.length) {
      val ele = stackTrace(i)
      val className = ele.getClassName
      if(className.startsWith("spinal.tester.")) {
        val fileName = ele.getFileName

      }
    }

    assert(test != null)
    val testClassName = if(test != null) test.getClass.getSimpleName else "test"  // FIXME "verilator" ?
    val suffixCanon = if(suffix != null) suffix else ""
    val suffixObjectToString = if(suffixObject != null) "_" + suffixObject.toString else ""

    var workspaceName: String = null
    val suffixCanonical = sanitizeStringForFilename(suffixCanon + suffixObjectToString)
    println(s"sanitizeStringForFilename(${suffixCanon + suffixObjectToString}) = ${suffixCanonical}")
    if(suffixCanonical.length > 20) {
      // prune down the total length to something identifiable, unique enough and manageable
      val hashAsString = computeHash(suffixCanonical)
      workspaceName = suffixCanonical.substring(0, 12) + hashAsString.substring(hashAsString.length - 8)
    } else {
      workspaceName = suffixCanonical
    }
    if(workspaceName == null || workspaceName.isEmpty)
      workspaceName = "test"  // FIXME

    //val suffixCanonical = testClassName + suffixCanon + suffixObjectToString

    val config = SpinalSimConfig()

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
