package spinal.core.formal

import java.io.{BufferedInputStream, File, FileFilter, FileInputStream, PrintWriter}
import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import org.apache.commons.io.FileUtils

import java.util.Locale
import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import scala.io.Source
import sys.process._

class SpinalSbyException extends Exception {}

object SbyMode extends Enumeration {
  type SbyMode = Value

  val Bmc = Value("bmc")
  val Prove = Value("prove")
  val Live = Value("live")
  val Cover = Value("cover")
  val Equiv = Value("equiv")
  val Synth = Value("synth")
}

sealed trait SbyEngine extends FormalEngin {
  def command: String
}

object SmtBmcSolver extends Enumeration {
  type SmtBmcSolver = Value

  val Yices = Value("yices")
  /** bitwuzla: is untested
   */
  val bitwuzla = Value("bitwuzla")
  /** Boolector: is untested
   */
  val Boolector = Value("boolector")
  val Z3 = Value("z3")
  /** mathsat: is untested
   */
  val mathsat = Value("mathsat")
  val cvc4 = Value("cvc4")
  /** cvc5: is untested
   */
  val cvc5 = Value("cvc5")
}

case class SmtBmc(
    nomem: Boolean = false,
    syn: Boolean = false,
    stbv: Boolean = false,
    stdt: Boolean = false,
    nopresat: Boolean = false,
    unroll: Option[Boolean] = None,
    dumpsmt2: Boolean = false,
    progress: Boolean = true,
    solver: SmtBmcSolver.SmtBmcSolver = SmtBmcSolver.Yices
) extends SbyEngine {
  def command: String = {
    "smtbmc" +
      (if (nomem) { " --nomem" }
       else { "" }) +
      (if (syn) { " --syn" }
       else { "" }) +
      (if (stbv) { " --stbv" }
       else { "" }) +
      (if (stdt) { " --stdt" }
       else { "" }) +
      (if (nopresat) { " --nopresat" }
       else { "" }) +
      (unroll
        .map(on => {
          if (on) { " --unroll" }
          else { "--nounroll" }
        })
        .getOrElse("")) +
      (if (dumpsmt2) { " --dumpsmt2" }
       else { "" }) +
      (if (progress) { " --progress" }
       else { "" }) +
      s" $solver"
  }
}

case class Aiger() extends SbyEngine {
  def command: String = { "aiger" }
}

case class Abc() extends SbyEngine {
  def command: String = { "abc" }
}

class SymbiYosysBackendConfig(
    val rtlSourcesPaths: ArrayBuffer[String] = ArrayBuffer[String](),
    val rtlIncludeDirs: ArrayBuffer[String] = ArrayBuffer[String](),
    var engines: ArrayBuffer[SbyEngine] = ArrayBuffer(SmtBmc()),
    var modesWithDepths: LinkedHashMap[String, Int] = LinkedHashMap[String, Int](),
    var timeout: Option[Int] = None,
    var multiClock: Boolean = false,
    var toplevelName: String = null,
    var workspacePath: String = null,
    var workspaceName: String = null,
    var keepDebugInfo: Boolean = true,    // See notes below around use for default change
    var skipWireReduce: Boolean = false
)

class SymbiYosysBackend(val config: SymbiYosysBackendConfig) extends FormalBackend {
  val workspaceName = new File(config.workspaceName).toPath()
  val workspacePath = new File(config.workspacePath).toPath()
  val workDir = workspaceName.resolve(workspacePath)
  val sbyFileName = s"${config.toplevelName}.sby"
  val sbyFilePath = workDir.resolve(sbyFileName)

  def clean(): Unit = {
    FileUtils.deleteQuietly(workDir.toFile())
  }

  def genSby(): Unit = {
    val localSources = config.rtlSourcesPaths.map(f => new File(f).getAbsolutePath).mkString("\n")
    val read = config.rtlSourcesPaths
      .map(f => {
        Paths.get(f).getFileName
      })
      .mkString(" ")
    val engineCmds = config.engines.map(engine => engine.command).mkString("\n")

    if (!config.modesWithDepths.nonEmpty) config.modesWithDepths("bmc") = 100
    var modes = config.modesWithDepths.keys
    val taskCmd = modes.mkString("\n")
    val modeCmd = modes.map(x => s"$x: mode $x").mkString("\n")
    val depthCmd = modes.map(x => s"$x: depth ${config.modesWithDepths(x)}").mkString("\n")
    val skipWireReduce: String = if (config.skipWireReduce) "-ifx" else ""

    val script = "[tasks]\n" +
      taskCmd +
      "\n\n" +
      "[options]\n" +
      modeCmd +
      "\n" +
      depthCmd +
      "\n" +
      (config.timeout.map(t => s"timeout $t\n").getOrElse("")) +
      (if (config.multiClock) { "multiclock on\n" }
       else { "" }) +
      "\n" +
      "[engines]\n" +
      engineCmds +
      "\n\n" +
      "[script]\n" +
      s"read -formal $read\n" +
      s"prep ${skipWireReduce} -top ${config.toplevelName}\n" +
      "\n" +
      "[files]\n" +
      localSources

    workDir.toFile.mkdir()
    Files.write(
      sbyFilePath,
      script.getBytes()
    )
  }

  class Logger extends ProcessLogger {
    override def err(s: => String): Unit = { println(s) }
    override def out(s: => String): Unit = {}
    override def buffer[T](f: => T) = f
  }

