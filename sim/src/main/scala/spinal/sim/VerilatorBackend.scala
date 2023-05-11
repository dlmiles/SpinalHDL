package spinal.sim

import java.io.{BufferedInputStream, File, FileFilter, FileInputStream, FileNotFoundException, IOException, PrintWriter}

import javax.tools.JavaFileObject
import net.openhft.affinity.impl.VanillaCpuLayout
import org.apache.commons.io.FileUtils
import java.security.MessageDigest

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import sys.process._
import scala.io.Source

class VerilatorBackendConfig{
  var signals                = ArrayBuffer[Signal]()
  var optimisationLevel: Int = 2
  val rtlSourcesPaths        = ArrayBuffer[String]()
  val rtlIncludeDirs         = ArrayBuffer[String]()
  var toplevelName: String   = null
  var maxCacheEntries: Int   = 100
  var cachePath: String      = null
  var workspacePath: String  = null
  var workspaceName: String  = null
  var vcdPath: String        = null
  var vcdPrefix: String      = null
  var waveFormat             : WaveFormat = WaveFormat.NONE
  var waveDepth:Int          = 1 // 0 => all
  var simulatorFlags         = ArrayBuffer[String]()
  var runFlags               = ArrayBuffer[String]()
  var withCoverage           = false
  var timeUnit: String       = null
  var timePrecision: String  = null
}


object VerilatorBackend {
  private val cacheGlobalLock = new Object()
  private val cachePathLockMap = mutable.HashMap[String, Object]()
}

class VerilatorBackend(val config: VerilatorBackendConfig) extends Backend {
  import Backend._

  val cachePath      = config.cachePath
  val cacheEnabled   = cachePath != null
  val maxCacheEntries = config.maxCacheEntries
  val workspaceName  = config.workspaceName
  val workspacePath  = config.workspacePath
  val wrapperCppName = s"V${config.toplevelName}__spinalWrapper.cpp"
  val wrapperCppPath = new File(s"${workspacePath}/${workspaceName}/$wrapperCppName").getAbsolutePath

  def cacheGlobalSynchronized(function: => Unit) = {
    if (cacheEnabled) {
      VerilatorBackend.cacheGlobalLock.synchronized {
        function
      }
    } else {
      function
    }
  }

  def cacheSynchronized(cacheFile: File)(function: => Unit): Unit = {
    if (cacheEnabled) {
      var lock: Object = null
      VerilatorBackend.cachePathLockMap.synchronized {
        lock = VerilatorBackend.cachePathLockMap.getOrElseUpdate(cacheFile.getCanonicalPath(), new Object())
      }

      lock.synchronized {
        function
      }
    } else {
      function
    }
  }

  def clean(): Unit = {
    FileUtils.deleteQuietly(new File(s"${workspacePath}/${workspaceName}"))
  }

  val availableFormats = Array(WaveFormat.VCD, WaveFormat.FST, 
                               WaveFormat.DEFAULT, WaveFormat.NONE)

  val format = if(availableFormats contains config.waveFormat){
                config.waveFormat  
              } else {
                println("Wave format " + config.waveFormat + " not supported by Verilator")
                WaveFormat.NONE
              }

  def encodeName(name: String): String = {
    // See verilator/src/V3Ast.cpp:86   AstNode::encodeName(const string&)
    def encodeChar(c: Char): String = {
      if (c.toInt > 255)
        return ""
      c.toShort.formatted("__0%02x")
    }

    // rules are
    // * isAlphabetic() [A-Za-z] are never encoded
    // * isDigit() [0-9] is only encoded if it is the first character, C++ symbols can't start with a digit
    // * underscore [_] is only encoded when found in pairs and only the 2nd of each pair is encoded
    //    also note the underscore hex encoding is in uppercase, while all other encodings use
    //    lowercase hex, this maybe important as C++ symbols as case sensitive
    //      _foo => _foo,
    //     __foo => ___05Ffoo,com
    //    ___foo => ___05F_foo,
    //   ____foo => ___05F___05Ffoo
    // * all other 8bit characters are encoded
    name.replace("__", "___05F").zipWithIndex.map {
      case s@(_, 0) => if (s._1 == '_' || Character.isAlphabetic(s._1)) s._1.toString else encodeChar(s._1)
      case s@(_, _) => if (s._1 == '_' || Character.isAlphabetic(s._1) || Character.isDigit(s._1)) s._1.toString else encodeChar(s._1)
    }.mkString("")
  }

