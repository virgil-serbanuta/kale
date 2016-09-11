package org.kframework.kale

import org.kframework.kale.transformer.Unary

import scala.collection.Set

import context._

object SubstitutionApply {
  def apply(pieces: Set[Unary.Piece[SubstitutionApply]], env: Environment)(s: Substitution): SubstitutionApply = new SubstitutionApply(pieces, env)(s)

  def apply(env: Environment): Substitution => SubstitutionApply = {
    import env._

    object Node0 extends Unary.TransformationFunction[Node0, Node0, SubstitutionApply] {
      def f(solver: SubstitutionApply)(t: Node0) = t
    }

    object Node1 extends Unary.TransformationFunction[Node1, Term, SubstitutionApply] {
      def f(solver: SubstitutionApply)(t: Node1) = t.label(solver(t._1))
    }

    object Node2 extends Unary.TransformationFunction[Node2, Term, SubstitutionApply] {
      def f(solver: SubstitutionApply)(t: Node2) = t.label(solver(t._1), solver(t._2))
    }

    object Node3 extends Unary.TransformationFunction[Node3, Term, SubstitutionApply] {
      def f(solver: SubstitutionApply)(t: Node3) = t.label(solver(t._1), solver(t._2), solver(t._3))
    }

    object Node4 extends Unary.TransformationFunction[Node4, Term, SubstitutionApply] {
      def f(solver: SubstitutionApply)(t: Node4) = t.label(solver(t._1), solver(t._2), solver(t._3), solver(t._4))
    }

    object Node5 extends Unary.TransformationFunction[Node5, Term, SubstitutionApply] {
      def f(solver: SubstitutionApply)(t: Node5) = t.label(solver(t._1), solver(t._2), solver(t._3), solver(t._4), solver(t._5))
    }

    object Node6 extends Unary.TransformationFunction[Node6, Term, SubstitutionApply] {
      def f(solver: SubstitutionApply)(t: Node6) = t.label(solver(t._1), solver(t._2), solver(t._3), solver(t._4), solver(t._5), solver(t._6))
    }

    object Var extends Unary.TransformationFunction[Variable, Term, SubstitutionApply] {
      def f(solver: SubstitutionApply)(v: Variable) = solver.getVariable(v).getOrElse(v)
    }

    object DoNothing extends Unary.TransformationFunction[Term, Term, SubstitutionApply] {
      def f(solver: SubstitutionApply)(a: Term) = a
    }

    object Context1 extends Unary.TransformationFunction[Context1, Term, SubstitutionApply] {
      override def f(solver: SubstitutionApply)(t: Context1): Term = {
        val recursiveResult = Equality(t.hole, solver(t.term))
        Substitution(solver.s, recursiveResult) match {
          case subs: Substitution =>
            val innerSolver: SubstitutionApply = SubstitutionApply(solver.pieces, solver.env)(subs)

            solver.getVariable(t.contextVar) map innerSolver getOrElse Bottom
          case `Bottom` => Bottom
        }
      }
    }

    val setOfUnaryPieces = labels.map({
      case `Variable` => Unary.Piece(Variable, Var)
      case l: Context1Label => Unary.Piece(l, Context1)

      case l: Label0 => Unary.Piece(l, Node0)
      case l: Label1 => Unary.Piece(l, Node1)
      case l: Label2 => Unary.Piece(l, Node2)
      case l: Label3 => Unary.Piece(l, Node3)
      case l: Label4 => Unary.Piece(l, Node4)
      case l: Label5 => Unary.Piece(l, Node5)
      case l: Label6 => Unary.Piece(l, Node6)
      case l: ConstantLabel[_] => Unary.Piece(l, DoNothing)
      case l: TruthLabel => Unary.Piece(l, DoNothing)
    })

    SubstitutionApply(setOfUnaryPieces, env)
  }
}

class SubstitutionApply(val pieces: Set[Unary.Piece[SubstitutionApply]], val env: Environment)(val s: Substitution) extends Unary.Apply[SubstitutionApply](pieces, env) with (Term => Term) {
  import env._

  def getVariable(v: Variable): Option[Term] = s.get(v)

  def fixpoint(t: Term): Term = Util.fixpoint(apply)(t)

  def apply(t: Term): Term =
    if (t.isGround)
      t
    else {
      arr(t.label.id) match {
        case null => Bottom
        case f => f(t)
      }
    }
}