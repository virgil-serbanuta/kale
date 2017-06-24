package org.kframework.kale.transformer

import org.kframework.kale._

object Binary {

  /**
    * f specifies how to process a pair of terms with labels (leftLabel, rightLabel).
    * f is automatically hooked and applied via Apply.
    */
  type ProcessingFunction = (Apply => (Term, Term) => Term)

  type ProcessingFunctions = PartialFunction[(Label, Label), ProcessingFunction]

  abstract class F[A <: Term, B <: Term](f: (A, B) => Term) extends ((Term, Term) => Term) with Product {
    override def toString = getClass.getTypeName

    override def apply(v1: Term, v2: Term): Term = f(v1.asInstanceOf[A], v2.asInstanceOf[B])
  }

  def definePartialFunction[Process <: Apply, A <: Term, B <: Term](f: PartialFunction[(Label, Label), Process => (A, B) => Term]): ProcessingFunctions = f.asInstanceOf[ProcessingFunctions]

  trait Apply extends ((Term, Term) => Term) {
    val env: Environment
    assert(env.isSealed)

    protected def processingFunctions: ProcessingFunctions = PartialFunction.empty

    protected lazy val arr: Array[Array[(Term, Term) => Term]] = {
      val pf = processingFunctions.lift

      val arr: Array[Array[(Term, Term) => Term]] =
        (0 until env.labels.size + 1).map({ i =>
          new Array[(Term, Term) => (Term)](env.labels.size + 1)
        }).toArray

      for (left <- env.labels) {
        for (right <- env.labels) {
          assert(arr(left.id)(right.id) == null)
          val f = pf((left, right)).map(x => x(this)).orNull
          arr(left.id)(right.id) = f
        }
      }
      arr
    }

    def apply(left: Term, right: Term): Term = {
      //      assert(labels.contains(left.label) && labels.contains(right.label))
      assert(left.label.id <= env.labels.size, "Left label " + left.label + " with id " + left.label.id + " is not registered. Label list:" + env.labels.map(l => (l.id, l)).toList.sortBy(_._1).mkString("\n"))
      assert(right.label.id <= env.labels.size, "Right label " + right.label + " with id " + right.label.id + " is not registered. Label list:" + env.labels.map(l => (l.id, l)).toList.sortBy(_._1).mkString("\n"))

      val u = try {
        arr(left.label.id)(right.label.id)
      } catch {
        case _: IndexOutOfBoundsException => throw new AssertionError("No processing function registered for: " + left.label + " and " + right.label)
      }
      val res = if (u != null)
        u(left, right)
      else
        env.Bottom

      assert(!(left == right && res == env.Bottom), left.toString)
      res
    }

    lazy val processingFunctionsByLabelPair: Map[(Label, Label), (Term, Term) => Term] = arr.zipWithIndex.flatMap({
      case (innerArray, i) => innerArray.zipWithIndex.filter(_._1 != null) map {
        case (f, j) if f != null => (env.labelForIndex(i), env.labelForIndex(j)) -> f
      }
    }).toMap

    override def toString: String = processingFunctionsByLabelPair.mkString("\n")
  }

}