  def genWrapperCpp(verilatorVersionDeci: BigDecimal): Boolean = {
    val jniPrefix = "Java_" + s"wrapper_${workspaceName}".replace("_", "_1") + "_VerilatorNative_"
    val wrapperString = s"""
#include <stdint.h>
#include <string>
#include <memory>
#include <jni.h>
#include <iostream>

#include "V${config.toplevelName}.h"
#ifdef TRACE
#include "verilated_${format.ext}_c.h"
#endif
#include "V${config.toplevelName}__Syms.h"

using namespace std;

class ISignalAccess{
public:
  virtual ~ISignalAccess() {}

  virtual void getAU8(JNIEnv *env, jbyteArray value) {}
  virtual void getAU8_mem(JNIEnv *env, jbyteArray value, size_t index) {}
  virtual void setAU8(JNIEnv *env, jbyteArray value, int length) {}
  virtual void setAU8_mem(JNIEnv *env, jbyteArray value, int length, size_t index) {}

  virtual uint64_t getU64() = 0;
  virtual uint64_t getU64_mem(size_t index) = 0;
  virtual void setU64(uint64_t value) = 0;
  virtual void setU64_mem(uint64_t value, size_t index) = 0;
};

class  CDataSignalAccess : public ISignalAccess{
public:
    CData *raw;
    CDataSignalAccess(CData *raw) : raw(raw){}
    CDataSignalAccess(CData &raw) : raw(addressof(raw)){}
    uint64_t getU64() {return *raw;}
    uint64_t getU64_mem(size_t index) {return raw[index];}
    void setU64(uint64_t value)  {*raw = value; }
    void setU64_mem(uint64_t value, size_t index){raw[index] = value; }
};


class  SDataSignalAccess : public ISignalAccess{
public:
    SData *raw;
    SDataSignalAccess(SData *raw) : raw(raw){}
    SDataSignalAccess(SData &raw) : raw(addressof(raw)){}
    uint64_t getU64() {return *raw;}
    uint64_t getU64_mem(size_t index) {return raw[index];}
    void setU64(uint64_t value)  {*raw = value; }
    void setU64_mem(uint64_t value, size_t index){raw[index] = value; }
};


class  IDataSignalAccess : public ISignalAccess{
public:
    IData *raw;
    IDataSignalAccess(IData *raw) : raw(raw){}
    IDataSignalAccess(IData &raw) : raw(addressof(raw)){}
    uint64_t getU64() {return *raw;}
    uint64_t getU64_mem(size_t index) {return raw[index];}
    void setU64(uint64_t value)  {*raw = value; }
    void setU64_mem(uint64_t value, size_t index){raw[index] = value; }
};


class  QDataSignalAccess : public ISignalAccess{
public:
    QData *raw;
    QDataSignalAccess(QData *raw) : raw(raw){}
    QDataSignalAccess(QData &raw) : raw(addressof(raw)){}
    uint64_t getU64() {return *raw;}
    uint64_t getU64_mem(size_t index) {return raw[index];}
    void setU64(uint64_t value)  {*raw = value; }
    void setU64_mem(uint64_t value, size_t index){raw[index] = value; }
};

class  WDataSignalAccess : public ISignalAccess{
public:
    WData *raw;
    uint32_t width;
    uint32_t wordsCount;
    bool sint;

    WDataSignalAccess(WData *raw, uint32_t width, bool sint) : 
      raw(raw), width(width), wordsCount((width+31)/32), sint(sint) {}

    uint64_t getU64_mem(size_t index) {
      WData *mem_el = &(raw[index*wordsCount]);
      return mem_el[0] + (((uint64_t)mem_el[1]) << 32);
    }

    uint64_t getU64() { return getU64_mem(0); }

    void setU64_mem(uint64_t value, size_t index)  {
      WData *mem_el = &(raw[index*wordsCount]);
      mem_el[0] = value;
      mem_el[1] = value >> 32;
      uint32_t padding = ((value & 0x8000000000000000l) && sint) ? 0xFFFFFFFF : 0;
      for(uint32_t idx = 2;idx < wordsCount;idx++){
        mem_el[idx] = padding;
      }

      if(width%32 != 0) mem_el[wordsCount-1] &= (1l << width%32)-1;
    }

    void setU64(uint64_t value)  {
      setU64_mem(value, 0);
    }
    
    void getAU8_mem(JNIEnv *env, jbyteArray value, size_t index) {
      WData *mem_el = &(raw[index*wordsCount]);
      uint32_t byteCount = wordsCount*4;
      uint32_t shift = 32-(width % 32);
      uint32_t backup = mem_el[wordsCount-1];
      uint8_t values[byteCount + !sint];
      if(sint && shift != 32) mem_el[wordsCount-1] = (((int32_t)backup) << shift) >> shift;
      for(uint32_t idx = 0;idx < byteCount;idx++){
        values[idx + !sint] = ((uint8_t*)mem_el)[byteCount-idx-1];
      }
      (env)->SetByteArrayRegion ( value, 0, byteCount + !sint, reinterpret_cast<jbyte*>(values));
      mem_el[wordsCount-1] = backup;
    }
  
    void getAU8(JNIEnv *env, jbyteArray value) {
      getAU8_mem(env, value, 0);
    }

    void setAU8_mem(JNIEnv *env, jbyteArray jvalue, int length, size_t index) {
      WData *mem_el = &(raw[index*wordsCount]);
      jbyte value[length];
      (env)->GetByteArrayRegion( jvalue, 0, length, value);
      uint32_t padding = (value[0] & 0x80 && sint) != 0 ? 0xFFFFFFFF : 0;
      for(uint32_t idx = 0;idx < wordsCount;idx++){
        mem_el[idx] = padding;
      }
      uint32_t capedLength = length > 4*wordsCount ? 4*wordsCount : length;
      for(uint32_t idx = 0;idx < capedLength;idx++){
        ((uint8_t*)mem_el)[idx] = value[length-idx-1];
      }
      if(width%32 != 0) mem_el[wordsCount-1] &= (1l << width%32)-1;
    }

    void setAU8(JNIEnv *env, jbyteArray jvalue, int length) {
      setAU8_mem(env, jvalue, length, 0);
    }
};

class Wrapper_${uniqueId};

#include <chrono>
using namespace std::chrono;

#if ((VERILATOR_VERSION_INTEGER >= 4200000L) && defined(VL_TIME_CONTEXT))
 // In my testing on windows mingw-w64-x86_64-gcc when VL_TIME_CONTEXT was not defined
 //  even though Verilator >= 4.200 it still insists on needing the global symbols
 //  sc_time_stamp or vl_time_stamp64.  But with VL_TIME_CONTEXT defined they can be omitted.
 //
 // This #if section is a no-op as it is the expected default for current versions
 //  most of the #ifdef's are to cover older versions of Verilator as best we can.
#else  // No support for VerilatedContext before 4.200
 #define EMIT_VL_TIME_STAMP64 1
 #define EMIT_SC_TIME_STAMP 1
 #define EMIT_THREAD_LOCAL_HANDLE 1
#endif

#if (VERILATOR_VERSION_INTEGER >= 4200000L)
  // This based on version only, independent of the source of time
  #define USE_VERILATED_CONTEXT 1
#endif
#ifdef USE_VERILATED_CONTEXT
  #define VERILATED_CONTEXTP(contextp)  (contextp)->
  #define VERILATED_COV(contextp)       (contextp)->coveragep()->
#else
  #define VERILATED_CONTEXTP(contextp)  Verilated::
  #define VERILATED_COV(contextp)       VerilatedCov::
#endif

#define SIM_TIME(handle) ((handle)->time())
#define SIM_TIMEINC(handle, v) ((handle)->timeInc(v))

class Wrapper_${uniqueId}{
public:
#ifdef EMIT_THREAD_LOCAL_HANDLE
    uint64_t _time;
#endif
    high_resolution_clock::time_point lastFlushAt;
    uint32_t timeCheck;
    bool waveEnabled;
    V${config.toplevelName} *top;
#ifdef USE_VERILATED_CONTEXT
    VerilatedContext *contextp;
#endif
    ISignalAccess *signalAccess[${config.signals.length}];
#ifdef TRACE
	  Verilated${format.ext.capitalize}C tfp;
#endif
    string name;
    int32_t time_precision;
    int32_t time_unit;

    Wrapper_${uniqueId}(
#ifdef USE_VERILATED_CONTEXT
        VerilatedContext *contextp,
#endif
        V${config.toplevelName} *top,
        const char * name){

#ifdef USE_VERILATED_CONTEXT
      this->contextp = contextp;
#endif
      this->top = top;

#ifdef EMIT_THREAD_LOCAL_HANDLE
      _time = 0;
#endif
#if (defined(USE_VERILATED_CONTEXT) && defined(VL_TIME_CONTEXT))
      contextp->time(0);
#endif
      timeCheck = 0;
      lastFlushAt = high_resolution_clock::now();
      waveEnabled = true;
${    val signalInits = for((signal, id) <- config.signals.zipWithIndex) yield {
      val typePrefix = if(signal.dataType.width <= 8) "CData"
      else if(signal.dataType.width <= 16) "SData"
      else if(signal.dataType.width <= 32) "IData"
      else if(signal.dataType.width <= 64) "QData"
      else "WData"
      val enforcedCast = if(signal.dataType.width > 64) "(WData*)" else ""
      val signalReference = s"top->${signal.path.map(encodeName).mkString("->")}"
      val memPatch = if(signal.dataType.isMem) "[0]" else ""

      s"      signalAccess[$id] = new ${typePrefix}SignalAccess($enforcedCast $signalReference$memPatch ${if(signal.dataType.width > 64) s" , ${signal.dataType.width}, ${if(signal.dataType.isInstanceOf[SIntDataType]) "true" else "false"}" else ""});\n"

    }

      signalInits.mkString("")
    }
#ifdef TRACE
      VERILATED_CONTEXTP(contextp)traceEverOn(true);
      top->trace(&tfp, 99);
      tfp.open((std::string("${new File(config.vcdPath).getAbsolutePath.replace("\\","\\\\")}/${if(config.vcdPrefix != null) config.vcdPrefix + "_" else ""}") + name + ".${format.ext}").c_str());
#endif
      this->name = name;
#if (VERILATOR_VERSION_INTEGER >= 4034000L)
      this->time_precision = VERILATED_CONTEXTP(contextp)timeprecision();
      this->time_unit = VERILATED_CONTEXTP(contextp)timeunit();
#else
      this->time_precision = VL_TIME_PRECISION;
      this->time_unit = VL_TIME_UNIT;
#endif
    }

    virtual ~Wrapper_${uniqueId}(){
      for(int idx = 0;idx < ${config.signals.length};idx++){
          delete signalAccess[idx];
      }

#ifdef TRACE
      if(waveEnabled) tfp.dump((vluint64_t)SIM_TIME(this));
      tfp.close();
#endif
#ifdef COVERAGE
      VERILATED_COV(contextp)write((("${new File(config.vcdPath).getAbsolutePath.replace("\\","\\\\")}/${if(config.vcdPrefix != null) config.vcdPrefix + "_" else ""}") + name + ".dat").c_str());
#endif

      top = nullptr;
#ifdef USE_VERILATED_CONTEXT
      contextp = nullptr;
#endif
    }

#ifdef USE_VERILATED_CONTEXT
    uint64_t time() {
       return contextp->time();
    }
    void timeInc(uint64_t v) {
       contextp->timeInc(v);
    }
#elif EMIT_THREAD_LOCAL_HANDLE
    uint64_t time() {
       return _time;
    }
    void timeInc(uint64_t v) {
       _time += v;
    }
#else
 #error "!USE_VERILATED_CONTEXT && !EMIT_THREAD_LOCAL_HANDLE defined, please report your version of Verilator and platform and toolchain"
#endif

    void final(){
      top->final();
    }
};

#if (defined(EMIT_THREAD_LOCAL_HANDLE) || !defined(VL_TIME_CONTEXT))
  thread_local Wrapper_${uniqueId} *simHandle${uniqueId};
#endif
#ifdef EMIT_VL_TIME_STAMP64
uint64_t vl_time_stamp64() {
  return SIM_TIME(simHandle${uniqueId});
}
#endif
#ifdef EMIT_SC_TIME_STAMP
double sc_time_stamp () {
  return (double) SIM_TIME(simHandle${uniqueId});
}
#endif

#ifdef __cplusplus
extern "C" {
#endif
#include <stdio.h>
#include <stdint.h>

#define API __attribute__((visibility("default")))


JNIEXPORT Wrapper_${uniqueId} * API JNICALL ${jniPrefix}newHandle_1${uniqueId}
  (JNIEnv * env, jobject obj, jstring name, jint seedValue){
    #if defined(_WIN32) && !defined(__CYGWIN__)
    srand(seedValue);
    #else
    srand48(seedValue);
    #endif

#ifdef USE_VERILATED_CONTEXT
    VerilatedContext *contextp = new VerilatedContext;
#endif
    V${config.toplevelName} *top = new V${config.toplevelName}(
#ifdef USE_VERILATED_CONTEXT
      contextp
#endif
     );

    const char* ch = env->GetStringUTFChars(name, 0);
    Wrapper_${uniqueId} *handle = new Wrapper_${uniqueId}(
#ifdef USE_VERILATED_CONTEXT
      contextp,
#endif
      top,
      ch);
    env->ReleaseStringUTFChars(name, ch);

#ifdef EMIT_THREAD_LOCAL_HANDLE
    simHandle${uniqueId} = handle;
#endif
    return handle;
}

JNIEXPORT void API JNICALL ${jniPrefix}randSeed_1${uniqueId}
          (JNIEnv *, jobject, Wrapper_${uniqueId} *handle, jint seed){
    VERILATED_CONTEXTP(handle->contextp)randSeed(seed);
}

JNIEXPORT void API JNICALL ${jniPrefix}randReset_1${uniqueId}
      (JNIEnv *, jobject, Wrapper_${uniqueId} *handle, jint val){
    VERILATED_CONTEXTP(handle->contextp)randReset(val);
}

JNIEXPORT jboolean API JNICALL ${jniPrefix}eval_1${uniqueId}
  (JNIEnv *, jobject, Wrapper_${uniqueId} *handle){
   handle->top->eval();
   return VERILATED_CONTEXTP(handle->contextp)gotFinish();
}

JNIEXPORT jint API JNICALL ${jniPrefix}getTimeUnit_1${uniqueId}
  (JNIEnv *, jobject, Wrapper_${uniqueId} *handle){
  return handle->time_unit;
}

JNIEXPORT jint API JNICALL ${jniPrefix}getTimePrecision_1${uniqueId}
  (JNIEnv *, jobject, Wrapper_${uniqueId} *handle){
  return handle->time_precision;
}

JNIEXPORT void API JNICALL ${jniPrefix}sleep_1${uniqueId}
  (JNIEnv *, jobject, Wrapper_${uniqueId} *handle, uint64_t cycles){
#ifdef TRACE
  if(handle->waveEnabled) {
    handle->tfp.dump((vluint64_t)SIM_TIME(handle));
  }
  handle->timeCheck++;
  if(handle->timeCheck > 10000){
    handle->timeCheck = 0;
    high_resolution_clock::time_point timeNow = high_resolution_clock::now();
    duration<double, std::milli> time_span = timeNow - handle->lastFlushAt;
    if(time_span.count() > 1e3){
      handle->lastFlushAt = timeNow;
      handle->tfp.flush();
    }
  }
#endif
  SIM_TIMEINC(handle, cycles);
}

JNIEXPORT jlong API JNICALL ${jniPrefix}getU64_1${uniqueId}
  (JNIEnv *, jobject, Wrapper_${uniqueId} *handle, int id){
  return handle->signalAccess[id]->getU64();
}

JNIEXPORT jlong API JNICALL ${jniPrefix}getU64mem_1${uniqueId}
  (JNIEnv *, jobject, Wrapper_${uniqueId} *handle, int id, uint64_t index){
  return handle->signalAccess[id]->getU64_mem(index);
}

JNIEXPORT void API JNICALL ${jniPrefix}setU64_1${uniqueId}
  (JNIEnv *, jobject, Wrapper_${uniqueId} *handle, int id, uint64_t value){
  handle->signalAccess[id]->setU64(value);
}

JNIEXPORT void API JNICALL ${jniPrefix}setU64mem_1${uniqueId}
  (JNIEnv *, jobject, Wrapper_${uniqueId} *handle, int id, uint64_t value, uint64_t index){
  handle->signalAccess[id]->setU64_mem(value, index);
}

JNIEXPORT void API JNICALL ${jniPrefix}deleteHandle_1${uniqueId}
  (JNIEnv *env, jobject, Wrapper_${uniqueId} * handle){

#ifdef USE_VERILATED_CONTEXT
  VerilatedContext *contextp = handle->contextp;
#endif
  V${config.toplevelName} *top = handle->top;
  delete handle;
  delete top;
#ifdef USE_VERILATED_CONTEXT
  delete contextp;
#endif

#ifdef EMIT_THREAD_LOCAL_HANDLE
  simHandle${uniqueId} = nullptr;
#endif
}

JNIEXPORT void API JNICALL ${jniPrefix}getAU8_1${uniqueId}
  (JNIEnv * env, jobject obj, Wrapper_${uniqueId} * handle, jint id, jbyteArray value){
  handle->signalAccess[id]->getAU8(env, value);
}

JNIEXPORT void API JNICALL ${jniPrefix}getAU8mem_1${uniqueId}
  (JNIEnv * env, jobject obj, Wrapper_${uniqueId} * handle, jint id, jbyteArray value, uint64_t index){
  handle->signalAccess[id]->getAU8_mem(env, value, index);
}

JNIEXPORT void API JNICALL ${jniPrefix}setAU8_1${uniqueId}
  (JNIEnv * env, jobject obj, Wrapper_${uniqueId} * handle, jint id, jbyteArray value, jint length){
  handle->signalAccess[id]->setAU8(env, value, length);
}

JNIEXPORT void API JNICALL ${jniPrefix}setAU8mem_1${uniqueId}
  (JNIEnv * env, jobject obj, Wrapper_${uniqueId} * handle, jint id, jbyteArray value, jint length, uint64_t index){
  handle->signalAccess[id]->setAU8_mem(env, value, length, index);
}

JNIEXPORT void API JNICALL ${jniPrefix}enableWave_1${uniqueId}
  (JNIEnv *, jobject, Wrapper_${uniqueId} * handle){
  handle->waveEnabled = true;
}

JNIEXPORT void API JNICALL ${jniPrefix}disableWave_1${uniqueId}
  (JNIEnv *, jobject, Wrapper_${uniqueId} * handle){
  handle->waveEnabled = false;
}

JNIEXPORT void API JNICALL ${jniPrefix}commandArgs_1${uniqueId}
  (JNIEnv * env, jobject obj, Wrapper_${uniqueId} * handle, jobjectArray args){
  const char *empty[] = { nullptr };
  const char **argv = nullptr;
  int argc = 0;
  jint size = 0;
  if(args) {
    size = env->GetArrayLength(args);
    if(size > 0) {
      argv = new const char*[size+1];
      for(jint i = 0; i < size; i++) {
        jobject ele = env->GetObjectArrayElement(args, i);
        if(ele)
          argv[argc++] = env->GetStringUTFChars((jstring)ele, 0);
      }
      argv[argc] = nullptr;
    }
  }

  VERILATED_CONTEXTP(handle->contextp)commandArgs(argc, (char**)(argc>0) ? argv : empty);

  if(argv) {
    for(jint i = 0; i < size; i++) {
      jobject ele = env->GetObjectArrayElement(args, i);
      env->ReleaseStringUTFChars((jstring)ele, argv[i]);
    }
    delete[] argv;
  }
}

JNIEXPORT void API JNICALL ${jniPrefix}finish_1${uniqueId}
  (JNIEnv * env, jobject obj, Wrapper_${uniqueId} * handle){
  VL_FINISH_MT("", 0, "*JNI*");
}

JNIEXPORT void API JNICALL ${jniPrefix}topFinal_1${uniqueId}
  (JNIEnv * env, jobject obj, Wrapper_${uniqueId} * handle){
  handle->top->final();
}

#ifdef __cplusplus
}
#endif
     """

    var dirtyCache = false;

    val wrapperCppFile = new File(wrapperCppPath)
    val wrapperCppTmpFile = new File(wrapperCppPath + ".tmp")

    val outFile = new java.io.FileWriter(wrapperCppTmpFile)
    outFile.write(wrapperString)
    outFile.flush()
    outFile.close()

    if(wrapperCppFile.isFile && FileUtils.contentEquals(wrapperCppFile, wrapperCppTmpFile)) {
      println(s"[info]  Generated ${wrapperCppFile.getName} no content change, preserving timestamp for incremental build")
      if(!wrapperCppTmpFile.delete())
        throw new IOException(s"unlink(${wrapperCppTmpFile}): failed")
    } else {
      if(wrapperCppFile.isFile && !wrapperCppFile.renameTo(new File(wrapperCppFile.getAbsolutePath + ".old")))
        throw new IOException(s"rename(${wrapperCppFile}): failed")
      println(s"[info]  Generated ${wrapperCppFile.getName} changed, updating")
      if(!wrapperCppTmpFile.renameTo(wrapperCppFile))
        throw new IOException(s"rename(${wrapperCppTmpFile}): failed")
      dirtyCache = true;
    }

    val exportMapString =
      s"""CODEABI_1.0 {
         |    global: $jniPrefix*;
         |    local: *;
         |};""".stripMargin

    val exportmapFile = new File(s"${workspacePath}/${workspaceName}/libcode.version")
    val exportmapTmpFile = new File(exportmapFile.getAbsolutePath + ".tmp")

    val writer = new java.io.FileWriter(exportmapTmpFile)
    writer.write(exportMapString)
    writer.flush()
    writer.close()

    if (exportmapFile.isFile && FileUtils.contentEquals(exportmapFile, exportmapTmpFile)) {
      println(s"[info]  Generated ${exportmapFile.getName} no content change, preserving timestamp for incremental build")
      if(!exportmapTmpFile.delete())
        throw new IOException(s"unlink(${exportmapTmpFile}): failed")
    } else {
      if(exportmapFile.isFile && !exportmapFile.renameTo(new File(exportmapFile.getAbsolutePath + ".old")))
        throw new IOException(s"rename(${exportmapFile}): failed")
      println(s"[info]  Generated ${exportmapFile.getName} changed, updating")
      if(!exportmapTmpFile.renameTo(exportmapFile))
        throw new IOException(s"rename(${exportmapTmpFile}): failed")
      dirtyCache = true;
    }

    dirtyCache;
  }

