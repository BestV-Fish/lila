package lila.tree

import strategygames.Centis
import strategygames.format.pgn.{ Glyph, Glyphs }
import strategygames.format.{ FEN, UciCharPair, Uci }
import strategygames.opening.FullOpening
import strategygames.{ GameLogic, Pocket, PocketData, Pos, Role }
import play.api.libs.json._

import lila.common.Json._

sealed trait Node {
  def ply: Int
  def fen: FEN
  def check: Boolean
  // None when not computed yet
  def dests: Option[Map[Pos, List[Pos]]]
  def destsUci: Option[List[String]]
  def captureLength: Option[Int]
  def drops: Option[List[Pos]]
  def eval: Option[Eval]
  def shapes: Node.Shapes
  def comments: Node.Comments
  def gamebook: Option[Node.Gamebook]
  def glyphs: Glyphs
  def children: List[Branch]
  def opening: Option[FullOpening]
  def comp: Boolean // generated by a computer analysis
  def pocketData: Option[PocketData]
  def addChild(branch: Branch): Node
  def dropFirstChild: Node
  def clock: Option[Centis]
  def forceVariation: Boolean

  // implementation dependent
  def idOption: Option[UciCharPair]
  def moveOption: Option[Uci.WithSan]

  // who's playerIndex plays next
  def playerIndex = strategygames.Player.fromPly(ply)

  def mainlineNodeList: List[Node] =
    dropFirstChild :: children.headOption.fold(List.empty[Node])(_.mainlineNodeList)
}

case class Root(
    ply: Int,
    fen: FEN,
    check: Boolean,
    // None when not computed yet
    dests: Option[Map[Pos, List[Pos]]] = None,
    destsUci: Option[List[String]] = None,
    captureLength: Option[Int] = None,
    drops: Option[List[Pos]] = None,
    eval: Option[Eval] = None,
    shapes: Node.Shapes = Node.Shapes(Nil),
    comments: Node.Comments = Node.Comments(Nil),
    gamebook: Option[Node.Gamebook] = None,
    glyphs: Glyphs = Glyphs.empty,
    children: List[Branch] = Nil,
    opening: Option[FullOpening] = None,
    clock: Option[Centis] = None, // clock state at game start, assumed same for both players
    pocketData: Option[PocketData]
) extends Node {

  def idOption       = None
  def moveOption     = None
  def comp           = false
  def forceVariation = false

  def addChild(branch: Branch)     = copy(children = children :+ branch)
  def prependChild(branch: Branch) = copy(children = branch :: children)
  def dropFirstChild               = copy(children = if (children.isEmpty) children else children.tail)
}

case class Branch(
    id: UciCharPair,
    ply: Int,
    move: Uci.WithSan,
    fen: FEN,
    check: Boolean,
    // None when not computed yet
    dests: Option[Map[Pos, List[Pos]]] = None,
    destsUci: Option[List[String]] = None,
    captureLength: Option[Int] = None,
    drops: Option[List[Pos]] = None,
    eval: Option[Eval] = None,
    shapes: Node.Shapes = Node.Shapes(Nil),
    comments: Node.Comments = Node.Comments(Nil),
    gamebook: Option[Node.Gamebook] = None,
    glyphs: Glyphs = Glyphs.empty,
    children: List[Branch] = Nil,
    opening: Option[FullOpening] = None,
    comp: Boolean = false,
    clock: Option[Centis] = None, // clock state after the move is played, and the increment applied
    pocketData: Option[PocketData],
    forceVariation: Boolean = false // cannot be mainline
) extends Node {

  def idOption   = Some(id)
  def moveOption = Some(move)

  def addChild(branch: Branch)     = copy(children = children :+ branch)
  def prependChild(branch: Branch) = copy(children = branch :: children)
  def dropFirstChild               = copy(children = if (children.isEmpty) children else children.tail)

  def setComp = copy(comp = true)
}

object Node {

  sealed trait Shape
  object Shape {
    type ID    = String
    type Brush = String
    case class Circle(brush: Brush, orig: Pos)           extends Shape
    case class Arrow(brush: Brush, orig: Pos, dest: Pos) extends Shape
  }
  case class Shapes(value: List[Shape]) extends AnyVal {
    def list = value
    def ++(shapes: Shapes) =
      Shapes {
        (value ::: shapes.value).distinct
      }
  }
  object Shapes {
    val empty = Shapes(Nil)
  }

