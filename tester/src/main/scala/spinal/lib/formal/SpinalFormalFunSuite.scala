package spinal.lib.formal

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.Tag
import org.scalatest.exceptions.TestFailedException
import spinal.core._
import spinal.core.formal._
import spinal.idslplugin.Location

object SpinalFormal extends Tag("spinal.tester.formal")

class SpinalFormalFunSuite extends AnyFunSuite{  
  def assert(assertion: Bool)(implicit loc: Location) = {
    spinal.core.assert(assertion)
  }

  def assume(assertion: Bool)(implicit loc: Location) = {
    spinal.core.assume(assertion)
  }

  def test(testName: String)(testFun: => Unit): Unit = {
    super.test("formal_" + testName, SpinalFormal) {
      shouldPass(testFun)
    }
  }

  def shouldPass(body: => Unit) = assert(try {
    body
    true
  } catch {
    case e : FormalResultException => println(e.toStringPretty); if(e.isPass) true else throw new TestFailedException(e.getMessage, e, 0)
  })

  def shouldFail(body: => Unit) = assert(try {
    body
    // UnexpectedPassException  ?
    false
  } catch {
    case e : FormalResultException => println(e.toStringPretty); if(e.isFail) true else throw new TestFailedException(e.getMessage, e, 0)
  })
}
