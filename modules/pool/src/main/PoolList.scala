package lila.pool

import play.api.libs.json.Json
import scala.concurrent.duration._
import strategygames.variant.Variant

object PoolList {

  import PoolConfig._

  val all: List[PoolConfig] = List(
    PoolConfig(1 ++ 0, Wave(12 seconds, 40 players), Variant.Chess(strategygames.chess.variant.Standard)),
    PoolConfig(3 ++ 2, Wave(22 seconds, 30 players), Variant.Chess(strategygames.chess.variant.Standard)),
    PoolConfig(
      3 ++ 2,
      Wave(22 seconds, 30 players),
      Variant.Draughts(strategygames.draughts.variant.Standard)
    ),
    PoolConfig(
      3 ++ 2,
      Wave(22 seconds, 30 players),
      Variant.Chess(strategygames.chess.variant.LinesOfAction)
    ),
    PoolConfig(5 ++ 3, Wave(22 seconds, 30 players), Variant.FairySF(strategygames.fairysf.variant.Shogi)),
    PoolConfig(3 ++ 2, Wave(22 seconds, 30 players), Variant.FairySF(strategygames.fairysf.variant.Xiangqi)),
    PoolConfig(3 ++ 2, Wave(22 seconds, 30 players), Variant.FairySF(strategygames.fairysf.variant.Flipello)),
    PoolConfig(3 ++ 2, Wave(22 seconds, 30 players), Variant.Mancala(strategygames.mancala.variant.Oware))
  )

  val clockStringSet: Set[String] = all.view.map(_.clock.show) to Set

  val json = Json toJson all

  implicit private class PimpedInt(self: Int) {
    // TODO: byoyomi, we need to support byoyomi clocks here.
    def ++(increment: Int) = strategygames.FischerClock.Config(self * 60, increment)
    def players            = NbPlayers(self)
  }
}