  class Logger(verbose: Boolean = true) extends ProcessLogger {
    override def err(s: => String): Unit = {
      if (s == null || s.isBlank)
        return
      if(verbose || !s.startsWith("ar: creating ")) println(s) }
    override def out(s: => String): Unit = {
      if(s == null || s.isBlank)
        return
      if(verbose) println(s)
    }
    override def buffer[T](f: => T) = f
  }

  val verilatorBinFilename = if(System.getenv("VERILATOR_BIN") != null) System.getenv("VERILATOR_BIN")
                            else (if(isWindows) "verilator_bin.exe" else "verilator")

//     VL_THREADED
  def resolveVerilatorVersion(): (String, BigDecimal) = {
    var versionDeci: BigDecimal = null
    var versionString: String = null
    val fileName = "verilator_version.txt"
    val verilatorVersionCacheFile = if(cachePath != null) new File(cachePath, fileName) else null
    if (verilatorVersionCacheFile != null && cacheEnabled) {
      var read: FileInputStream = null
      try {
        read = new FileInputStream(verilatorVersionCacheFile)
        versionString = new String(read.readAllBytes(), StandardCharsets.UTF_8)
        read.close()
      } catch { // best effort as fallback next will just invoke process
        case _: FileNotFoundException => {}
        case _: SecurityException => {}
      } finally try {
        if (read != null) read.close()
      } catch {
        case _: IOException => {}
      }
    }
    var haveFreshData = false
    if (versionString == null) { // invoke process
      val verilatorVersionProcess = Process(Seq(verilatorBinFilename, "--version"), new File(workspacePath))
      versionString = verilatorVersionProcess.lineStream.mkString("\n") // blocks and throws an exception if exit status != 0
      haveFreshData = true
    }
    versionDeci = BigDecimal(versionString.split(" ")(1))
    if (verilatorVersionCacheFile != null && cacheEnabled && haveFreshData) {
      var writer: java.io.FileWriter = null
      var verilatorVersionCacheTmpFile: File = null
      try {
        val cacheDir = new File(cachePath)  // cachePath!=null when verilatorVersionCacheFile != null
        cacheDir.mkdirs()
        verilatorVersionCacheTmpFile = File.createTempFile(fileName, ".tmp", cacheDir)
        writer = new java.io.FileWriter(verilatorVersionCacheTmpFile /*, StandardCharsets.UTF_8*/) // JDK11+
        writer.write(versionString)
        writer.flush()
        writer.close()
        // this will fail on windows if the target exists, which means it won't update it
        if(verilatorVersionCacheTmpFile.renameTo(verilatorVersionCacheFile))  verilatorVersionCacheTmpFile = null
      } catch { // best effort as fallback next will just invoke process
        case e: FileNotFoundException => println(s"${e.getClass.getName}: ${e.getMessage}")
        case e: SecurityException => println(s"${e.getClass.getName}: ${e.getMessage}")
      } finally try {
        if (verilatorVersionCacheTmpFile != null) verilatorVersionCacheTmpFile.delete()
        if (writer != null) writer.close()
      } catch {
        case _: IOException => {}
      }
    }
    (versionString, versionDeci)
  }

