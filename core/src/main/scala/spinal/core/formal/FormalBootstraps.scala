/*                                                                           *\
**        _____ ____  _____   _____    __                                    **
**       / ___// __ \/  _/ | / /   |  / /   HDL Core                         **
**       \__ \/ /_/ // //  |/ / /| | / /    (c) Dolu, All rights reserved    **
**      ___/ / ____// // /|  / ___ |/ /___                                   **
**     /____/_/   /___/_/ |_/_/  |_/_____/                                   **
**                                                                           **
**      This library is free software; you can redistribute it and/or        **
**    modify it under the terms of the GNU Lesser General Public             **
**    License as published by the Free Software Foundation; either           **
**    version 3.0 of the License, or (at your option) any later version.     **
**                                                                           **
**      This library is distributed in the hope that it will be useful,      **
**    but WITHOUT ANY WARRANTY; without even the implied warranty of         **
**    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU      **
**    Lesser General Public License for more details.                        **
**                                                                           **
**      You should have received a copy of the GNU Lesser General Public     **
**    License along with this library.                                       **
\*                                                                           */
package spinal.core.formal

import java.io.{File, PrintWriter}
import org.apache.commons.io.FileUtils
import spinal.core.internals.{PhaseContext, PhaseNetlist}
import spinal.core.sim.SimWorkspace
import spinal.core.util.Util
import spinal.core.{BlackBox, Component, SpinalConfig, SpinalReport}

import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import scala.io.Source
import scala.reflect.io.Path


// TYPO: FormalEngine
trait FormalEngin {}
trait FormalBackend {
  def doVerify(name: String = "formal"): Unit
}

/** Formal verify Workspace
  */
object FormalWorkspace {
  def allocateUniqueId(): Int = SimWorkspace.allocateUniqueId()
  def allocateWorkspace(path: String, name: String): String = SimWorkspace.allocateWorkspace(path, name)
}

class SpinalFormalBackendSel
object SpinalFormalBackendSel {
  val SYMBIYOSYS = new SpinalFormalBackendSel
}

/** SpinalSim configuration
  */
