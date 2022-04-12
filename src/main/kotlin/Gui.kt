import java.awt.GridLayout
import javax.swing.*
import java.awt.Color
import com.github.antma.flickering_chess.*
import kotlin.collections.HashMap
import kotlin.math.sign

object Pieces {
  fun piece2path(p: Int): java.net.URL {
    val s = StringBuilder().apply {
      append("resources/")
      append(when(p) {
        -KING -> "BlackKing"
        -QUEEN -> "BlackQueen"
        -ROOK -> "BlackRook"
        -BISHOP -> "BlackBishop"
        -KNIGHT -> "BlackKnight"
        -PAWN -> "BlackPawn"
        PAWN -> "WhitePawn"
        KNIGHT -> "WhiteKnight"
        BISHOP -> "WhiteBishop"
        ROOK -> "WhiteRook"
        QUEEN -> "WhiteQueen"
        KING -> "WhiteKing"
        else -> ""
      })
      append(".png")
    }.toString()
    return javaClass.classLoader.getResource(s)
  }
  val m = HashMap<Int, ImageIcon>().apply {
    for (i in -KING .. -PAWN) {
      put(i, ImageIcon(piece2path(i)))
    }
    for (i in PAWN .. KING) {
      put(i, ImageIcon(piece2path(i)))
    }
    put(0, ImageIcon())
  }
}

class Cell(cell: Int, val pos: Position): JButton() {
  val row = cell / 8
  val col = cell % 8
  fun cell128(): Int = row * 16 + col
  val s = StringBuilder(2).apply {
    append((col + 97).toChar())
    append((row + 49).toChar())
  }.toString()
  var piece = 0
  val isBlack: Boolean
    get() = ((row + col) % 2) == 0
  fun backcolor() = if (isBlack) Color.DARK_GRAY else Color.LIGHT_GRAY
  init {
    background = backcolor()
    val x = row * 16 + col
    piece = pos.board[x]
    if (piece != 0) icon = Pieces.m.get(piece)!!
  }
  fun changeSelection(v: Boolean) {
    setSelected(v)
    if (v) background = Color.YELLOW
    else background = backcolor()
  }
  fun click() {
    if (!isSelected() && piece * pos.side <= 0) return
    changeSelection(!isSelected())
  }
  fun updatePiece(p: Int) {
    if (piece == p) return
    piece = p
    icon = Pieces.m.get(p)!!
  }
}

enum class UIState {
  UserMove,
  ComputeMove,
  Promotion,
  GameFinished,
}

class PromotionPiece(c: PromotionPieceChooser, p: Int): JButton() {
  init {
    icon = Pieces.m.get(p)!!
    setSize(100, 100)
    addActionListener {
      c.isVisible = false
      c.chessboard.promoteTo(p)
    }
  }
}

class PromotionPieceChooser(val chessboard: ChessBoard) : JFrame() {
  init {
    title = "Promote to"
    setSize(400, 140)
    setLayout(GridLayout(1, 4))
    add(PromotionPiece(this, QUEEN))
    add(PromotionPiece(this, ROOK))
    add(PromotionPiece(this, BISHOP))
    add(PromotionPiece(this, KNIGHT))
  }
}

class ChessBoard() : JFrame() {
  var game = Game()
  var max_nodes = 1
  val chooser = PromotionPieceChooser(this)
  val engine = Engine(16)
  val cells = Array(64) {
    val i = it / 8
    val j = it % 8
    Cell((7 - i) * 8 + j, game.pos)
  }
  fun selectedCell(): Cell? = cells.find { it.isSelected() }
  var state = UIState.UserMove
  var promotion_move: String? = null
  private fun deselectCells() {
    selectedCell()?.changeSelection(false)
  }
  private fun updateBoard() {
    for (p in cells) p.updatePiece(game.pos.board[p.cell128()])
  }
  private fun adjudicateGame(): Boolean {
    val r = game.getResult()
    if (r != null) {
      title = when(r) {
        GameResult.WhiteWins -> "White wins by checkmate."
        GameResult.BlackWins -> "Black wins by checkmate."
        GameResult.Stalemate -> "Draw by stalemate."
        GameResult.FiftyMoveRule -> "Draw by 50 moves rule."
        GameResult.ThreeFold -> "Draw by 3-fold repetition."
        GameResult.InsufficientMaterial -> "Draw by insufficient material."
      }
      state = UIState.GameFinished
      return true
    }
    return false
  }
  fun newGame(level: Int) {
    state = UIState.GameFinished
    deselectCells()
    max_nodes = when(level) {
      1 -> 1
      2 -> 100
      else -> 10000
    }
    promotion_move = null
    game = Game()
    updateBoard()
    state = UIState.UserMove
    title = "Level $level"
  }
  fun promoteTo(p: Int) {
    if (state != UIState.Promotion) return
    require(promotion_move != null)
    val t = promotion_move + pieceToPromotionCharacter(p)
    promotion_move = null
    require(game.doSANMove(t))
    afterUserMove(t)
  }
  fun afterUserMove(t: String) {
    deselectCells()
    System.err.println(t)
    val v = game.pos.validate()
    require(v == null)
    updateBoard()
    if (!adjudicateGame()) {
      state = UIState.ComputeMove
      SwingUtilities.invokeLater {
        engine.setParams(max_depth=100, max_nodes=max_nodes, use_qsearch = true)
        val p = engine.root_search(game.pos)
        require(game.doSANMove(p.first.san()))
        System.err.println(p.first.san())
        updateBoard()
        if (!adjudicateGame()) state = UIState.UserMove
      }
    }
  }
  private fun createMenu() {
    val mb = JMenuBar()
    val n = JMenu("New")
    for (l in 1 .. 3) {
      val i = JMenuItem("New game (Level $l)")
      i.addActionListener {
        newGame(l)
      }
      n.add(i)
    }
    mb.add(n)
    jMenuBar = mb
  }
  init {
    defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    setSize(800, 800)
    setLayout(GridLayout(8, 8))
    for (b in cells) add(b)
    isVisible = true
    for (q in cells) {
      q.addActionListener { _ ->
        if (state == UIState.UserMove) {
          val c = selectedCell()
          if (c != null) {
            val t = StringBuilder(4).apply {
              append(c.s)
              append(q.s)
            }.toString()
            if (game.pos.isLegalPromotion(t)) {
              promotion_move = t
              state = UIState.Promotion
              chooser.isVisible = true
            } else if (game.doSANMove(t)) {
              afterUserMove(t)
            } else if (c === q) {
              c.click()
            } else if (q.piece.sign * c.piece.sign > 0) {
              c.click()
              q.click()
            }
          } else {
            q.click()
          }
        }
      }
    }
    createMenu()
  }
}

private fun createAndShowGUI() {
  val cb = ChessBoard()
  cb.newGame(level = 1)
}

fun main() {
  SwingUtilities.invokeLater(::createAndShowGUI)
}