  // The -cc line contains generated paths /path/project/tmp/job_1/TOP.v that are unique for
  //  this potentially parallel execution, the data content of the RTL sources is already included
  //  in the hash already, so the exact path is unimportant, this will allow reuse of the objs
  //  for the same TOP.v file by the cache.
  val PATTERN_1 = Pattern.compile("^\\s*-cc\\s+.*$", Pattern.MULTILINE)

  // This allows local shell variables in the form ^_varName= to be omitted from cache hash
  //  generation allowing an easy mechanism to exclude other auto-generated data.
  val PATTERN_2 = Pattern.compile("^_[A-Za-z0-9_]+=.*$", Pattern.MULTILINE)

  def transformScriptForCacheHash(s: String): String = {
    val ss = PATTERN_1.matcher(s).replaceAll("")
    PATTERN_2.matcher(ss).replaceAll("")
  }

  def compileVerilator(): Unit = {
    val java_home = System.getProperty("java.home")
    assert(java_home != "" && java_home != null, "JAVA_HOME need to be set")
    val jdk = java_home.replace("/jre","").replace("\\jre","")

    val jniIncludes = ArrayBuffer[String]()
    if (isWindows) {
      new File(s"${workspacePath}\\${workspaceName}").mkdirs()
      FileUtils.copyDirectory(new File(s"$jdk\\include"), new File(s"${workspacePath}\\${workspaceName}\\jniIncludes"))
      jniIncludes += "jniIncludes"
    } else {
      jniIncludes += jdk + File.separatorChar + "include"
    }

    {
      var found = false
      val jniIncludeFile = new File(jdk, "include")
      var osName = System.getProperty("os.name")
      if(osName != null) {
        osName = osName.toLowerCase
        val jniIncludeOsNameFile = new File(jniIncludeFile, osName)
        if (jniIncludeOsNameFile.isDirectory) {
          jniIncludes += jniIncludeOsNameFile.getAbsolutePath // linux || freebsd
          found = true
        }
      }
      if(!found) {
        // Can include what we see exists
        Seq("darwin", "win32", "linux", "freebsd").foreach(dirName => {
          // darwin (os.name="Mac OS X" || os.name="Darwin") || win32 (os.name="Windows 10" || os.name="Windows ??")
          val jniIncludeOsWellKnownFile = new File(jniIncludeFile, dirName)
          if (jniIncludeOsWellKnownFile.isDirectory) {
            jniIncludes += jniIncludeOsWellKnownFile.getAbsolutePath
            found = true
          }
        })
      }
      if(!found) {
        jniIncludes += jdk + File.separatorChar + "include" + File.separatorChar + (if(isWindows) "win32" else (if(isMac) "darwin" else "linux")) // fallback to old method
      }
    }

    def shellQuoteAndEscape(s: String): String = {
      // A lot of escaping going on here, half of them are to get out of Scala and into the file, we want in the
      //  verilatorScript.sh: -I"Directory\\ Name" which the shell will interpret and pass to verilator, which
      //  will build the makefile to get -IDirectory\ Name
      "\"" + s.replace(" ", "\\\\ ") + "\""
    }

    val arch = System.getProperty("os.arch")
    val flags   = if(isMac) List("-dynamiclib") else (if(arch == "arm" || arch == "aarch64" || arch == "loongarch64") List("-fPIC", "-shared", "-Wno-attributes") else List("-fPIC", "-m64", "-shared", "-Wno-attributes"))

    val waveArgs = format match {
      case WaveFormat.FST =>  "-CFLAGS -DTRACE --trace-fst"
      case WaveFormat.VCD =>  "-CFLAGS -DTRACE --trace"
      case WaveFormat.NONE => ""
      // Other formats are not supported by Verilator
      case _ => ???
    }

    val covArgs = config.withCoverage match {
      case true =>  "-CFLAGS -DCOVERAGE --coverage"
      case false => ""
    }

    val timeUnit = config.timeUnit match {
      case null => null
      case s: String => s.replace(" ", "")
    }
    val timePrecision = config.timePrecision match {
      case null => null
      case s: String => s.replace(" ", "")
    }
    val timeScaleArgs = (timeUnit, timePrecision) match {
      case (null, null) => ""
      case (_, null) => s"--timescale-override ${timeUnit}/"
      case (null, _) => "--timescale-override \" /" + s"${timePrecision}" + "\""  // extra space is a verilator quirk in at least 5.006)
      case (_, _) => s"--timescale-override ${timeUnit}/${timePrecision}"
    }

    val rtlIncludeDirsArgs = config.rtlIncludeDirs.map(e => s"-I${new File(e).getAbsolutePath}")
      .map('"' + _.replace("\\","/") + '"').mkString(" ")

    val (verilatorVersion, verilatorVersionDeci) = resolveVerilatorVersion()

    val cflags = ArrayBuffer[String]()
    if(verilatorVersionDeci >= BigDecimal("4.200")) {
      // From what I could understand from testing, our wrapper and the verilated*.o need to be built
      // with these same defines so they are all configured to same symbols for 'time' source linkage.
      // The more recent Verilator versions reduce the amount of global state so that multiple simulations
      // each potentially with a different sense of time can run in the same process space.
      // These defines reduce legacy methods that do not facilitate that.
      cflags += "-DVL_TIME_CONTEXT";
      cflags += "-DVL_TIME_STAMP64";
    }

    // when changing the verilator script, the hash generation (below) must also be updated
    val verilatorScript = s"""set -e ;
       |# Script lines starting with an underscore, or -cc do not form part of the cache hash
       |_workspacePath="${workspacePath}"
       |_workspaceName="${workspaceName}"
       |if [ "X$$VERILATOR_VERBOSE" != "X" ]; then
       | cat "verilatorScript.sh" 2>/dev/null || true;
       | fi;
       |${verilatorBinFilename}
       | ${flags.map("-CFLAGS " + _).mkString(" ")}
       | ${flags.map("-LDFLAGS " + _).mkString(" ")}
       | ${jniIncludes.map("-CFLAGS -I" + shellQuoteAndEscape(_)).mkString(" ")}
       | -CFLAGS -fvisibility=hidden
       | -LDFLAGS -fvisibility=hidden
       | -CFLAGS -std=c++11
       | -LDFLAGS -std=c++11
       | --autoflush  
       | ${cflags.map("-CFLAGS " + _).mkString(" ")}
       | --output-split 5000
       | --output-split-cfuncs 500
       | --output-split-ctrace 500
       | -Wno-WIDTH -Wno-UNOPTFLAT -Wno-CMPCONST -Wno-UNSIGNED
       | --x-assign unique
       | --x-initial-edge
       | --trace-depth ${config.waveDepth}
       | -O3
       | -CFLAGS -O${config.optimisationLevel}
       | $waveArgs
       | $covArgs
       | $timeScaleArgs
       | --Mdir ${workspaceName}
       | --top-module ${config.toplevelName}
       | $rtlIncludeDirsArgs
       | -cc ${config.rtlSourcesPaths.filter(e => e.endsWith(".v") || 
                                                  e.endsWith(".sv") || 
                                                  e.endsWith(".h"))
                                     .map(new File(_).getAbsolutePath)
                                     .map('"' + _.replace("\\","/") + '"')
                                     .mkString(" ")}
       | --exe $workspaceName/$wrapperCppName
       | ${config.simulatorFlags.mkString(" ")}
       | "$$@" > verilatorScript.out 2> verilatorScript.err;
       |rc=$$?;
       |if [ "X$$VERILATOR_VERBOSE" != "X" ]; then
       | [ -f "verilatorScript.out" ] && [ ! -s "verilatorScript.out" ] && rm -f "verilatorScript.out" || true;
       | [ -f "verilatorScript.err" ] && [ ! -s "verilatorScript.out" ] && rm -f "verilatorScript.err" || true;
       | echo "$$0: $${_workspacePath}/$${_workspaceName} EXIT $$rc";
       | fi;
       |exit $$rc;
       | """.stripMargin.replace("\r", "").replace("\n ", " \\\n ")



    val workspaceDir = new File(s"${workspacePath}/${workspaceName}")
    var workspaceCacheDir: File = null
    var hashCacheDir: File = null

    if (cacheEnabled) {
      // calculate hash of verilator version+options and source file contents

      val md = MessageDigest.getInstance("SHA-1")

      md.update(cachePath.getBytes())
      md.update(0.toByte)
      md.update(verilatorBinFilename.getBytes())
      md.update(0.toByte)
      md.update(verilatorVersion.getBytes())
      md.update(0.toByte)
      md.update(transformScriptForCacheHash(verilatorScript).getBytes(StandardCharsets.UTF_8))
      md.update(0.toByte)

      def hashFile(md: MessageDigest, file: File) = {
        val bis = new BufferedInputStream(new FileInputStream(file))
        val buf = new Array[Byte](1024)

        Iterator.continually(bis.read(buf, 0, buf.length))
          .takeWhile(_ >= 0)
          .foreach(md.update(buf, 0, _))

        bis.close()
      }

      config.rtlIncludeDirs.foreach { dirname =>
        FileUtils.listFiles(new File(dirname), null, true).asScala.foreach { file =>
          hashFile(md, file)
          md.update(0.toByte)
        }

        md.update(0.toByte)
      }

      config.rtlSourcesPaths.foreach { filename =>
        hashFile(md, new File(filename))
        md.update(0.toByte)
      }

      val hash = md.digest().map(x => (x & 0xFF).toHexString.padTo(2, '0')).mkString("")
      workspaceCacheDir = new File(s"${cachePath}/${hash}/${workspaceName}")
      hashCacheDir = new File(s"${cachePath}/${hash}")

      cacheGlobalSynchronized {
        // remove old cache entries

        val cacheDir = new File(cachePath)
        if (cacheDir.isDirectory()) {
          if (maxCacheEntries > 0) {
            val cacheEntriesArr = cacheDir.listFiles()
              .filter(_.isDirectory())
              .sortWith(_.lastModified() < _.lastModified())

            val cacheEntries = cacheEntriesArr.toBuffer
            val cacheEntryFound = workspaceCacheDir.isDirectory()

            while (cacheEntries.length > maxCacheEntries || (!cacheEntryFound && cacheEntries.length >= maxCacheEntries)) {
              if (cacheEntries(0).getCanonicalPath() != hashCacheDir.getCanonicalPath()) {
                cacheSynchronized(cacheEntries(0)) {
                  FileUtils.deleteQuietly(cacheEntries(0))
                }
              }

              cacheEntries.remove(0)
            }
          }
        }
      }
    }

    cacheSynchronized(hashCacheDir) {
      var useCache = false

      if (cacheEnabled) {
        if (workspaceCacheDir.isDirectory()) {
          println("[info] Found cached verilator binaries")
          useCache = true
        }
      }

      var lastTime = System.currentTimeMillis()

      def bench(msg : String): Unit ={
        val newTime = System.currentTimeMillis()
        val sec = (newTime-lastTime)*1e-3
        println(msg + " " + sec)
        lastTime = newTime
      }

      val verilatorScriptFile = new PrintWriter(new File(workspacePath + "/verilatorScript.sh"))
      verilatorScriptFile.write(verilatorScript)
      verilatorScriptFile.close

      // invoke verilator or copy cached files depending on whether cache is not used or used
      if (!useCache) {
        val shCommand = if(isWindows) "sh.exe" else "sh"
        assert(Process(Seq(shCommand, "verilatorScript.sh"),
                       new File(workspacePath)).! (new Logger()) == 0, "Verilator invocation failed")
      } else {
        FileUtils.copyDirectory(workspaceCacheDir, workspaceDir)
      }

      // #!/bin/sh
      //#
      //makeBinFilename="make.exe"
      //threadCount="1"
      //workspacePath="/path/to/foo/blah/project/simWorkspace/MyComponent"
      //workspaceName="verilator"
      //configToplevelName="Toplevel"
      //
      //${makeBinFilename} -j$threadCount VM_PARALLEL_BUILDS=1 \
      //	-C "${workspacePath}/${workspaceName}" \
      //	-f "V${configToplevelName}.mk" \
      //	"V${configToplevelName}" \
      //	"CURDIR=${workspacePath}/${workspaceName}" \
      //	$@

      val makeBinFilename = if (System.getenv("MAKE") != null) System.getenv("MAKE")
                            else (if (isWindows) "make.exe" else "make")

      val dirtyCache = genWrapperCpp(verilatorVersionDeci)
      val threadCount = SimManager.cpuCount
      if (!useCache) {
        assert(s"${makeBinFilename} -j$threadCount VM_PARALLEL_BUILDS=1 -C ${workspacePath}/${workspaceName} -f V${config.toplevelName}.mk V${config.toplevelName} CURDIR=${workspacePath}/${workspaceName}".!  (new Logger()) == 0, "Verilator C++ model compilation failed")
      } else {
        // do not remake Vtoplevel__ALL.a
        assert(s"${makeBinFilename} -j$threadCount VM_PARALLEL_BUILDS=1 -C ${workspacePath}/${workspaceName} -f V${config.toplevelName}.mk -o V${config.toplevelName}__ALL.a V${config.toplevelName} CURDIR=${workspacePath}/${workspaceName}".!  (new Logger()) == 0, "Verilator C++ model compilation failed")
      }

      if (cacheEnabled) {
        // update cache

        if (!useCache || dirtyCache) {
          // ideally want to sync file existence (delete from cacheDir files not now existing after make in workspaceDir)
          // and to overwrite files with newer timestamps (overwrite cacheDir copy with updated workspaceDir copy when timestamp is newer)
          FileUtils.deleteQuietly(workspaceCacheDir)

          // copy only needed files to save disk space
          FileUtils.copyDirectory(workspaceDir, workspaceCacheDir, new FileFilter() {
            def accept(file: File): Boolean = file.getName() == s"V${config.toplevelName}__ALL.a" || file.getName().endsWith(".mk") || file.getName().endsWith(".h") ||
              (file.getName.endsWith(".o") &&
                (file.getName.startsWith(s"V${config.toplevelName}__spinalWrapper") ||
                file.getName.startsWith("verilated"))
                ) ||
              file.getName == s"V${config.toplevelName}" ||
              file.getName == s"V${config.toplevelName}.exe" ||
              file.getName.equals(wrapperCppName) ||
              file.getName.equals("libcode.version")
          })
        }

        FileUtils.touch(hashCacheDir)
      }

      FileUtils.copyFile(new File(s"${workspacePath}/${workspaceName}/V${config.toplevelName}${if (isWindows) ".exe" else ""}"), new File(s"${workspacePath}/${workspaceName}/${workspaceName}_$uniqueId.${if (isWindows) "dll" else (if (isMac) "dylib" else "so")}"))
    }
  }

