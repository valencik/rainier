package com.stripe.rainier.sampler

private[sampler] case class LeapFrog(
  nVars: Int,
  initialHalfThenFullStep: Array[Double] => Array[Double] ,
  twoFullSteps: Array[Double] => Array[Double],
  finalHalfStep: Array[Double] => Array[Double]
) {
  /*
  Params layout:
  array(0..(n-1)) == ps
  array(n..(n*2-1)) == qs
  array(n*2) == potential
  array(n*2+1) == stepSize
   */
  private val potentialIndex = nVars * 2
  private val stepSizeIndex = potentialIndex + 1
  private val inputOutputSize = stepSizeIndex + 1
  private val buf1 = new Array[Double](inputOutputSize)
  private val buf2 = new Array[Double](inputOutputSize)

  //Compute the acceptance probability for a single step at this stepSize without
  //re-initializing the ps, or modifying params
  def tryStepping(params: Array[Double], stepSize: Double): Double = {
    System.arraycopy(params, 0, buf1, 0, inputOutputSize)
    buf1(stepSizeIndex) = stepSize
    buf2 = initialHalfThenFullStep(buf1) //CompiledFunction.run(initialHalfThenFullStep, buf1, globals, buf2)
    buf1 = finalHalfStep(buf2) // CompiledFunction.run(finalHalfStep, buf2, globals, buf1)
    logAcceptanceProb(params, buf1)
  }

  //attempt to take N steps
  //this will always clobber the stepSize and ps in params,
  //but will only update the qs if the move is accepted
  def step(params: Array[Double], n: Int, stepSize: Double)(
      implicit rng: RNG): Double = {
    initializePs(params)
    params(stepSizeIndex) = stepSize
    buf2 = initialHalfThenFullStep(params) //CompiledFunction.run(initialHalfThenFullStep, params, globals, buf2)
    var i = 1
    var in = buf2
    var out = buf1
    while (i < n) {
      out = twoFullSteps(in) //CompiledFunction.run(twoFullSteps, in, globals, out)
      val tmp = in; in = out; out = tmp
      i += 1
    }
    out = finalHalfStep(in)//CompiledFunction.run(finalHalfStep, in, globals, out)
    val p = logAcceptanceProb(params, out)
    if (p > Math.log(rng.standardUniform))
      System.arraycopy(out, 0, params, 0, inputOutputSize)
    p
  }

  def variables(array: Array[Double]): Array[Double] = {
    val newArray = new Array[Double](nVars)
    var i = 0
    while (i < nVars) {
      newArray(i) = array(i + nVars)
      i += 1
    }
    newArray
  }

  //we want the invariant that a params array always has the potential which
  //matches the qs. That means when we initialize a new one
  //we need to compute the potential. We can do that (slightly wastefully)
  //by using initialHalfThenFullStep with a stepSize of 0.0
  def initialize(implicit rng: RNG): Array[Double] = {
    java.util.Arrays.fill(buf1, 0.0)
    var i = nVars
    val j = nVars * 2
    while (i < j) {
      buf1(i) = rng.standardNormal
      i += 1
    }
    val array = new Array[Double](inputOutputSize)
    array = initialHalfThenFullStep(buf1) //CompiledFunction.run(initialHalfThenFullStep, buf1, globals, array)
    initializePs(array)
    array
  }

  /**
    * This is the dot product (ps^T ps).
    * The fancier variations of HMC involve changing this kinetic term
    * to either take the dot product with respect to a non-identity matrix (ps^T M ps)
    * (a non-standard Euclidean metric) or a matrix that depends on the qs
    * (ps^T M(qs) ps) (a Riemannian metric)
    */
  private def kinetic(array: Array[Double]): Double = {
    var k = 0.0
    var i = 0
    while (i < nVars) {
      val p = array(i)
      k += (p * p)
      i += 1
    }
    k / 2.0
  }

  private def logAcceptanceProb(from: Array[Double],
                                to: Array[Double]): Double = {
    val deltaH = kinetic(to) + to(potentialIndex) - kinetic(from) - from(
      potentialIndex)
    if (deltaH.isNaN) { Math.log(0.0) } else { (-deltaH).min(0.0) }
  }

  private def initializePs(array: Array[Double])(implicit rng: RNG): Unit = {
    var i = 0
    while (i < nVars) {
      array(i) = rng.standardNormal
      i += 1
    }
  }
}

