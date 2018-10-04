package com.stripe.rainier.compute

class Target(val real: Real, val placeholders: Map[Variable, Array[Double]]) {
  val nRows =
    if (placeholders.isEmpty)
      0
    else
      placeholders.head._2.size

  val placeholderVariables: List[Variable] = placeholders.keys.toList
  val variables: Set[Variable] = RealOps.variables(real) -- placeholderVariables

  private def inlineRow(i: Int): Real = {
    val row: Map[Variable, Real] = placeholders.map {
      case (v, a) => v -> Real(a(i))
    }
    PartialEvaluator.inline(real, row)
  }

  def inlined: Real =
    if (placeholders.isEmpty)
      real
    else {
      val inlinedRows =
        0.until(nRows).map { i =>
          inlineRow(i)
        }
      Real.sum(inlinedRows.toList)
    }

  val MAX_INLINE_TERMS = 2000
  def maybeInlined: Option[Real] =
    if (placeholders.isEmpty)
      Some(real)
    else {
      var result = Real.zero
      var i = 0
      while (i < nRows) {
        result += inlineRow(i)
        i += 1
        result match {
          case l: Line if (l.ax.size > MAX_INLINE_TERMS) =>
            return None
          case _ => ()
        }
      }
      result match {
        case l: Line =>
          println("**" + l.ax.size)
        case _ => println("***")
      }
      Some(result)
    }

  def batched(batchBits: Int): (List[Variable], List[Real]) = {
    val (variables, outputs) =
      0.to(batchBits)
        .toList
        .map { i =>
          batch(i)
        }
        .unzip

    (placeholderVariables ++ variables.flatten, real :: outputs)
  }

  private def batch(bit: Int): (List[Variable], Real) =
    0.until(1 << bit).foldLeft((List.empty[Variable], Real.zero)) {
      case ((v, r), _) =>
        val newVars = placeholderVariables.map { _ =>
          new Variable
        }
        val newReal =
          PartialEvaluator.inline(real, placeholderVariables.zip(newVars).toMap)
        (v ++ newVars, r + newReal)
    }

  def updater: (Real, List[Variable]) = {
    val newVars = placeholderVariables.map { _ =>
      new Variable
    }
    val newReal =
      PartialEvaluator.inline(real, placeholderVariables.zip(newVars).toMap)
    (newReal, newVars)
  }
}

object Target {
  def apply(real: Real): Target = new Target(real, Map.empty)
  val empty: Target = apply(Real.zero)
}
