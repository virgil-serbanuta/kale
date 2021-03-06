package org.kframework.kale.standard

import org.kframework.kale
import org.kframework.kale._
import org.kframework.kale.context.Context1ApplicationLabel
import org.kframework.kale.util.{NameFromObject, Named, unreachable}
import org.kframework.kore.implementation.DefaultBuilders
import org.kframework.kore

import scala.collection.{Iterable, Map, Seq, Set}

abstract class ReferenceLabel[T](val name: String)(implicit val env: Environment) extends PrimordialDomainValueLabel[T]

trait PrimordialDomainValueLabel[T] extends DomainValueLabel[T] {
  def apply(v: T): DomainValue[T] = StandardDomainValue(this, v)
}

private[standard] case class StandardDomainValue[T](label: DomainValueLabel[T], data: T) extends DomainValue[T]

private[standard] case class StandardVariableLabel(implicit override val env: Environment) extends Named("#Variable") with VariableLabel {
  def apply(name: String): Variable = apply((Name(name), Sort.K))

  def apply(name: String, sort: kale.Sort): Variable = apply((Name(name), sort))

  def apply(name: kale.Name): Variable = apply((name, Sort.K))

  def apply(nameAndSort: (kale.Name, kale.Sort)): Variable = StandardVariable(nameAndSort._1, nameAndSort._2)

  override protected[this] def internalInterpret(s: String): (kale.Name, kale.Sort) = s.split(":") match {
    case Array(name, sort) => (Name(name), Sort(sort))
  }

  var counter = 0

  def __ = {
    counter += 1
    this ((Name("_" + counter), Sort("K")))
  }
}

private[standard] case class StandardVariable(name: kale.Name, givenSort: kale.Sort)(implicit env: Environment) extends Variable with kore.Variable {
  override lazy val sort = givenSort

  val label = env.Variable
}

private[standard] case class StandardTruthLabel(implicit val env: Environment) extends NameFromObject with TruthLabel {
  def apply(v: Boolean) = if (v) env.Top else env.Bottom
}

private[standard] abstract class Truth(val data: Boolean)(implicit val env: Environment) extends kale.Truth {
  val label = env.Truth
}

private[standard] case class TopInstance(implicit eenv: Environment) extends Truth(true) with kale.Top {
  override def get(v: Variable): Option[Term] = None

  def asMap = Map()

  override def toString: String = "⊤"

  override def apply(t: Term): Term = t
}

private[standard] case class BottomInstance(implicit eenv: Environment) extends Truth(false) with kale.Bottom {
  override def toString: String = "⊥"
}


private[standard] case class StandardEqualityLabel(implicit override val env: DNFEnvironment) extends Named("=") with EqualityLabel {
  override def apply(_1: Term, _2: Term): Term = {
    if (_1 == _2)
      env.Top
    else if (_1.isGround && _2.isGround) {
      env.Bottom
    } else {
      import org.kframework.kale.util.StaticImplicits._
      val Variable = env.Variable
      _1.label match {
        case `Variable` if !_2.contains(_1) => binding(_1.asInstanceOf[Variable], _2)
        case `Variable` if _2.containsInConstructor(_1) => env.Bottom
        case _ => new Equals(_1, _2)
      }
    }
  }

  override def binding(_1: Variable, _2: Term): Binding = {
    import org.kframework.kale.util.StaticImplicits._
    assert(!_2.contains(_1))
    new Binding(_1.asInstanceOf[Variable], _2)
  }
}

private[kale] class Equals(val _1: Term, val _2: Term)(implicit env: Environment) extends kale.Equals {
  val label = env.Equality

  override def equals(other: Any): Boolean = other match {
    case that: Equals => this._1 == that._1 && this._2 == that._2
    case _ => false
  }
}


class Binding(val variable: Variable, val term: Term)(implicit val env: DNFEnvironment) extends Equals(variable, term) with kale.Binding {
  // TODO(Daejun): Cosmin: occur check failed due to the context variables
  // assert(!util.Util.contains(term, variable))
  assert(_1.isInstanceOf[Variable])

  def get(v: Variable): Option[Term] = if (_1 == v) Some(_2) else None

  def asMap = Map(variable -> term)

  override def toString: String = super[Equals].toString
}

private[standard] case class StandardRewriteLabel(implicit val env: Environment) extends {
  val name = "=>"
} with RewriteLabel {
  def apply(_1: Term, _2: Term) = Rewrite(_1, _2)
}

case class Rewrite(_1: Term, _2: Term)(implicit env: Environment) extends kale.Rewrite {
  override val label = env.Rewrite
}