case class SpinalFormalConfig(
    var _workspacePath: String = System.getenv().getOrDefault("SPINALSIM_WORKSPACE", "./simWorkspace/formal"),
    var _workspaceName: String = null,
    var _spinalConfig: SpinalConfig = SpinalConfig().includeFormal,
    var _additionalRtlPath: ArrayBuffer[String] = ArrayBuffer[String](),
    var _additionalIncludeDir: ArrayBuffer[String] = ArrayBuffer[String](),
    var _modesWithDepths: LinkedHashMap[String, Int] = LinkedHashMap[String, Int](),
    var _backend: SpinalFormalBackendSel = SpinalFormalBackendSel.SYMBIYOSYS,
    var _keepDebugInfo: Boolean = false,
    var _skipWireReduce: Boolean = false,
    var _hasAsync: Boolean = false,
    var _timeout: Option[Int] = None,
    var _engines: ArrayBuffer[FormalEngin] = ArrayBuffer()
) {
  def withSymbiYosys: this.type = {
    _backend = SpinalFormalBackendSel.SYMBIYOSYS
    this
  }

  def withBMC(depth: Int = 100): this.type = {
    _modesWithDepths("bmc") = depth
    this
  }

  def withProve(depth: Int = 100): this.type = {
    _modesWithDepths("prove") = depth
    this
  }

  def withCover(depth: Int = 100): this.type = {
    _modesWithDepths("cover") = depth
    this
  }

  def withTimeout(timeout: Int): this.type = {
    _timeout = Some(timeout)
    this
  }

  // TYPO: withEngines
  def withEngies(engines: Seq[FormalEngin]): this.type = {
    _engines.clear()
    _engines ++= engines
    this
  }

  // TYPO: addEngine
  def addEngin(engine: FormalEngin): this.type = {
    _engines ++= Seq(engine)
    this
  }

  def withAsync: this.type = {
    _hasAsync = true
    this
  }

  def workspacePath(path: String): this.type = {
    _workspacePath = path
    this
  }

  def workspaceName(name: String): this.type = {
    _workspaceName = name
    this
  }

  def withConfig(config: SpinalConfig): this.type = {
    _spinalConfig = config
    this
  }

  def withDebug: this.type = {
    _keepDebugInfo = true
    this
  }

  def withoutDebug: this.type = {
    _keepDebugInfo = false
    this
  }

  def withOutWireReduce: this.type = {
    _skipWireReduce = true
    this
  }

  def addRtl(that: String): this.type = {
    _additionalRtlPath += that
    this
  }

  def addIncludeDir(that: String): this.type = {
    _additionalIncludeDir += that
    this
  }

  def doVerify[T <: Component](report: SpinalReport[T]): Unit = compile(report).doVerify()
  def doVerify[T <: Component](report: SpinalReport[T], name: String): Unit =
    compile(report).doVerify(name)

  def doVerify[T <: Component](rtl: => T): Unit = compile(rtl).doVerify()
  def doVerify[T <: Component](rtl: => T, name: String): Unit = compile(rtl).doVerify(name)

  def compile[T <: Component](rtl: => T): FormalBackend = {
    this.copy().compileCloned(rtl)
  }

  def compileCloned[T <: Component](rtl: => T): FormalBackend = {
    val uniqueId = FormalWorkspace.allocateUniqueId()
    new File(s"tmp").mkdirs()
    new File(s"tmp/job_$uniqueId").mkdirs()
    val config = _spinalConfig
      .copy(targetDirectory = s"tmp/job_$uniqueId")
      .addTransformationPhase(new PhaseNetlist {
        override def impl(pc: PhaseContext): Unit = pc.walkComponents {
          case b: BlackBox if b.isBlackBox && b.isSpinalSimWb => b.clearBlackBox()
          case _                                              =>
        }
      })
    val report = _backend match {
      case SpinalFormalBackendSel.SYMBIYOSYS =>
        // config.generateVerilog(rtl)
        config.generateSystemVerilog(rtl)
    }
    report.blackboxesSourcesPaths ++= _additionalRtlPath
    report.blackboxesIncludeDir ++= _additionalIncludeDir
    compile[T](report)
  }

  def compile[T <: Component](report: SpinalReport[T]): FormalBackend = {
    if (_workspacePath.startsWith("~"))
      _workspacePath = System.getProperty("user.home") + _workspacePath.drop(1)

    if (_workspaceName == null)
      _workspaceName = s"${report.toplevelName}"

    _workspaceName = FormalWorkspace.allocateWorkspace(_workspacePath, _workspaceName)

    println(
      f"[Progress] Formal verification workspace in ${new File(s"${_workspacePath}/${_workspaceName}").getAbsolutePath}"
    )
    val rootWorkplace = new File(_workspacePath).getAbsoluteFile.toPath()
    val workingWorkspace = rootWorkplace.resolve(_workspaceName)
    val rtlDir = workingWorkspace.resolve("rtl")

    rootWorkplace.toFile.mkdirs()
    FileUtils.deleteQuietly(workingWorkspace.toFile())
    if(workingWorkspace.toFile.exists()) // This could be true due to gtkwave have generated VCD open
      println(s"f[warning] Formal workspace directory could not be deleted before run: ${workingWorkspace}")
    workingWorkspace.toFile.mkdirs()
    rtlDir.toFile.mkdirs()

    val rtlFiles = new ArrayBuffer[String]()
//    val rtlPath = rtlDir.getAbsolutePath
    report.generatedSourcesPaths.foreach { srcPath =>
      val src = new File(srcPath)
      val lines = Source.fromFile(src).withClose(() => {}).getLines.toArray
      println(s"FormalBootstraps ${src} has ${lines.length} lines")
      val w = new PrintWriter(src)
      for (line <- lines) {
        val str = if (line.contains('$' + "readmem")) {
          println(s"FormalBootstraps ${src}:nnn has ${lines.length} lines and has ${line}")
          // CWD from the simulator runtime perspective, so there is an extra directory which we use
          //  using the name "simulatorName" to be an arbitrary directory name to act as CWD during sim execution
          val currentWorkingDirectoryPath = Path(workingWorkspace.resolve("simulatorName").toAbsolutePath.toFile)
          val targetDirectoryPath = Path(workingWorkspace.resolve("rtl").toAbsolutePath.toFile)
          val newline = Util.fixupVerilogDollarReadmemPath(line, currentWorkingDirectoryPath, targetDirectoryPath)
          println(s"FormalBootstraps ${src}:nnn has ${lines.length} lines and has ${newline}")
          newline
        } else {
          line
        }
        w.println(str)
      }
      w.close()

      val dst = rtlDir.resolve(src.getName)
      FileUtils.copyFileToDirectory(src, rtlDir.toFile())
      rtlFiles.append(dst.toString)
    }

    _backend match {
      case SpinalFormalBackendSel.SYMBIYOSYS =>
        println(f"[Progress] Yosys compilation started")
        val startAt = System.nanoTime()
        val vConfig = new SymbiYosysBackendConfig(
          workspacePath = workingWorkspace.toString(),
          workspaceName = "formal",
          toplevelName = report.toplevelName,
          modesWithDepths = _modesWithDepths,
          timeout = _timeout,
          keepDebugInfo = _keepDebugInfo,
          skipWireReduce = _skipWireReduce,
          multiClock = _hasAsync
        )
        vConfig.rtlSourcesPaths ++= rtlFiles
        vConfig.rtlIncludeDirs ++= report.rtlIncludeDirs
        if (_engines.nonEmpty)
          vConfig.engines = _engines.map(x =>
            x match {
              case e: SbyEngine => e
            }
          )

        val backend = new SymbiYosysBackend(vConfig)
        val deltaTime = (System.nanoTime() - startAt) * 1e-6
        println(f"[Progress] Yosys compilation done in $deltaTime%1.3f ms")
        backend
    }
  }
}
