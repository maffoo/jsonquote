package net.maffoo.jsonquote

object Util {
  def expect[A](a: A)(implicit it: Iterator[A]): Unit = {
    val next = it.next()
    require(next == a, s"expected $a but got $next")
  }
}