  case class Comment(id: Comment.Id, text: Comment.Text, by: Comment.Author) {
    def removeMeta =
      text.removeMeta map { t =>
        copy(text = t)
      }
  }
  object Comment {
    case class Id(value: String) extends AnyVal
    object Id {
      def make = Id(lila.common.ThreadLocalRandom nextString 4)
    }
    private val metaReg = """\[%[^\]]+\]""".r
    case class Text(value: String) extends AnyVal {
      def removeMeta: Option[Text] = {
        val v = metaReg.replaceAllIn(value, "").trim
        if (v.nonEmpty) Some(Text(v)) else None
      }
    }
    sealed trait Author
    object Author {
      case class User(id: String, titleName: String) extends Author
      case class External(name: String)              extends Author
      case object PlayStrategy                            extends Author
      case object Unknown                            extends Author
    }
    def sanitize(text: String) =
      Text {
        text.trim
          .take(4000)
          .replaceAll("""\r\n""", "\n") // these 3 lines dedup p1 spaces and new lines
          .replaceAll("""(?m)(^ *| +(?= |$))""", "")
          .replaceAll("""(?m)^$([\n]+?)(^$[\n]+?^)+""", "$1")
          .replaceAll("[{}]", "") // {} are reserved in PGN comments
      }
  }
  case class Comments(value: List[Comment]) extends AnyVal {
    def list                           = value
    def findBy(author: Comment.Author) = list.find(_.by == author)
    def set(comment: Comment) =
      Comments {
        if (list.exists(_.by == comment.by)) list.map {
          case c if c.by == comment.by => c.copy(text = comment.text)
          case c                       => c
        }
        else list :+ comment
      }
    def delete(commentId: Comment.Id) =
      Comments {
        value.filterNot(_.id == commentId)
      }
    def +(comment: Comment)    = Comments(comment :: value)
    def ++(comments: Comments) = Comments(value ::: comments.value)

    def filterEmpty = Comments(value.filter(_.text.value.nonEmpty))

    def hasPlayStrategyComment = value.exists(_.by == Comment.Author.PlayStrategy)
  }
  object Comments {
    val empty = Comments(Nil)
  }

  case class Gamebook(deviation: Option[String], hint: Option[String]) {
    private def trimOrNone(txt: Option[String]) = txt.map(_.trim).filter(_.nonEmpty)
    def cleanUp =
      copy(
        deviation = trimOrNone(deviation),
        hint = trimOrNone(hint)
      )
    def nonEmpty = deviation.nonEmpty || hint.nonEmpty
  }

  // TODO copied from lila.game
  // put all that shit somewhere else
  implicit private val pocketWriter: OWrites[Pocket] = OWrites { v =>
    JsObject(
      Role.storable(v.roles.headOption match {
        case Some(r) => r match {
          case Role.ChessRole(_)   => GameLogic.Chess()
          case Role.FairySFRole(_) => GameLogic.FairySF()
          case _ => sys.error("Pocket not implemented for GameLogic")
        }
        case None => GameLogic.Chess()
      }).flatMap { role =>
        Some(v.roles.count(role ==)).filter(0 <).map { count =>
          role.groundName -> JsNumber(count)
        }
      }
    )
  }
  implicit private val pocketDataWriter: OWrites[PocketData] = OWrites { v =>
    Json.obj("pockets" -> List(v.pockets.p1, v.pockets.p2))
  }

  implicit val openingWriter: OWrites[FullOpening] = OWrites { o =>
    Json.obj(
      "eco"  -> o.eco,
      "name" -> o.name
    )
  }

  implicit private val posWrites: Writes[Pos] = Writes[Pos] { p =>
    JsString(p.key)
  }
  implicit private val shapeCircleWrites = Json.writes[Shape.Circle]
  implicit private val shapeArrowWrites  = Json.writes[Shape.Arrow]
  implicit val shapeWrites: Writes[Shape] = Writes[Shape] {
    case s: Shape.Circle => shapeCircleWrites writes s
    case s: Shape.Arrow  => shapeArrowWrites writes s
  }
  implicit val shapesWrites: Writes[Node.Shapes] = Writes[Node.Shapes] { s =>
    JsArray(s.list.map(shapeWrites.writes))
  }
  implicit val glyphWriter: Writes[Glyph] = Json.writes[Glyph]
  implicit val glyphsWriter: Writes[Glyphs] = Writes[Glyphs] { gs =>
    Json.toJson(gs.toList)
  }

