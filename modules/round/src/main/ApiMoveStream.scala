package lila.round

import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import strategygames.Color
import strategygames.format.Forsyth
import play.api.libs.json._
import scala.concurrent.ExecutionContext

import lila.common.Bus
import lila.game.actorApi.MoveGameEvent
import lila.game.{ Game, GameRepo }
import strategygames.{ Centis, Replay }
import lila.game.actorApi.FinishGame

final class ApiMoveStream(gameRepo: GameRepo, gameJsonView: lila.game.JsonView)(implicit
    ec: ExecutionContext
) {

  private val delayMovesBy         = 3
  private val delayKeepsFirstMoves = 5

  def apply(game: Game): Source[JsObject, _] =
    Source futureSource {
      gameRepo.initialFen(game) map { initialFen =>
        val buffer = scala.collection.mutable.Queue.empty[JsObject]
        var moves  = 0
        Source(List(gameJsonView(game, initialFen))) concat
          Source
            .queue[JsObject](16, akka.stream.OverflowStrategy.dropHead)
            .statefulMapConcat { () => js =>
              moves += 1
              if (game.finished || moves <= delayKeepsFirstMoves) List(js)
              else {
                buffer.enqueue(js)
                (buffer.size > delayMovesBy) ?? List(buffer.dequeue())
              }
            }
            .mapMaterializedValue { queue =>
              val clocks = ~(for {
                clk   <- game.clock
                times <- game.bothClockStates
              } yield Vector(clk.config.initTime, clk.config.initTime) ++ times)
              val clockOffset = game.startColor.fold(0, 1)
              Replay.situations(game.variant.gameLib, game.pgnMoves, initialFen, game.variant) foreach {
                _.zipWithIndex foreach { case (s, index) =>
                  val clk = for {
                    white <- clocks.lift(index + 1 - clockOffset)
                    black <- clocks.lift(index - clockOffset)
                  } yield (white, black)
                  queue offer toJson(
                    Forsyth.exportBoard(s.board.variant.gameLib, s.board),
                    s.color,
                    s.board.history.lastMove.map(_.uci),
                    clk
                  )
                }
              }
              if (game.finished) {
                queue offer gameJsonView(game, initialFen)
                queue.complete()
              } else {
                val chans = List(MoveGameEvent makeChan game.id, "finishGame")
                val sub = Bus.subscribeFun(chans: _*) {
                  case MoveGameEvent(g, fen, move) =>
                    queue.offer(toJson(g, fen, move.some)).unit
                  case FinishGame(g, _, _) if g.id == game.id =>
                    queue offer gameJsonView(g, initialFen)
                    (1 to buffer.size) foreach { _ => queue.offer(Json.obj()) } // push buffer content out
                    queue.complete()
                }
                queue.watchCompletion() dforeach { _ =>
                  Bus.unsubscribe(sub, chans)
                }
              }
            }
      }
    }

  private def toJson(game: Game, fen: String, lastMoveUci: Option[String]): JsObject =
    toJson(
      fen,
      game.turnColor,
      lastMoveUci,
      game.clock.map { clk =>
        (clk.remainingTime(strategygames.White), clk.remainingTime(strategygames.Black))
      }
    )

  private def toJson(
      boardFen: String,
      turnColor: Color,
      lastMoveUci: Option[String],
      clock: Option[(Centis, Centis)]
  ): JsObject =
    clock.foldLeft(
      Json
        .obj("fen" -> s"$boardFen ${turnColor.letter}")
        .add("lm" -> lastMoveUci)
    ) { case (js, clk) =>
      js ++ Json.obj("wc" -> clk._1.roundSeconds, "bc" -> clk._2.roundSeconds)
    }
}
