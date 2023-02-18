package spinal.sim

object SimVerilator{
  final val bigInt32b = BigInt("FFFFFFFFFFFFFFFF",16)
}

class SimVerilator(backend : VerilatorBackend, 
                   handle : Long) extends SimRaw(){
  
  override def getIntMem(signal : Signal,
                      index : Long) : Int = {
    assert(signal.id != -1, "You can't access this signal in the simulation, as it isn't public")
    signal.dataType.raw64ToInt(backend.nativeInstance.getU64_mem(handle, 
                                                                 signal.id,
                                                                 index), signal : Signal)
  }

  def setIntMem(signal : Signal,
                 value : Int,
                 index : Long) : Unit = {
    setLongMem(signal, value, index)
  }

  override def getLongMem(signal : Signal,
                          index : Long) : Long = {
    assert(signal.id != -1, "You can't access this signal in the simulation, as it isn't public")
    signal.dataType.raw64ToLong(backend.nativeInstance.getU64_mem(handle, 
                                                                  signal.id, 
                                                                  index), signal : Signal)
  }
  override def setLongMem(signal : Signal, 
                          value : Long,
                          index : Long) : Unit = {
    assert(signal.id != -1, "You can't access this signal in the simulation, as it isn't public")
    backend.nativeInstance.setU64_mem(handle, 
                                      signal.id, 
                                      signal.dataType.longToRaw64(value, signal : Signal),
                                      index)
  }

  override def getBigIntMem(signal: Signal,
                         index : Long) = {
    if(signal.dataType.width < 64 || (signal.dataType.width == 64 && signal.dataType.isInstanceOf[SIntDataType])) {
      getLongMem(signal, index)
    } else if(signal.dataType.width == 64){
      val rawValue = backend.nativeInstance.getU64_mem(handle, 
                                                       signal.id,
                                                       index)
      if(rawValue >= 0 ) {
        BigInt(rawValue)
      }else{
        BigInt(rawValue + 1) + SimVerilator.bigInt32b
      }
    } else {
      if(signal.dataType.isInstanceOf[SIntDataType]){
        val array = new Array[Byte]((signal.dataType.width+31)/32*4)
        backend.nativeInstance.getAU8_mem(handle, 
                                          signal.id, 
                                          array,
                                          index)
        BigInt(array)
      }else{
        val array = new Array[Byte]((signal.dataType.width+31)/32*4 + 1)
        backend.nativeInstance.getAU8_mem(handle, 
                                          signal.id, 
                                          array,
                                          index)
        array(0) = 0
        BigInt(array)
      }
    }
  }

  override def setBigIntMem(signal : Signal, 
                         value : BigInt,
                         index : Long): Unit = {
    val valueBitLength = value.bitLength + (if(value.signum == -1) 1 else 0)
    if(valueBitLength <= 63) {
      setLongMem(signal, 
                 value.toLong, 
                 index)
    } else if(valueBitLength == 64 && signal.dataType.width == 64) {
      assert(signal.id != -1, "You can't access this signal in the simulation, as it isn't public")
      val valueLong = value.toLong
      signal.dataType.checkIs64(valueLong, signal : Signal)
      backend.nativeInstance.setU64_mem(handle, signal.id, valueLong, index)
    } else {
      signal.dataType.checkBigIntRange(value, signal)
      val array = value.toByteArray
      backend.nativeInstance.setAU8_mem(handle, 
                                        signal.id, 
                                        array, 
                                        array.length, 
                                        index)
    }
  }

  override def getInt(signal : Signal) : Int = { getIntMem(signal, 0) }
  def setInt(signal : Signal, value : Int) : Unit = { setLongMem(signal, value, 0) }
  override def getLong(signal : Signal) : Long = { getLongMem(signal, 0) }
  override def setLong(signal : Signal, value : Long) : Unit = { setLongMem(signal, value, 0) }
  override def getBigInt(signal : Signal) : BigInt = { getBigIntMem(signal, 0) }
  override def setBigInt(signal : Signal, value : BigInt) : Unit = { setBigIntMem(signal, value, 0) }

  def tryRandSeed(seed : Int) : Boolean = {
    try {
      randSeed(seed)
      true
    } catch {
      case _ : UnsupportedOperationException => false
    }
  }
  def randSeed(seed : Int) : Unit = backend.nativeInstance.rand_seed(handle, seed)
  def randReset(value : Int) : Unit = backend.nativeInstance.rand_reset(handle, value)
  override def eval() : Boolean = backend.nativeInstance.eval(handle)
  def getTimeUnit(): Int = backend.nativeInstance.get_time_unit(handle)
  override def getTimePrecision(): Int = backend.nativeInstance.get_time_precision(handle)
  override def sleep(cycles : Long) = backend.nativeInstance.sleep(handle, cycles)
  override def end() = backend.nativeInstance.deleteHandle(handle)
  override def isBufferedWrite : Boolean = false
  override def enableWave(): Unit = backend.nativeInstance.enableWave(handle)
  override def disableWave(): Unit =  backend.nativeInstance.disableWave(handle)
  def commandArgs(args: Array[String]): Unit = backend.nativeInstance.commandArgs(handle, args)
  def finish(): Unit = backend.nativeInstance.finish(handle)
  def topFinal(): Unit = backend.nativeInstance.topFinal(handle)
}

