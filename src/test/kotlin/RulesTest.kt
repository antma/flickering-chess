import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.junit.Test

import com.github.antma.flickering_chess.Position

fun doMoves(pos: Position, s: String) {
  for (t in s.split(' ')) {
    assertTrue(pos.doSANMove(t) != null, "doSANMove ${t} failed")
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
}