private[standard] case class DNFAndLabel(implicit val env: DNFEnvironment) extends {
  val name = "∧"
} with AssocWithIdLabel with AndLabel {

  import env._

  /**
    * normalizing
    */
  override def apply(_1: Term, _2: Term): Term = {
    if (_1 == Bottom || _2 == Bottom)
      Bottom
    else {
      apply(Set(_1, _2))
    }
    /*
      val substitutionAndTerms(sub1, terms1) = _1
      val substitutionAndTerms(sub2, terms2) = _2
      val allElements: Set[Term] = terms1.toSet ++ terms2 + sub1 + sub2
      Or(allElements map Or.asSet reduce cartezianProduct)
     */
  }

  /**
    * normalizing
    */
  override def apply(terms: Iterable[Term]): Term = {
    if (terms.isEmpty) Top
    else {
      val disjunction = terms map Or.asSet reduce cartezianProduct
      Or(disjunction)
    }

    //    val bindings: Map[Variable, Term] = terms.collect({ case Equality(v: Variable, t) => v -> t }).toMap
    //    val pureSubstitution = Substitution(bindings)
    //    val others: Iterable[Term] = terms.filter({ case Equality(v: Variable, t) => false; case _ => true })
    //    apply(pureSubstitution, others)
  }

  /**
    * normalizing
    */
  private def applyOnNonOrs(_1: Term, _2: Term): Term = {
    if (_1 == Bottom || _2 == Bottom)
      Bottom
    else {
      val substitutionAndTerms(sub1, terms1) = _1
      val substitutionAndTerms(sub2, terms2) = _2
      apply(sub1, sub2) match {
        case `Bottom` => Bottom
        case substitutionAndTerms(sub, terms) =>
          apply(sub, (terms1 ++ terms2 map sub) ++ terms)
        case _ => unreachable()
      }
    }
  }

  /**
    * not-normalizing
    */
  def apply(m: Map[Variable, Term]): Substitution = substitution(m)

  /**
    * normalizing
    */
  def apply(_1: Substitution, _2: Substitution): Term = substitution(_1, _2)

  /**
    * not-normalizing
    */
  def apply(pureSubstitution: Substitution, others: Iterable[Term]): Term = substitutionAndTerms(pureSubstitution, others)

  def asMap(t: Substitution): Map[Variable, Term] = t match {
    case `Top` => Map[Variable, Term]()
    case b: Binding => Map(b.variable -> b.term)
    case s: MultipleBindings => s.m
  }

  object formulasAndNonFormula {
    def unapply(t: Term): Some[(Term, Option[Term])] = t match {
      case tt: And => Some(tt.predicates, tt.nonPredicates)
      case tt if tt.isPredicate => Some(tt, None)
      case tt if !tt.isPredicate => Some(Top, Some(tt))
    }
  }

  object substitution {

    /**
      * normalizing
      */
    def apply(_1: Substitution, _2: Substitution): Term = {
      // TODO(Daejun): exhaustively apply to get a fixpoint, but be careful to guarantee termination
      // TODO: optimize to use the larger substitution as the first one
      val substitutionAndTerms(newSubs2, termsOutOfSubs2: Iterable[Term]) = _1(_2)

      val applyingTheSubsOutOf2To1 = newSubs2(_1).asInstanceOf[Substitution]

      val m1 = asMap(applyingTheSubsOutOf2To1)
      val m2 = asMap(newSubs2)

      val newSub: Substitution = substitution(m1 ++ m2)

      DNFAndLabel.this.apply(newSub, termsOutOfSubs2)
    }

    /**
      * not-normalizing
      */
    def apply(m: Map[Variable, Term]): Substitution = m.size match {
      case 0 => Top
      case 1 => new Binding(m.head._1, m.head._2)
      case _ => new MultipleBindings(m)
    }

    def unapply(t: Term): Option[Map[Variable, Term]] = t match {
      case t: Substitution => Some(asMap(t))
      case _ => None
    }
  }

  def asSubstitutionAndTerms(t: Term): (Substitution, Set[Term]) = t match {
    case s: Substitution => (s, Set.empty)
    case and: AndOfSubstitutionAndTerms => (and.s, And.asSet(and.terms))
    case and: AndOfTerms => (Top, and.terms)
    case o => (Top, Set(o))
  }

  /**
    * Unwraps into a substitution and non-substitution terms
    */
  object substitutionAndTerms {
    /**
      * not normalizing
      */
    def apply(pureSubstitution: Substitution, otherTerms: Iterable[Term]): Term = {
      val others = otherTerms filterNot (_ == env.Top)

      assert(others forall { t => t == pureSubstitution(t) })

      if (others.isEmpty) {
        pureSubstitution
      } else if (pureSubstitution == Top && others.size == 1) {
        others.head
      } else {
        strongBottomize(others.toSeq: _*) {
          val terms = if (others.size > 1) new AndOfTerms(others.toSet) else others.head
          if (pureSubstitution == Top) {
            terms
          } else {
            new AndOfSubstitutionAndTerms(pureSubstitution, terms)
          }
        }
      }
    }

    def unapply(t: Term): Option[(Substitution, Iterable[Term])] = Some(asSubstitutionAndTerms(t))
  }

  private def cartezianProduct(t1: Iterable[Term], t2: Iterable[Term]): Seq[Term] = {
    for (e1 <- t1.toSeq; e2 <- t2.toSeq) yield {
      applyOnNonOrs(e1, e2)
    }
  }

  override def construct(l: Iterable[Term]): Term = ???
}

