package lila.game

import strategygames.togyzkumalak
import strategygames.togyzkumalak.format
import strategygames.togyzkumalak.{ Piece, PieceMap, Pos, PositionHash, Role }
import strategygames.{ Player => PlayerIndex, GameFamily }

import lila.db.ByteArray

sealed trait PtnStorage

private object PtnStorage {

  case object OldBin extends PtnStorage {

    def encode(gf: GameFamily, pgnMoves: PgnMoves) =
      ByteArray {
        monitor(_.game.pgn.encode("old")) {
          format.pgn.Binary.writeMoves(gf, pgnMoves).get
        }
      }

    def decode(bytes: ByteArray, plies: Int): PgnMoves =
      monitor(_.game.pgn.decode("old")) {
        format.pgn.Binary.readMoves(bytes.value.toList, plies).get.toVector
      }
  }

  //is Huffman used/needed anywhere for PtnStorage?
  //we default to Togyzkumalak in here
  case object Huffman extends PtnStorage {

    import org.lichess.compression.game.{ Encoder, Piece => JavaPiece, Role => JavaRole }
    import scala.jdk.CollectionConverters._

    def encode(pgnMoves: PgnMoves) =
      ByteArray {
        monitor(_.game.pgn.encode("huffman")) {
          Encoder.encode(pgnMoves.toArray)
        }
      }
    def decode(bytes: ByteArray, plies: Int): Decoded =
      monitor(_.game.pgn.decode("huffman")) {
        val decoded = Encoder.decode(bytes.value, plies)
        Decoded(
          pgnMoves = decoded.pgnMoves.toVector,
          pieces = decoded.pieces.asScala.view.flatMap { case (k, v) =>
            togyzkumalakPos(k).map(_ -> togyzkumalakPiece(v))
          }.toMap,
          positionHashes = decoded.positionHashes,
          lastMove = Option(decoded.lastUci) flatMap (format.Uci.apply),
          halfMoveClock = decoded.halfMoveClock
        )
      }

    private def togyzkumalakPos(sq: Integer): Option[Pos] = Pos(sq)
    private def togyzkumalakCount(role: JavaRole): Int =
      Role.javaSymbolToInt(role.symbol)

    private def togyzkumalakPiece(piece: JavaPiece): (Piece, Int) =
      (Piece(PlayerIndex.fromP1(piece.white), Role.defaultRole), togyzkumalakCount(piece.role))
  }

  case class Decoded(
      pgnMoves: PgnMoves,
      pieces: PieceMap,
      positionHashes: PositionHash, // irrelevant after game ends
      //unmovedRooks: UnmovedRooks,   // irrelevant after game ends
      lastMove: Option[format.Uci],
      //castles: Castles,  // irrelevant after game ends
      halfMoveClock: Int // irrelevant after game ends
  )

  private def monitor[A](mon: lila.mon.TimerPath)(f: => A): A =
    lila.common.Chronometer.syncMon(mon)(f)
}
