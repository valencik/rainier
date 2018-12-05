package com.stripe.rainier.compute

trait Encoder[T, U] {
  def wrap(t: T): U
  def create(acc: List[Variable]): (U, List[Variable])
  def extract(t: T, acc: List[Double]): List[Double]
}

object Encoder {
  implicit val int: Encoder[Int, Real] =
    new Encoder[Int, Real] {
      def wrap(t: Int) = Real(t)
      def create(acc: List[Variable]) = {
        val u = new Variable
        (u, u :: acc)
      }
      def extract(t: Int, acc: List[Double]) =
        t.toDouble :: acc
    }

  implicit val double: Encoder[Double, Real] =
    new Encoder[Double, Real] {
      def wrap(t: Double) = Real(t)
      def create(acc: List[Variable]) = {
        val u = new Variable
        (u, u :: acc)
      }
      def extract(t: Double, acc: List[Double]) =
        t :: acc
    }

  implicit def zip[A, B, X, Y](implicit a: Encoder[A, X],
                               b: Encoder[B, Y]): Encoder[(A, B), (X, Y)] =
    new Encoder[(A, B), (X, Y)] {
      def wrap(t: (A, B)) = (a.wrap(t._1), b.wrap(t._2))
      def create(acc: List[Variable]) = {
        val (bv, acc1) = b.create(acc)
        val (av, acc2) = a.create(acc1)
        ((av, bv), acc2)
      }
      def extract(t: (A, B), acc: List[Double]) =
        a.extract(t._1, b.extract(t._2, acc))
    }

  def vector[T, U](size: Int)(
      implicit ph: Encoder[T, U]): Encoder[Seq[T], Seq[U]] =
    new Encoder[Seq[T], Seq[U]] {
      def wrap(t: Seq[T]) = t.map { x =>
        ph.wrap(x)
      }
      def create(acc: List[Variable]) =
        1.to(size).foldLeft((List.empty[U], acc)) {
          case ((us, a), _) =>
            val (u, a2) = ph.create(a)
            (u :: us, a2)
        }
      def extract(t: Seq[T], acc: List[Double]) =
        t.foldRight(acc) { case (x, a) => ph.extract(x, a) }
    }
}