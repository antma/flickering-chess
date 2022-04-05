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
    System.err.println(javaClass.classLoader.getResource(s).path)
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

class ChessBoard(pos: Position) : JFrame() {
  val cells = Array(64) {
    val i = it / 8
    val j = it % 8
    Cell((7 - i) * 8 + j, pos)
  }
  fun selectedCell(): Cell? = cells.find { it.isSelected() }
  init {
    defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    setSize(800, 800)
    setLayout(GridLayout(8, 8))
    for (b in cells) add(b)
    isVisible = true
    for (q in cells) {
      q.addActionListener { _ -> 
        val c = selectedCell()
        if (c != null) {
          val t0 = StringBuilder(4).apply {
            append(c.s)
            append(q.s)
          }.toString()
          val t = if (pos.isPromotion(t0)) t0 + "q" else t0
          if (pos.doSANMove(t) != null) {
            System.err.println(t)
            val v = pos.validate()
            require(v == null)
            c.click()
            for (p in cells) p.updatePiece(pos.board[p.cell128()])
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

private fun createAndShowGUI() {
  val p = Position()
  ChessBoard(p)
  /*
  val frame = JFrame().apply {
    defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    setSize(600, 600)
  }
  frame.isVisible = true
  */
}

fun main() {
  SwingUtilities.invokeLater(::createAndShowGUI)
}
