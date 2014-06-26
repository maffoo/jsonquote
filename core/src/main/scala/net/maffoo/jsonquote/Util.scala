package net.maffoo.jsonquote

case class JsonError(msg: String, position: Pos) extends Exception(msg)

object Util {
  def expect[A](a: A)(implicit it: Iterator[(A, Pos)]): Unit = {
    val (next, pos) = it.next()
    if (next != a) throw JsonError(s"expected $a but got $next", pos)
  }
}
