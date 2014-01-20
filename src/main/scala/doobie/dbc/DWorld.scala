package doobie
package dbc

import scalaz._
import scalaz.Kleisli.ask
import scalaz.effect.IO
import scalaz.effect.MonadCatchIO.ensuring
import scalaz.effect.kleisliEffect._
import scalaz.syntax.effect.monadCatchIO._
import Scalaz._

// In order to help inference we slice off the S parameter.
trait DWorld[S] {

  type Action0[S0, +A] = Kleisli[IO, (Log[LogElement], S0), A]

  type Action[+A] = Action0[S,A] // Kleisli[IO, (Log[LogElement], S), A]

  protected def log: Action[Log[LogElement]] =
    ask[IO, (Log[LogElement], S)].map(_._1)

  protected def effect[A](f: S => A): Action[A] =
    ask[IO, (Log[LogElement], S)].map(p => f(p._2))

  protected def primitive[A](e: => String, f: S => A): Action[A] =
    push("jdbc:" + e, effect(f))

  def push[A](e: => String, a: Action[A]): Action[A] =
    log.flatMap(_.log(LogElement(e), a))

  // Call a subroutine in another monad, with a cleanup action.
  // It's a bit lame because of the type lamdbda
  protected def gosub[S,A](state: Action[S], action: Action0[S,A], cleanup: Action0[S, Unit]): Action[A] =
    for {
      p <- log tuple state
      a = ensuring[({type l[a] = Action0[S,a]})#l, A, Unit](action, cleanup).run(p).liftIO[Action]
      a <- push("gosub/cleanup", a)
    } yield a

  // Call a subroutine in another monad, without cleanup
  protected def gosub0[S,A](state: Action[S], action: Action0[S,A]): Action[A] =
    log tuple state >>= (p => push("gosub", action.run(p).liftIO[Action]))

}