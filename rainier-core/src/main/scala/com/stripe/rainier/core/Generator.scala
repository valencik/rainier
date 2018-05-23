package com.stripe.rainier.core

import com.stripe.rainier.compute.Real
import com.stripe.rainier.sampler.RNG

trait Generator[T] { self =>
  def requirements: Set[Real]

  def get(implicit r: RNG, n: Numeric[Real]): T

  def map[U](fn: T => U): Generator[U] = new Generator[U] {
    val requirements: Set[Real] = self.requirements
    def get(implicit r: RNG, n: Numeric[Real]): U = fn(self.get)
  }

  def flatMap[U](fn: T => Generator[U]): Generator[U] = new Generator[U] {
    val requirements: Set[Real] = self.requirements
    def get(implicit r: RNG, n: Numeric[Real]): U = fn(self.get).get
  }

  def repeat(k: Int): Generator[Seq[T]] = new Generator[Seq[T]] {
    val requirements: Set[Real] = self.requirements
    def get(implicit r: RNG, n: Numeric[Real]): Seq[T] = 0.until(k).map { i =>
      self.get
    }
  }
}

object Generator {
  def apply[T](t: T): Generator[T] = new Generator[T] {
    val requirements: Set[Real] = Set.empty
    def get(implicit r: RNG, n: Numeric[Real]): T = t
  }

  def from[T](fn: (RNG, Numeric[Real]) => T): Generator[T] =
    new Generator[T] {
      val requirements: Set[Real] = Set.empty
      def get(implicit r: RNG, n: Numeric[Real]): T = fn(r, n)
    }

  def require[T](reqs: Set[Real])(fn: (RNG, Numeric[Real]) => T): Generator[T] =
    new Generator[T] {
      val requirements: Set[Real] = reqs
      def get(implicit r: RNG, n: Numeric[Real]): T = fn(r, n)
    }

  def traverse[T](seq: Seq[Generator[T]]): Generator[Seq[T]] =
    new Generator[Seq[T]] {
      val requirements: Set[Real] = seq.flatMap(_.requirements).toSet
      def get(implicit r: RNG, n: Numeric[Real]): Seq[T] =
        seq.map { g =>
          g.get
        }
    }
}
