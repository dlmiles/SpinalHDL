package spinal.sim

import java.io.{BufferedInputStream, File, FileInputStream}
import org.apache.commons.io.FileUtils
import spinal.sim.Helper.toHex

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.mutable

object SimError{
  def apply(message : String): Unit ={
    System.out.flush()
    Thread.sleep(20)
    System.err.println("\n\n" + message)
    throw new Exception()
  }
}

object WaveFormat{
  //Common waveformat options
  object VCD extends WaveFormat("vcd")
  object FST extends WaveFormat("fst")
  object DEFAULT extends WaveFormat
  object NONE extends WaveFormat
 
  //GHDL only
  object VCDGZ extends WaveFormat("vcdgz")
  object GHW extends WaveFormat("ghw")

  //Icarus Verilator only
  object FST_SPEED extends WaveFormat("fst-speed")
  object FST_SPACE extends WaveFormat("fst-space")
  object LXT extends WaveFormat("lxt")
  object LXT_SPEED extends WaveFormat("lxt-speed")
  object LXT_SPACE extends WaveFormat("lxt-space")
  object LXT2 extends WaveFormat("lxt2")
  object LXT2_SPEED extends WaveFormat("lxt2-speed")
  object LXT2_SPACE extends WaveFormat("lxt2-space")

  // VCS only
  object VPD extends WaveFormat("vpd")
  object FSDB extends WaveFormat("fsdb")

  // XSim only
  object WDB extends WaveFormat("wdb")
}

sealed class WaveFormat(val ext : String = "???"){
  override def toString: String = {
    getClass().getName().split("\\$").last
  }
}


trait Backend{
  val uniqueId : String
  def isBufferedWrite : Boolean
}

object Backend{
  private var uniqueIdInt = 0
  private var uniqueId = 0.toString
  private val uniqueSet = mutable.Set[String]()
  def allocateUniqueId(id: String = null): String = {
    this.synchronized {
      uniqueIdInt = uniqueIdInt + 1
      uniqueId = uniqueIdInt.toString

      if (id != null) {
        assert(!id.isBlank)
        uniqueId = id
      }

      if (uniqueSet.contains(uniqueId)) // guarantee it is unique
        throw new Exception(s"allocateUniqueId(${uniqueId}) is not unique")
      uniqueSet.add(uniqueId)

      uniqueId
    }
  }

  val osName         = System.getProperty("os.name").toLowerCase
  val isWindows      = osName.contains("windows")
  val isMac          = osName.contains("mac") || osName.contains("darwin")
  val isLinux = !isWindows && !isMac

  val jdk = System.getProperty("java.home").replace("/jre","").replace("\\jre","")
}

object Helper {
  def toHex(bytes: Array[Byte]): String = {
    bytes.map(x => (x & 0xFF).toHexString.reverse.padTo(2, '0').reverse).mkString("")
  }
}

class MDHelper(algorithm: String = "SHA-1") {
  val md = MessageDigest.getInstance(algorithm)

  def hashString(string: String): Unit = {
    md.update(string.getBytes(StandardCharsets.UTF_8))
    md.update(0.toByte)
  }

  def hashStrings(strings: String*): Unit = {
    strings.foreach(hashString)
  }

  def hashFile(file: File): Unit = {
    // Scala 2.13 has scala.util.Using
    val bis = new BufferedInputStream(new FileInputStream(file))
    try {
      val buf = new Array[Byte](1024)

      Iterator.continually(bis.read(buf))
        .takeWhile(_ >= 0)
        .foreach(md.update(buf, 0, _))

      md.update(0.toByte)
    } finally {
      bis.close()
    }
  }

  def hashAllFilesInDirectory(dir: File): Unit = {
    FileUtils.listFiles(dir, null, true).asScala.foreach { file =>
      println(s"hashAllFilesInDirectory(${dir}) => ${file}")    // REMOVEME
      hashFile(file)
    }
    md.update(0.toByte)
  }

  def digest(): Array[Byte] = {
    md.digest()
  }

  def digestHexString(): String = {
    val s = toHex(digest())
    println(s"digestHexString() = ${s}")    // REMOVEME
    s
  }
}

object MDHelper {
  def digestHash(strings: Traversable[String], sourceFiles: Traversable[String], sourceDirs: Traversable[String]): String = {
    val md = new MDHelper()

    strings.foreach { string =>
      md.hashString(string)
    }

    sourceFiles.foreach { filename =>
      md.hashFile(new File(filename))
    }

    sourceDirs.foreach { dirname =>
      md.hashAllFilesInDirectory(new File(dirname))
    }

    md.digestHexString()
  }

}
