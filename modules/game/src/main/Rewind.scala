package lila.game

import cats.data.Validated
import strategygames.GameLogic
import strategygames.format.{ FEN }
import strategygames.format.pgn.{ Reader, Sans, Tag, Tags }
import org.joda.time.DateTime

object Rewind {

  private def createTags(fen: Option[FEN], game: Game) = {
    val variantTag = Some(Tag(_.Variant, game.variant.name))
    val fenTag     = fen.map(f => Tag(_.FEN, f.value))

    Tags(List(variantTag, fenTag).flatten)
  }

  def apply(game: Game, initialFen: Option[FEN]): Validated[String, Progress] =
    (game.variant.gameLogic match {
      case GameLogic.Chess() | GameLogic.Draughts() =>
        Reader
          .movesWithSans(
            game.variant.gameLogic,
            moveStrs = game.pgnMoves,
            op = sans => Sans(sans.value.dropRight(1)),
            tags = createTags(initialFen, game)
          )
      case GameLogic.FairySF() =>
        Reader
          .movesWithPgns(
            game.variant.gameLogic,
            moveStrs = game.pgnMoves,
            op = ucis => ucis.dropRight(1),
            tags = createTags(initialFen, game)
          )
    }).flatMap(_.valid) map { replay =>
      val sgPlayer        = game.turnSGPlayer
      val rewindedGame = replay.state
      val newClock = game.clock.map(_.takeback) map { clk =>
        game.clockHistory.flatMap(_.last(sgPlayer)).fold(clk) { t =>
          clk.setRemainingTime(sgPlayer, t)
        }
      }
      def rewindPlayer(player: Player) = player.copy(proposeTakebackAt = 0)
      val newGame = game.copy(
        p1Player = rewindPlayer(game.p1Player),
        p2Player = rewindPlayer(game.p2Player),
        chess = rewindedGame.copy(clock = newClock),
        binaryMoveTimes = game.binaryMoveTimes.map { binary =>
          val moveTimes = BinaryFormat.moveTime.read(binary, game.playedTurns)
          BinaryFormat.moveTime.write(moveTimes.dropRight(1))
        },
        loadClockHistory = _ => game.clockHistory.map(_.update(!sgPlayer, _.dropRight(1))),
        movedAt = DateTime.now
      )
      Progress(game, newGame)
    }
}