  def doVerify(name: String): Unit = {
    println(f"[Progress] Start ${config.toplevelName} formal verification with $name.")
    val isWindows = System.getProperty("os.name").toLowerCase.contains("windows")
    val command = if (isWindows) "sby.exe" else "sby"
    val exitStatus = Process(Seq(command, "-f", sbyFilePath.toString()), workspacePath.toFile()).!(new Logger())
    val success = exitStatus == 0

    var hasResult = false       // did sby process complete and emit logfile end marker
    var verdict: String = null  // "PASS" | "FAIL"
    var resultCode: Integer = null  // rc=0
    var exc: FormalResultException = null

    // Do this always to provide a summary towards getting a formal_results.xml
    if(true) {
      var assertedLines = ArrayBuffer[String]()
      def analyseLog(file : File): Unit ={
        if(file.exists()){
          val pattern = """(\w+.sv):(\d+).(\d+)-(\d+).(\d+)""".r
          val PATTERN_verdict = """\s+DONE\s+\(([A-Za-z0-9_$]+)""".r
          val PATTERN_rc = """rc=(\d+)""".r
          for (line <- Source.fromFile(file).getLines) {
            if(!success)    // FIXME this would be better based on unexpected outcome not exitStatus
              println(line)

            // We validate that we saw the DONE marker, indicating Yosys execution completed with a result and the full log was written out ok
            //   (as opposed to crashed, terminated due to time limits, disk full log truncations, etc...)
            if(line.contains(" DONE ")) {	// toplevel logfile.txt
             PATTERN_verdict.findFirstMatchIn(line) match {
                case Some(x) => verdict = x.group(1)	// "PASS" | "FAIL" | "ERROR"
                case None =>
              }
              PATTERN_rc.findFirstMatchIn(line) match {
                case Some(x) => resultCode = x.group(1).toInt
                case None =>
              }
            } else if(line.contains("Status: failed")) {	// engine_0/logfile.txt  engine_0/logfile_basecase.txt
               hasResult = true
               verdict = "FAILED"
            } else if (line.contains("Temporal induction failed!")) { // engine_0/logfile_induction.txt
              hasResult = true
              verdict = "FAILED"
            } else if(line.contains("Status: passed")) {	// engine_0/logfile.txt  engine_0/logfile_induction.txt
              // engine_0/logfile_induction.txt Also "Temporal induction successful."
               hasResult = true
               verdict = "PASSED"
            } else if(line.contains("Status: error")) {		// engine_0/logfile.txt
               verdict = "ERROR"
            }

            if(line.contains("Assert failed in") || line.contains("Unreached cover statement")){
              pattern.findFirstMatchIn(line) match {
                case Some(x) => {
                  val sourceName = x.group(1)
                  val assertLine = x.group(2).toInt
                  val source = Source.fromFile(workspacePath.resolve(Paths.get("rtl", sourceName)).toFile()).getLines.drop(assertLine)
                  val assertString = source.next().dropWhile(_ == ' ')
                  assertedLines += assertString
                  println("--   " +assertString)
                }
                case None =>
              }
            }
          }
        }
      }
      for((name, depth) <- config.modesWithDepths) {
        val logFileName = name match {
          case "bmc" => Seq("logfile.txt")
          case "prove" => Seq("logfile_basecase.txt", "logfile_induction.txt")
          case "cover" => Seq("logfile.txt")
        }
        for (e <- logFileName) analyseLog(workDir.resolve(Paths.get(s"${config.toplevelName}_${name}", "engine_0", e)).toFile())
      }

      val assertedLinesFormatted = assertedLines.map{l =>
        val splits = l.split("// ")
        "(" + splits(1).replace(":L", ":") + ")  "
      }
      val assertsReport = assertedLinesFormatted.map(e => s"\tissue.triggered.from${e}\n").mkString("")
      val proofAt = "\tproof in " + workDir + s"/${config.toplevelName}_${config.modesWithDepths.head._1}/engine_0"

      val message = if(success) "SymbiYosys completed" else s"SymbiYosys exit ${exitStatus}"

      val passOrFail: java.lang.Boolean = if(verdict != null) verdict.toUpperCase(Locale.ENGLISH) match {
        case "PASS" => true
        case "PASSED" => true
        case "FAIL" => false
        case "FAILED" => false
        case _ => null
      } else null

      exc = FormalResultException.builder(message + "\n" + assertsReport + proofAt, hasResult, exitStatus, passOrFail, resultCode)
    }
    // This has the unwanted behaviour of leaving behind all the failed Yosys (exitStatus) not the failed unit tests (context)
    //    so it does not cleanup for SpinalFormalFunSuite#shouldFail(), it also cleans up in scenarios of an unexpected pass.
    // Since we clean before the next start in FormalBootstraps#compile():198 why should the default be to cleanup ?
    // No other simWorkspace delete the results by default, 'sbt clean' exists in Java space when that is important,
    // the point being the behaviour is not consistent with expectations from other aspects of development
    // it is more annoying to delete data you need to diagnose problem, than to have data laying around you can always delete later
    if (!config.keepDebugInfo && exc == null) clean()

    if(exc != null)
      throw exc

    throw new Exception("SpinalFormal failure to parse log outputs")
  }

  def checks(): Unit = {
    config.modesWithDepths.keySet.map(x => SbyMode.withName(x))
  }

  checks()
  genSby()
}
