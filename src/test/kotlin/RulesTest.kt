import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.junit.Test

import com.github.antma.flickering_chess.Position
import com.github.antma.flickering_chess.Engine

fun doMoves(pos: Position, s: String) {
  for (t in s.split(' ')) {
    assertTrue(pos.doSANMove(t) != null, "doSANMove ${t} failed")
    val v = pos.validate()
    if (v != null) assertTrue(false, "validation failed with message '${v}' after move ${t}")
  }
}

class RulesTest {
  @Test
  fun testCastling() {
    val p = Position()
    doMoves(p, "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 e1e2 e8e7 e2e1 e7e8")
    assertTrue(p.doSANMove("e1g1") == null)
    doMoves(p, "b1c3")
    assertTrue(p.doSANMove("e8g8") == null)
  }
  @Test
  fun testIllegalMove() {
    val p = Position()
    doMoves(p, "e2e4 e7e5 e1e2 e8e7 e2e3 e7e6")
    assertTrue(p.doSANMove("e3f4") == null)
  }
  @Test
  fun testCheckMate() {
    val p = Position()
    doMoves(p, "e2e4 e7e5 f1c4 f8c5 d1h5 g8f6 h5f7")
    assertTrue(p.isCheck())
    assertTrue(p.isCheckMate())
  }
  //@Test
  fun testEngine() {
    val p = Position()
    doMoves(p, "e2e4 a7a6 d2d4 c7c6 g1f3 b7b5 c2c4 b5c4 f1c4 d7d6 e1g1 e7e6 f1e2 a6a5 d4d5 d8b6 d5e6")
    val e = Engine(16)
    val m = e.root_search(p, max_depth = 5, max_nodes = Int.MAX_VALUE)
    val v = p.validate()
    if (v != null) assertTrue(false, "validation failed with message '${v}' after root search")
    assertTrue(p.doSANMove(m.first.san()) != null)
  }
  @Test
  fun testIsLegal() {
    val p = Position()
    doMoves(p, "e2e4 a7a6 d2d4 c7c6 g1f3 b7b5 b1c3 g8f6 e1e2 f6d4")
    assertTrue(p.doSANMove("c3d2") == null)
  }
  //@Test
  fun testEngine2() {
    val p = Position()
    doMoves(p, "e2e4 a7a6 d2d4 c7c6 g1f3 b7b5 b1c3")
    val e = Engine(16)
    val m = e.root_search(p, max_depth = 5, max_nodes = Int.MAX_VALUE)
    val v = p.validate()
    if (v != null) assertTrue(false, "validation failed with message '${v}' after root search")
    assertTrue(p.doSANMove(m.first.san()) != null)
  }
  @Test
  fun testUndoCastling() {
    val p = Position()
    doMoves(p, "e2e4 e7e5 e1e2 d7d6 e2e1 c8g4 e1e2 b8c6 e2e1 d8e7 e1e2")
    val u = p.doSANMove("e8c8")
    assertTrue(u != null)
    p.undoMove(u)
    val v = p.validate()
    if (v != null) assertTrue(false, "validation failed with message '${v}' after undo castling, fen = ${p.fen()}")
  }
}
