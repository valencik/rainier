package rainier.models

import rainier.compute._
import rainier.core._
import rainier.sampler._

object FitHLL {
  val hll = HLL(10)
  val rand = new scala.util.Random
  implicit val rng = RNG.default

  def model(sketch: Map[Int, Byte]) =
    NonNegative.param.condition { lambda =>
      hll.logDensity(lambda, sketch)
    }

  def compare(scale: Int) = {
    println("Generating a set with max size " + scale)
    val data = 1.to(scale).map { i =>
      rand.nextInt
    }
    println("True size: " + data.toSet.size)

    val sketch = hll(data)
    println("Estimated size: " + hll.cardinality(sketch).toInt)
    val (lower, upper) = hll.bounds(sketch)
    println("Confidence interval: " + lower.toInt + ", " + upper.toInt)

    println("Inferring size")
    val samples = model(sketch).sample()
    val mean = samples.sum / samples.size
    println("Inferred size: " + mean.toInt)
    val sorted = samples.sorted
    val lower2 = sorted((samples.size * 0.05).toInt)
    val upper2 = sorted((samples.size * 0.95).toInt)
    println("Credible interval: " + lower2.toInt + ", " + upper2.toInt)
    println("")
  }

  def main(args: Array[String]) {
    compare(1000)
  }
}