  implicit val clockWrites: Writes[Centis] = Writes { clock =>
    JsNumber(clock.centis)
  }
  implicit val commentIdWrites: Writes[Comment.Id] = Writes { id =>
    JsString(id.value)
  }
  implicit val commentTextWrites: Writes[Comment.Text] = Writes { text =>
    JsString(text.value)
  }
  implicit val commentAuthorWrites: Writes[Comment.Author] = Writes[Comment.Author] {
    case Comment.Author.User(id, name) => Json.obj("id" -> id, "name" -> name)
    case Comment.Author.External(name) => JsString(s"${name.trim}")
    case Comment.Author.PlayStrategy        => JsString("playstrategy")
    case Comment.Author.Unknown        => JsNull
  }
  implicit val commentWriter  = Json.writes[Node.Comment]
  implicit val gamebookWriter = Json.writes[Node.Gamebook]
  import Eval.JsonHandlers.evalWrites

  @inline implicit private def toPimpedJsObject(jo: JsObject) = new lila.base.PimpedJsObject(jo)

  implicit val defaultNodeJsonWriter: Writes[Node] =
    makeNodeJsonWriter(alwaysChildren = true)

  val minimalNodeJsonWriter: Writes[Node] =
    makeNodeJsonWriter(alwaysChildren = false)

  implicit def nodeListJsonWriter(alwaysChildren: Boolean): Writes[List[Node]] =
    Writes[List[Node]] { list =>
      val writer = if (alwaysChildren) defaultNodeJsonWriter else minimalNodeJsonWriter
      JsArray(list map writer.writes)
    }

  def makeNodeJsonWriter(alwaysChildren: Boolean): Writes[Node] =
    Writes { node =>
      import node._
      try {
        val comments = node.comments.list.flatMap(_.removeMeta)
        Json
          .obj(
            "ply" -> ply,
            "fen" -> fen.value
          )
          .add("id", idOption.map(_.toString))
          .add("uci", moveOption.map(_.uci.uci))
          .add("san", moveOption.map(_.san))
          .add("check", check)
          .add("eval", eval.filterNot(_.isEmpty))
          .add("comments", if (comments.nonEmpty) Some(comments) else None)
          .add("gamebook", gamebook)
          .add("glyphs", glyphs.nonEmpty)
          .add("shapes", if (shapes.list.nonEmpty) Some(shapes.list) else None)
          .add("opening", opening)
          .add("dests", dests.map { dst =>
            captureLength match {
              case Some(capts) => "#" + capts.toString + " " + destString(dst)
              case _ => destString(dst)
            }
          })
          .add("destsUci", destsUci)
          .add("captLen", captureLength)
          .add(
            "drops",
            drops.map { drops =>
              JsString(drops.map(_.key).mkString)
            }
          )
          .add("clock", clock)
          .add("crazy", pocketData)
          .add("comp", comp)
          .add(
            "children",
            if (alwaysChildren || children.nonEmpty) Some {
              nodeListJsonWriter(true) writes children
            }
            else None
          )
          .add("forceVariation", forceVariation)
      } catch {
        case e: StackOverflowError =>
          e.printStackTrace()
          sys error s"### StackOverflowError ### in tree.makeNodeJsonWriter($alwaysChildren)"
      }
    }

  def destString(dests: Map[Pos, List[Pos]]): String = {
    val sb    = new java.lang.StringBuilder(80)
    var first = true
    dests foreach { case (orig, dests) =>
      if (first) first = false
      else sb append " "
      sb append orig.piotr
      dests foreach { sb append _.piotr }
    }
    sb.toString
  }

  implicit val destsJsonWriter: Writes[Map[Pos, List[Pos]]] = Writes { dests =>
    JsString(destString(dests))
  }

  val partitionTreeJsonWriter: Writes[Node] = Writes { node =>
    JsArray {
      node.mainlineNodeList.map(minimalNodeJsonWriter.writes)
    }
  }
}
