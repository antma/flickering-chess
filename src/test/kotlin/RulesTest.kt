import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

import com.github.antma.flickering_chess.Position
import com.github.antma.flickering_chess.Engine
import com.github.antma.flickering_chess.Game
import com.github.antma.flickering_chess.GameResult

fun Position.doMoves(s: String) {
  for (t in s.split(' ')) {
    assertTrue(doSANMove(t) != null, "doSANMove ${t} failed")
    val v = validate()
    if (v != null) assertTrue(false, "validation failed with message '${v}' after move ${t}")
  }
}

fun Game.doMoves(s: String) {
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
    p.doMoves("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 e1e2 e8e7 e2e1 e7e8")
    assertTrue(p.doSANMove("e1g1") == null)
    p.doMoves("b1c3")
    assertTrue(p.doSANMove("e8g8") == null)
  }
  @Test
  fun testIllegalMove() {
    val p = Position()
    p.doMoves("e2e4 e7e5 e1e2 e8e7 e2e3 e7e6")
    assertTrue(p.doSANMove("e3f4") == null)
  }
  @Test
  fun testCheckMate() {
    val p = Position()
    p.doMoves("e2e4 e7e5 f1c4 f8c5 d1h5 g8f6 h5f7")
    assertTrue(p.isCheck())
    assertTrue(p.isCheckMate())
  }
  //@Test
  fun testEngine() {
    val p = Position()
    p.doMoves("e2e4 a7a6 d2d4 c7c6 g1f3 b7b5 c2c4 b5c4 f1c4 d7d6 e1g1 e7e6 f1e2 a6a5 d4d5 d8b6 d5e6")
    val e = Engine(16)
    val m = e.root_search(p, max_depth = 5, max_nodes = Int.MAX_VALUE)
    val v = p.validate()
    if (v != null) assertTrue(false, "validation failed with message '${v}' after root search")
    assertTrue(p.doSANMove(m.first.san()) != null)
  }
  @Test
  fun testIsLegal() {
    val p = Position()
    p.doMoves("e2e4 a7a6 d2d4 c7c6 g1f3 b7b5 b1c3 g8f6 e1e2 f6d4")
    assertTrue(p.doSANMove("c3d2") == null)
  }
  //@Test
  fun testEngine2() {
    val p = Position()
    p.doMoves("e2e4 a7a6 d2d4 c7c6 g1f3 b7b5 b1c3")
    val e = Engine(16)
    val m = e.root_search(p, max_depth = 5, max_nodes = Int.MAX_VALUE)
    val v = p.validate()
    if (v != null) assertTrue(false, "validation failed with message '${v}' after root search")
    assertTrue(p.doSANMove(m.first.san()) != null)
  }
  @Test
  fun testEngine3() {
    val p = Position()
    p.doMoves("e2e4 a7a6 d2d4 a6a5 g1f3 a5a4 f1c4 b7b6 e1g1 c7c6 a2a3 b6b5 c4d3 d7d6 f1e2 e7e6")
    p.doMoves("b1c3 f7f6 e4e5 d6e5 d4e5 f6e5 c3e5 a8a6 d1e1 d8c7 c1f4 f8d6 c2c4 b5c4 d3c4 a6b6")
    p.doMoves("c4d3 c6c5 a1d1 d6e5 f4e5 c7a7 f3h5 h7h6 d3g6 h8h7 e2d2 b8c6 d2d8 e8f7 d8c8 b6c8")
    p.doMoves("g6f4 f7g6 h5h3 h6h5 e1e2 h7h8 e2c2 g6f7 f4g5 f7f8 c2g6 c8d7 e5d6 d7d6 d1d6 c6e8")
    p.doMoves("d6e6 a7d7 h3d3 c5c4 d3c5 e8d6 c5d6 d7e6 g6e6 c4c3 b2c3 g7g6 c3c4 f8g7 e6f7 g7h6")
    p.doMoves("h2h4 g8f6 f7f6 h8e8 g5f7 e8e1 g1h2 e1h1 h2h1")
    val e = Engine(16)
    val m = e.root_search(p, max_depth = 6, max_nodes = Int.MAX_VALUE)
    val v = p.validate()
    if (v != null) assertTrue(false, "validation failed with message '${v}' after root search")
    assertTrue(p.doSANMove(m.first.san()) != null)
  }
  @Test
  fun testUndoCastling() {
    val p = Position()
    p.doMoves("e2e4 e7e5 e1e2 d7d6 e2e1 c8g4 e1e2 b8c6 e2e1 d8e7 e1e2")
    val u = p.doSANMove("e8c8")
    assertTrue(u != null)
    p.undoMove(u)
    val v = p.validate()
    if (v != null) assertTrue(false, "validation failed with message '${v}' after undo castling, fen = ${p.fen()}")
  }
  @Test
  fun testStalemate() {
    val g = Game()
    g.doMoves("e2e4 b8c6 g1f3 d7d5 e4e5 e7e6 d2d4 d8e7 c2c3 g7g6 f1d3 a7a6 e1g1 f7f5 e5f6 g8f6 f1e2 g6g5 h2h3 e6e5 d4e5 f6g7")
    g.doMoves("c1e3 g5g4 h3g4 h8g8 d3h7 e7e6 g4g5 e6f5 h7h6 g7e6 h6f6 e6f4 e3f4 f5f4 b1d2 c8g4 f3g4 f4g4 d2f3 g4c4 e2d2 e8f7")
    g.doMoves("f3d5 c6d5 d2d5 f8c5 d5d7 f7f8 d1h5 a6a5 a1d1 a8e8 d1d5 c5f2 g1f2 c4f4 f2g1 f4e3 g1h1 e3c1 d5d1 c1d1 d7d1 g8f6")
    g.doMoves("e5f6 b7b6 h5e8 f8e8 g5g6 c7c5 d1d6 a5a4 d6b6 a4a3 b2a3 e8d8 f6f7 d8e7 b6a4 c5c4 a4c4 e7f8 h1h2 f8g7 h2g3 g7f8")
    g.doMoves("g3f4 f8g7 f4f5 g7f8 f5f6")
    assertEquals(g.getResult(), GameResult.Stalemate)
  }
  @Test
  fun testIsLegalPromotion() {
    val p = Position()
    p.doMoves("e2e4 a7a5 e4e5 f7f5 e5f6 a5a4 f6e7 a4a3")
    assertTrue(p.isLegalPromotion("e7d8"))
    assertTrue(!p.isLegalPromotion("e7e8"))
    assertTrue(p.isLegalPromotion("e7f8"))
  }
  fun testMobility() {
    val p = Position()
    p.doMoves("g1f3 g8f6 c2c4 b8c6 b1c3 e7e6 e2e3 d7d5 d2d4 f6g5 c4d5 e6d5 f1e2 d8f6")
    p.doMoves("e1g1 f8d6 a2a3 g5f3 e2f3 c8f5 b2b4 a7a6 d1e2")
    //e8d7
  }
}
