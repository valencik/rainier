package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Fn[A, Y] {self =>
  type X
  protected def encoder: Encoder[A] { type U = X }
  protected def xy(x: X): Y

  def apply(seq: Seq[A]): Seq[Y] =
    seq.map{a => xy(encoder.wrap(a))}
  
  def zip[B, Z](fn: Fn[B, Z]): Fn[(A, B), (Y, Z)] =
    new Fn[(A, B), (Y, Z)] {
        type X = (self.X, fn.X)
        val encoder = Encoder.zip(self.encoder, fn.encoder)
        def xy(x: (self.X, fn.X)) = (self.xy(x._1), fn.xy(x._2))
    }

  def map[Z](g: Y => Z): Fn[A, Z] =
    new Fn[A,Z] {
        type X = self.X
        val encoder = self.encoder
        def xy(x: X) = g(self.xy(x))
    }
}

object Fn {
  def encode[A](implicit enc: Encoder[A]) =
    new Fn[A, enc.U] {
      type X = enc.U
      val encoder = enc
      def xy(x: X) = x
    }
}