class SimVerilatorProxy(backend: VerilatorBackend,
                        handle: Long) extends SimVerilator(backend, handle) {

  val verbose = false;
  override def getIntMem(signal: Signal,
                         index: Long): Int = {
    val rc = super.getIntMem(signal, index)
    if(verbose)
      println(s"SimVerilator#getIntMem($signal, $index) = $rc")
    rc
  }

  override def setIntMem(signal: Signal,
                value: Int,
                index: Long): Unit = {
    if(verbose)
      println(s"SimVerilator#setIntMem($signal, $value, $index)")
    super.setIntMem(signal, value, index)
  }

  override def getLongMem(signal: Signal,
                          index: Long): Long = {
    val rc = super.getLongMem(signal, index)
    if (verbose)
      println(s"SimVerilator#getLongMem($signal, $index) = $rc")
    rc
  }

  override def setLongMem(signal: Signal,
                          value: Long,
                          index: Long): Unit = {
    if (verbose)
      println(s"SimVerilator#setLongMem($signal, $value, $index)")
    super.setLongMem(signal, value, index)
  }

  override def getBigIntMem(signal: Signal,
                            index: Long) = {
    val rc = super.getBigIntMem(signal, index)
    if (verbose)
      println(s"SimVerilator#getBigIntMem($signal, $index) = $rc")
    rc
  }

  override def setBigIntMem(signal: Signal,
                            value: BigInt,
                            index: Long): Unit = {
    if (verbose)
      println(s"SimVerilator#setBigIntMem($signal, $value, $index)")
    super.setBigIntMem(signal, value, index)
  }

  override def getInt(signal: Signal): Int = {
    val rc = super.getInt(signal)
    if (verbose)
      println(s"SimVerilator#getInt($signal) = $rc")
    rc
  }

  override def setInt(signal: Signal, value: Int): Unit = {
    if (verbose)
      println(s"SimVerilator#setInt($signal, $value)")
    super.setInt(signal, value)
  }

  override def getLong(signal: Signal): Long = {
    val rc = super.getLong(signal)
    if (verbose)
      println(s"SimVerilator#getLong($signal) = $rc")
    rc
  }

  override def setLong(signal: Signal, value: Long): Unit = {
    if (verbose)
      println(s"SimVerilator#setLong($signal, $value)")
    super.setLong(signal, value)
  }

  override def getBigInt(signal: Signal): BigInt = {
    val rc = super.getBigInt(signal)
    if (verbose)
      println(s"SimVerilator#getBigInt($signal) = $rc")
    rc
  }

  override def setBigInt(signal: Signal, value: BigInt): Unit = {
    if (verbose)
      println(s"SimVerilator#setBigInt($signal, $value)")
    super.setBigInt(signal, value)
  }

  override def eval(): Boolean = {
    val rc = super.eval()
    if (verbose)
      println(s"SimVerilator#eval() = $rc")
    rc
  }

  override def tryRandSeed(seed: Int): Boolean = {
    val rc = super.tryRandSeed(seed)
    if (verbose)
      println(s"SimVerilator#tryRandSeed($seed) = $rc")
    rc
  }

  override def randSeed(seed: Int): Unit = {
    if (verbose)
      println(s"SimVerilator#randSeed($seed)")
    super.randSeed(seed)
  }

  override def randReset(value: Int): Unit = {
    if (verbose)
      println(s"SimVerilator#randReset($value)")
    super.randReset(value)
  }

  override def getTimeUnit(): Int = {
    val rc = super.getTimeUnit()
    if (verbose)
      println(s"SimVerilator#getTimeUnit() = $rc")
    rc
  }

  override def getTimePrecision(): Int = {
    val rc = super.getTimePrecision()
    if (verbose)
      println(s"SimVerilator#getTimePrecision() = $rc")
    rc
  }

  override def sleep(cycles: Long) = {
    if (verbose)
      println(s"SimVerilator#sleep($cycles)")
    super.sleep(cycles)
  }

  override def end() = {
    if (verbose)
      println(s"SimVerilator#end()")
    super.end()
  }

  override def isBufferedWrite: Boolean = {
    val rc = super.isBufferedWrite
    if (verbose)
      println(s"SimVerilator#isBufferedWrite() = $rc")
    rc
  }

  override def enableWave(): Unit = {
    if (verbose)
      println(s"SimVerilator#enableWave()")
    super.enableWave()
  }

  override def disableWave(): Unit = {
    if (verbose)
      println(s"SimVerilator#disableWave()")
    super.disableWave()
  }

  override def commandArgs(args: Array[String]): Unit = {
    if (verbose)
      println(s"SimVerilator#commandArgs(${args.mkString("\"", "\", \"", "\"")})")
    super.commandArgs(args)
  }

  override def finish(): Unit = {
    if (verbose)
      println(s"SimVerilator#finish()")
    super.finish()
  }

  override def topFinal(): Unit = {
    if (verbose)
      println(s"SimVerilator#topFinal()")
    super.topFinal()
  }

}
