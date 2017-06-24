package org.kframework.kale

import com.typesafe.scalalogging.Logger
import org.kframework.kale.standard.Bottomize
import org.kframework.kale.transformer.{Binary, Unary}

import scala.collection._

trait Environment extends MatchingLogicMixin with Bottomize {

  implicit protected val env: this.type = this

  val uniqueLabels = mutable.Map[String, Label]()

  def labels = uniqueLabels.values.toSet

  private var pisSealed = false

  def seal(): Unit = {
    pisSealed = true
  }

  def isSealed = pisSealed

  def unaryProcessingFunctions: Unary.ProcessingFunctions = Unary.processingFunctions

  val substitutionMaker: Substitution => SubstitutionApply


  final val unify = standard.lift("unify", {
    (a: Term, b: Term) =>
      assert(this.isSealed)
      unifier(a, b)
  })

  protected def unifier: Binary.Apply

  def register(label: Label): Int = {
    assert(!isSealed, "Cannot register label " + label + " because the environment is sealed")
    assert(label != null)

    if (uniqueLabels.contains(label.name))
      throw new AssertionError("Label " + label.name + " already registered. The current env is: \n" + this)

    uniqueLabels.put(label.name, label)
    uniqueLabels.size
  }

  def label(labelName: String): Label = uniqueLabels(labelName)

  lazy val labelForIndex: Map[Int, Label] = labels map { l => (l.id, l) } toMap

  override def toString = {
    "nextId: " + uniqueLabels.size + "\n" + uniqueLabels.mkString("\n")
  }

  def rewrite(rule: Term, obj: Term): Term

  val log = Logger("EnvironmentLogger" + this.hashCode())
  //  protected def defineBinaryPFs[Process <: Apply, A <: Term, B <: Term](f: PartialFunction[(Label, Label), Process => (A, B) => Term]): Binary.ProcessingFunctions = f.asInstanceOf[Binary.ProcessingFunctions]
}

trait HasMatcher {
  self: Environment =>

  protected def makeMatcher: Binary.ProcessingFunctions = PartialFunction.empty
}

trait HasUnifier {
  self: Environment =>

  protected def makeUnifier: Binary.ProcessingFunctions = PartialFunction.empty
}