  def compileJava(): Unit = {
    val verilatorNativeImplCode =
      s"""package wrapper_${workspaceName};
         |import spinal.sim.IVerilatorNative;
         |
         |public class VerilatorNative implements IVerilatorNative {
         |    public long newHandle(String name, int seed) { return newHandle_${uniqueId}(name, seed);}
         |    public boolean eval(long handle) { return eval_${uniqueId}(handle);}
         |    public void rand_seed(long handle, int seed) { randSeed_${uniqueId}(handle, seed);}
         |    public void rand_reset(long handle, int value) { randReset_${uniqueId}(handle, value);}
         |    public int get_time_unit(long handle) { return getTimeUnit_${uniqueId}(handle);}
         |    public int get_time_precision(long handle) { return getTimePrecision_${uniqueId}(handle);}
         |    public void sleep(long handle, long cycles) { sleep_${uniqueId}(handle, cycles);}
         |    public long getU64(long handle, int id) { return getU64_${uniqueId}(handle, id);}
         |    public long getU64_mem(long handle, int id, long index) { return getU64mem_${uniqueId}(handle, id, index);}
         |    public void setU64(long handle, int id, long value) { setU64_${uniqueId}(handle, id, value);}
         |    public void setU64_mem(long handle, int id, long value, long index) { setU64mem_${uniqueId}(handle, id, value, index);}
         |    public void getAU8(long handle, int id, byte[] value) { getAU8_${uniqueId}(handle, id, value);}
         |    public void getAU8_mem(long handle, int id, byte[] value, long index) { getAU8mem_${uniqueId}(handle, id, value, index);}
         |    public void setAU8(long handle, int id, byte[] value, int length) { setAU8_${uniqueId}(handle, id, value, length);}
         |    public void setAU8_mem(long handle, int id, byte[] value, int length, long index) { setAU8mem_${uniqueId}(handle, id, value, length, index);}
         |    public void deleteHandle(long handle) { deleteHandle_${uniqueId}(handle);}
         |    public void enableWave(long handle) { enableWave_${uniqueId}(handle);}
         |    public void disableWave(long handle) { disableWave_${uniqueId}(handle);}
         |    public void commandArgs(long handle, String[] args) { commandArgs_${uniqueId}(handle, args);}
         |    public void finish(long handle) { finish_${uniqueId}(handle);}
         |    public void topFinal(long handle) { topFinal_${uniqueId}(handle);}
         |
         |
         |    public native long newHandle_${uniqueId}(String name, int seed);
         |    public native boolean eval_${uniqueId}(long handle);
         |    public native void randSeed_${uniqueId}(long handle, int seed);
         |    public native void randReset_${uniqueId}(long handle, int value);
         |    public native int getTimeUnit_${uniqueId}(long handle);
         |    public native int getTimePrecision_${uniqueId}(long handle);
         |    public native void sleep_${uniqueId}(long handle, long cycles);
         |    public native long getU64_${uniqueId}(long handle, int id);
         |    public native long getU64mem_${uniqueId}(long handle, int id, long index);
         |    public native void setU64_${uniqueId}(long handle, int id, long value);
         |    public native void setU64mem_${uniqueId}(long handle, int id, long value, long index);
         |    public native void getAU8_${uniqueId}(long handle, int id, byte[] value);
         |    public native void getAU8mem_${uniqueId}(long handle, int id, byte[] value, long index);
         |    public native void setAU8_${uniqueId}(long handle, int id, byte[] value, int length);
         |    public native void setAU8mem_${uniqueId}(long handle, int id, byte[] value, int length, long index);
         |    public native void deleteHandle_${uniqueId}(long handle);
         |    public native void enableWave_${uniqueId}(long handle);
         |    public native void disableWave_${uniqueId}(long handle);
         |    public native void commandArgs_${uniqueId}(long handle, String[] args);
         |    public native void finish_${uniqueId}(long handle);
         |    public native void topFinal_${uniqueId}(long handle);
         |
         |    static{
         |      System.load("${new File(s"${workspacePath}/${workspaceName}").getAbsolutePath.replace("\\","\\\\")}/${workspaceName}_$uniqueId.${if(isWindows) "dll" else (if(isMac) "dylib" else "so")}");
         |    }
         |}
       """.stripMargin

    val verilatorNativeImplFile = new DynamicCompiler.InMemoryJavaFileObject(s"wrapper_${workspaceName}.VerilatorNative", verilatorNativeImplCode)
    import collection.JavaConverters._
    DynamicCompiler.compile(List[JavaFileObject](verilatorNativeImplFile).asJava, s"${workspacePath}/${workspaceName}")
  }

  def checks(): Unit ={
    if(System.getProperty("java.class.path").contains("sbt-launch.jar")){
      System.err.println("""[Error] It look like you are running the simulation with SBT without having the SBT 'fork := true' configuration.\n  Add it in the build.sbt file to fix this issue, see https://github.com/SpinalHDL/SpinalTemplateSbt/blob/master/build.sbt""")
      throw new Exception()
    }
  }

  clean()
  checks()
  compileVerilator()
  compileJava()

  val nativeImpl = DynamicCompiler.getClass(s"wrapper_${workspaceName}.VerilatorNative", s"${workspacePath}/${workspaceName}")
  val nativeInstance: IVerilatorNative = nativeImpl.newInstance().asInstanceOf[IVerilatorNative]

  def instanciate(name: String, seed: Int) = nativeInstance.newHandle(name, seed)

  override def isBufferedWrite: Boolean = false
}