private[standard] final class AndOfTerms(val terms: Set[Term])(implicit val env: Environment) extends And with Assoc {

  import env._

  lazy val predicates: Term = And(terms filter (_.isPredicate))

  lazy val nonPredicates: Option[Term] = {
    val nonFormulas = terms filter (!_.isPredicate)
    if (nonFormulas.size > 1) {
      throw new NotImplementedError("only handle at most one term for now")
    }
    nonFormulas.headOption
  }

  assert(terms.size > 1, terms.toString())
  assert(!terms.contains(Bottom))
  assert(!terms.contains(Top))

  override val label = And
  override val assocIterable: Iterable[Term] = terms

  override def _1: Term = terms.head

  override def _2: Term = if (terms.size == 2) terms.tail.head else new AndOfTerms(terms.tail)

  override def equals(other: Any): Boolean = other match {
    case that: AndOfTerms => this.terms == that.terms
    case _ => false
  }

  override def asSet: Set[Term] = terms
}

private[kale] final class AndOfSubstitutionAndTerms(val s: Substitution, val terms: Term)(implicit env: Environment) extends And with Assoc {

  import env._

  assert(terms != Bottom)

  val label = And

  lazy val predicates: Term = terms match {
    case a: AndOfTerms => And(s, a.predicates)
    case t if t.isPredicate => And(s, t)
    case _ => s
  }

  lazy val nonPredicates: Option[Term] = terms match {
    case a: AndOfTerms => a.nonPredicates
    case t if !t.isPredicate => Some(t)
    case _ => None
  }

  lazy val _1: Term = s
  lazy val _2: Term = terms
  override lazy val assocIterable: Iterable[Term] = And.asIterable(s) ++ And.asIterable(terms)

  override def equals(other: Any): Boolean = other match {
    case that: AndOfSubstitutionAndTerms => this.s == that.s && this.terms == that.terms
    case _ => false
  }

  override def asSet: Set[Term] = And.asSet(terms) | And.asSet(s)
}

private[standard] final class MultipleBindings(val m: Map[Variable, Term])(implicit val env: DNFEnvironment) extends And with Substitution with BinaryInfix {
  assert(m.size >= 2)

  import env._

  val label = And
  lazy val _1 = Equality(m.head._1, m.head._2)
  lazy val _2: Substitution = And.substitution(m.tail)

  override def equals(other: Any): Boolean = other match {
    case that: MultipleBindings => m == that.m
    case _ => false
  }

  override val hashCode: Int = label.hashCode

  def get(v: Variable): Option[Term] = m.get(v)

  override def asMap: Map[Variable, Term] = m

  override val assocIterable: Iterable[Term] = And.asIterable(this)

  override def toString: String = super[BinaryInfix].toString

  override val predicates: Term = this
  override val nonPredicates: Option[Term] = None

  override def asSet: Set[Term] = m.map({ case (k, v) => Equality.binding(k, v) }).toSet
}

private[standard] case class DNFOrLabel(implicit override val env: Environment) extends Named("∨") with OrLabel {

  import env._

  def apply(_1: Term, _2: Term): Term =
    asSet(_1) | asSet(_2) match {
      case s if s.isEmpty => Bottom
      case s if s.size == 1 => s.head
      case s => new OrWithAtLeastTwoElements(s)
    }

  override def apply(l: Iterable[Term]): Term = l.foldLeft(Bottom: Term)(apply)
}

private[this] class OrWithAtLeastTwoElements(val terms: Set[Term])(implicit env: Environment) extends Or {
  assert(terms.size > 1)

  import env._

  val label = Or

  lazy val _1: Term = terms.head
  lazy val _2 = Or(terms.tail.toSeq)
  override val assocIterable: Iterable[Term] = terms

  override lazy val isPredicate: Boolean = terms.forall(_.isPredicate)

  override def equals(other: Any): Boolean = other match {
    case that: OrWithAtLeastTwoElements => this.terms == that.terms
    case _ => false
  }

  override def asSet: Set[Term] = terms
}

// implements: X and (M = (c = X) and (M = Bot implies t) and (not(m = Bot) implies e)
private[standard] class IfThenElseLabel(implicit override val env: Environment) extends Named("if_then_else") with Label3 {
  def apply(c: Term, t: Term, e: Term) = {
    if (c == env.Top)
      t
    else if (c == env.Bottom)
      e
    else
      FreeNode3(this, c, t, e)
  }
}

case class Name(str: String) extends kale.Name

private[standard] class BindMatchLabel(implicit override val env: Environment) extends Named("BindMatch") with Label2 {
  def apply(v: Term, p: Term) = FreeNode2(this, v.asInstanceOf[Variable], p)
}
