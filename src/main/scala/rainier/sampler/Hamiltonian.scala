package rainier.sampler

import rainier.compute._

sealed trait SampleMethod
case object SampleNUTS extends SampleMethod
case object SampleHMC extends SampleMethod

case class Hamiltonian(iterations: Int,
                       burnIn: Int,
                       tuneEvery: Int,
                       nSteps: Int,
                       sampleMethod: SampleMethod = SampleNUTS,
                       chains: Int = 4,
                       initialStepSize: Double = 1.0)
    extends Sampler {
  val description = ("HamiltonianMC",
                     Map(
                       "nSteps" -> nSteps.toDouble,
                       "initialStepSize" -> initialStepSize,
                       "iterations" -> iterations.toDouble,
                       "burnIn" -> burnIn.toDouble,
                       "tuneEvery" -> tuneEvery.toDouble,
                       "chains" -> chains.toDouble
                     ))

  def sample(density: Real)(implicit rng: RNG): Iterator[Sample] = {
    val tuned = take(HamiltonianChain(density),
                     burnIn + 1,
                     initialStepSize,
                     sampleMethod).last
    val stepSize = findReasonableStepSize(tuned, initialStepSize)
    println(s"tuned stepSize: $stepSize")
    0.until(chains).iterator.flatMap { i =>
      take(tuned, iterations, initialStepSize, sampleMethod).map { c =>
        val eval = new Evaluator(c.variables.zip(c.hParams.qs).toMap)
        Sample(i, c.accepted, eval)
      }.iterator
    }
  }

  /**
    * @note: Let U(Θ) be the potential, K(r) the kinetic.
    * The NUTS paper defines
    * H(Θ,r) = U(Θ) - K(r) as the difference
    * p(Θ,r) = exp(H)
    * and for ΔH = H(Θ',r') - H(Θ,r)
    * defines the acceptance ratio as min{1, exp(ΔH)}.
    * Neal and McKay, on the other hand
    * H(Θ,r) = U(Θ) + K(r) as the sum
    * and the acceptance ratio as min{1, exp(-ΔH)}.
    * These are the definitions we use, in the rest of HMC and NUTS
    * so we similarly use -ΔH to tune the stepSize for consistency.
    */
  private def computeDeltaH(chain: HamiltonianChain,
                            nextChain: HamiltonianChain): Double =
    nextChain.hParams.hamiltonian - chain.hParams.hamiltonian

  private def computeExponent(deltaH: Double): Double = {
    if (-deltaH > Math.log(0.5)) { 1.0 } else { -1.0 }
  }

  private def updateStepSize(stepSize: Double, exponent: Double): Double = {
    stepSize * Math.pow(2, exponent)
  }

  private def continueTuningStepSize(deltaH: Double,
                                     exponent: Double): Boolean = {
    exponent * (-deltaH) > -exponent * Math.log(2)
  }

  private def tuneStepSize(chain: HamiltonianChain, exponent: Double)(
      nextChain: HamiltonianChain,
      stepSize: Double,
      continue: Boolean
  ): (HamiltonianChain, Double, Boolean) = {
    val deltaH = computeDeltaH(chain, nextChain)
    val newContinue = continueTuningStepSize(deltaH, exponent)
    val (newStepSize, newNextChain) = {
      if (newContinue) {
        val updatedStepSize = updateStepSize(stepSize, exponent)
        val updatedNextChain = chain.nextChain(updatedStepSize)
        (updatedStepSize, updatedNextChain)
      } else { (stepSize, nextChain) }
    }
    println(s"deltaH: $deltaH")
    println(s"qs: ${chain.hParams.qs}")
    println(s"new qs: ${nextChain.hParams.qs}")
    println(s"potential: ${nextChain.hParams.potential}")
    println(s"grad: ${nextChain.hParams.gradPotential}")
    println(s"newContinue: $newContinue")
    println(s"newStepSize: $newStepSize")
    (newNextChain, newStepSize, newContinue)
  }

  private def findReasonableStepSize(chain: HamiltonianChain,
                                     initialStepSize: Double): Double = {
    val nextChain = chain.nextChain(initialStepSize)
    val deltaH = computeDeltaH(chain, nextChain)
    val exponent = computeExponent(deltaH)
    val tuningSteps =
      Stream.iterate((nextChain, initialStepSize, true)) {
        case (nextChain, stepSize, continue) =>
          tuneStepSize(chain, exponent)(nextChain, stepSize, continue)
      }
    val (_, stepSize, _) = tuningSteps.takeWhile {
      case (_, _, continue) => continue
    }.last
    stepSize
  }

  private def take(chain: HamiltonianChain,
                   iterations: Int,
                   stepSize: Double,
                   sampleMethod: SampleMethod): List[HamiltonianChain] = {
    def go(list: List[HamiltonianChain],
           remaining: Int): List[HamiltonianChain] =
      (remaining, sampleMethod) match {
        case (n, SampleNUTS) if n > 0 =>
          go(list.head.nextNUTS(stepSize) :: list, n - 1)
        case (n, SampleHMC) if n > 0 =>
          go(list.head.nextHMC(stepSize, nSteps) :: list, n - 1)
        case _ => list.take(iterations)
      }
    go(List(chain), iterations)
  }
}
