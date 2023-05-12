package spinal.tester.scalatest

import org.scalatest.funsuite.AnyFunSuite
import spinal.core._

import scala.language.postfixOps


class BBA(val cd : ClockDomain) extends BlackBox{
  val i = in(Bool)
}

class BBB(val cd : ClockDomain) extends BlackBox{
  val o = out(Bool)
}

class CrossClockCheckerTesterA extends Component{
  val clkA = ClockDomain.external("clkA")
  val clkB = ClockDomain.external("clkB")

  val reg = clkA(RegNext(in Bool()))

  val bb = new BBA(clkB)
  bb.i := reg
}


class CrossClockCheckerTesterB extends Component{
  val clkA = ClockDomain.external("clkA")
  val clkB = ClockDomain.external("clkB")

  val reg = in Bool()

  val bb = new BBA(clkB)
  bb.i := reg
}

class CrossClockCheckerTesterC extends Component{
  val clkA = ClockDomain.external("clkA")
  val clkB = ClockDomain.external("clkB")

  val reg = out Bool()

  val bb = new BBB(clkB)
  bb.o <> reg
}


class CrossClockCheckerTester extends SpinalAnyFunSuite{
  import CheckTester._

  test("a") {
    generationShouldPass(new CrossClockCheckerTesterA)
  }

  test("b") {
    generationShouldFail({
      val c = new CrossClockCheckerTesterA
      c.bb.i.addTag(ClockDomainTag(c.clkB))
      c
    })
  }

  test("c") {
    generationShouldFail({
      val c = new CrossClockCheckerTesterB
      c.reg.addTag(ClockDomainTag(c.clkA))
      c.bb.i.addTag(ClockDomainTag(c.clkB))
      c
    })
  }

  test("d") {
    generationShouldFail({
      val c = new CrossClockCheckerTesterC
      c.reg.addTag(ClockDomainTag(c.clkA))
      c.bb.o.addTag(ClockDomainTag(c.clkB))
      c
    })
  }
}


class SynchronousCheckerTesterA extends Component{
  val clk,reset = in Bool()
  val input = in UInt(8 bits)
  val output = out UInt(8 bits)

  val cd = ClockDomain(clk, reset)
  val logic = cd on new Area{
    val a = RegNext(input)
    val b = RegNext(a)
    output := b
  }
}

class SynchronousCheckerTesterB extends Component{
  val clk1,reset = in Bool()
  val input = in UInt(8 bits)
  val output = out UInt(8 bits)


  val clk2 = CombInit(clk1)

  val cd1 = ClockDomain(clk1, reset)
  val cd2 = ClockDomain(clk2, reset)

  val logic1 = cd1 on new Area{
    val a = RegNext(input)
    val b = RegNext(a)
    output := b
  }

  val logic2 = cd2 on new Area{
    val a = out(RegNext(logic1.a))

  }
}

class SynchronousCheckerTesterC(v : Int) extends Component{
  val clk1, clk2, reset = in Bool()
  val input = in UInt(8 bits)
  val output = out UInt(8 bits)

  val clk1Buf = List.fill(3)(Bool).foldLeft(clk1){(i,o) => o := i; o}
  val clk2Buf = List.fill(3)(Bool).foldLeft(clk2){(i,o) => o := i; o}

  val cd1 = ClockDomain(clk1Buf, reset)
  val cd2 = ClockDomain(clk2Buf, reset)
  v match{
    case 0 =>
    case 1 => cd1.setSyncWith(cd2)
    case 2 => cd2.setSyncWith(cd1)
    case 3 => ClockDomain(clk1).setSyncWith(cd2)
    case 4 => ClockDomain(clk2).setSyncWith(cd1)
    case 5 => ClockDomain(clk1).setSyncWith(ClockDomain(clk2))
  }

  val logic1 = cd1 on new Area{
    val a = RegNext(input)
    val b = RegNext(a)
    output := b
  }

  val logic2 = cd2 on new Area{
    val a = out(RegNext(logic1.a))
  }
}


class SynchronousCheckerTesterD(v : Int) extends Component{
  val clk1,reset = in Bool()
  val input = in UInt(8 bits)
  val output = out UInt(8 bits)


  val clk2 = clk1 && input === 0

  val cd1 = ClockDomain(clk1, reset)
  val cd2 = ClockDomain(clk2, reset)

  v match {
    case 0 =>
    case 1 => cd1.setSyncWith(cd2)
    case 2 => Clock.syncDrive(source = clk1, sink = clk2)
  }

  val logic1 = cd1 on new Area{
    val a = RegNext(input)
    val b = RegNext(a)
    output := b
  }

  val logic2 = cd2 on new Area{
    val a = out(RegNext(logic1.a))
  }
}

class SynchronousCheckerTester extends SpinalAnyFunSuite{
  import CheckTester._

  test("a") { generationShouldPass(new SynchronousCheckerTesterA) }
  test("b") { generationShouldPass(new SynchronousCheckerTesterB) }
  test("c0") { generationShouldFail(new SynchronousCheckerTesterC(0)) }
  test("c1") { generationShouldPass(new SynchronousCheckerTesterC(1)) }
  test("c2") { generationShouldPass(new SynchronousCheckerTesterC(2)) }
  test("c3") { generationShouldPass(new SynchronousCheckerTesterC(3)) }
  test("c4") { generationShouldPass(new SynchronousCheckerTesterC(4)) }
  test("c5") { generationShouldPass(new SynchronousCheckerTesterC(5)) }
  test("d0") { generationShouldFail(new SynchronousCheckerTesterD(0)) }
  test("d1") { generationShouldPass(new SynchronousCheckerTesterD(1)) }
  test("d2") { generationShouldPass(new SynchronousCheckerTesterD(2)) }
//  test("d3") { generationShouldFail(new SynchronousCheckerTesterD(3)) }
}
