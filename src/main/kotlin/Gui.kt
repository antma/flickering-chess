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
  GameFinished
}

class ChessBoard(val game: Game) : JFrame() {
  val engine = Engine(16)
  val cells = Array(64) {
    val i = it / 8
    val j = it % 8
    Cell((7 - i) * 8 + j, game.pos)
  }
  fun selectedCell(): Cell? = cells.find { it.isSelected() }
  var state = UIState.UserMove
  private fun updateBoard() {
    for (p in cells) p.updatePiece(game.pos.board[p.cell128()])
  }
  private fun adjudicateGame(): Boolean {
    val r = game.getResult()
    if (r != null) {
      title = r
      state = UIState.GameFinished
      return true
    }
    return false
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
            val t0 = StringBuilder(4).apply {
              append(c.s)
              append(q.s)
            }.toString()
            val t = if (game.pos.isPromotion(t0)) t0 + "q" else t0
            if (game.doSANMove(t)) {
              System.err.println(t)
              val v = game.pos.validate()
              require(v == null)
              c.click()
              updateBoard()
              if (!adjudicateGame()) {
                state = UIState.ComputeMove
                SwingUtilities.invokeLater {
                  val p = engine.root_search(game.pos, max_depth=100, max_nodes=10000)
                  require(game.doSANMove(p.first.san()))
                  System.err.println(p.first.san())
                  updateBoard()
                  if (!adjudicateGame()) state = UIState.UserMove
                }
              }
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
  }
}

private fun createAndShowGUI() {
  ChessBoard(Game())
}

fun main() {
  SwingUtilities.invokeLater(::createAndShowGUI)
}