private[sampler] object LeapFrog {
  def apply(df: DensityFunction): LeapFrog = {
//    val qs = context.variables
//    val ps = qs.map { _ =>
//      new Variable
//    }
//    val potential = new Variable
//    val stepSize = new Variable
//    val inputs: List[Variable] = (ps ++ qs) :+ potential :+ stepSize
//
//    val newPotential = context.density * -1
//    val grad = context.gradient.map { g =>
//      g * -1
//    }

//    val halfPs =
//      ps.zip(grad).map {
//        case (p, g) => p - (stepSize / 2) * g
//      }
//    val halfPsNewQs =
//      qs.zip(halfPs).map { case (q, p) => q + (stepSize * p) }
//
//    val initialHalfThenFullStep =
//      (halfPs ++ halfPsNewQs) :+ newPotential :+ stepSize

//    val fullPs =
//      ps.zip(grad).map { case (p, g) => p - stepSize * g }
//
//    val fullPsNewQs =
//      qs.zip(fullPs).map { case (q, p) => q + (stepSize * p) }

//    val twoFullSteps =
//      (fullPs ++ fullPsNewQs) :+ newPotential :+ stepSize

//    val finalHalfStep =
//      (halfPs ++ qs) :+ newPotential :+ stepSize

    def split(inputs: Array[Double]): (Array[Double], Array[Double], Double, Double) = {
      (require (inputs.size = densityFun.nVars * 2 + 2))
      val nVars = densityFun.nVars
      (inputs.slice(0, nVars), inputs.slice(nVars, nVars * 2), inputs(nVars * 2), inputs(nVars * 2 + 1))
    }

    def grad(params: Array[Double]) = densityFun.gradient(params).map{ g => g * -1 }

    def newPotential(params: Array[Double]) = densityFun.density(params) * -1

    def halfPs(qs: Array[Double], ps: Array[Double]): Array[Double] =
      ps.zip(grad(qs)).map{
        case (p, g) => p - (stepSize / 2) * g
      }

    def halfPsNewQs(qs: Array[Double], ps: Array[Double]): Array[Double] = {
      val halfNewPs = halfPs(qs, ps)
      qs.zip(halfNewPs).map{ case (q, p) => q + (stepSize * q)
    }

    def initialHalfThenFullStep(inputs: Array[Double]): Array[Double] = {
      val (ps, qs, _, stepSize) = split(inputs)
      (halfPs(qs, ps) ++ halfPsNewQs(qs, ps)) :+ newPotential(qs) :+ stepSize
    }

    def fullPs(qs: Array[Double], ps: Array[Double]): Array[Double] =
      ps.zip(grad(qs)).map{ case (p, g) => p - stepSize * g }

    def fullPsNewQs(qs: Array[Double], ps: Array[Double]): Array[Double] =
      qs.zip(fullPs(qs, ps)).map{ case (q, p) = > q + (stepSize * p) }

    def twoFullSteps(inputs: Array[Double]): Array[Double] = {
      val (ps, qs, potential, stepSize) = split(inputs)
      (fullPs(qs, ps) ++ fullPsNewQs(ps, qs)) :+ newPotential(qs) :+ stepSize
    }

    def finalHalfStep(inputs: Array[Double]): Array[Double] = {
      val (ps, qs, _, stepSize) = split(inputs)
      (halfPs(qs, ps) ++ qs) :+ newPotential(qs) :+ stepSize
    }

    LeapFrog(
      densityFun.nVars,
      initialHalfThenFullStep _,
      twoFullSteps _,
      finalHalfStep _
    )
  }
}
