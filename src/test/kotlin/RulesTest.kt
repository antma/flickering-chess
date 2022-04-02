import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.junit.Test

import com.github.antma.flickering_chess.Position

class RulesTest {
  @Test
  fun testPrimesLarge() {
    val p = Position()
    assertTrue(p.doSANMove("e2e4") != null)
    assertTrue(p.doSANMove("e7e5") != null)
    assertTrue(p.doSANMove("g1f3") != null)
    assertTrue(p.doSANMove("b8c6") != null)
    assertTrue(p.doSANMove("f1c4") != null)
    assertTrue(p.doSANMove("f8c5") != null)
    assertTrue(p.doSANMove("e1e2") != null)
    assertTrue(p.doSANMove("e8e7") != null)
    assertTrue(p.doSANMove("e2e1") != null)
    assertTrue(p.doSANMove("e7e8") != null)
    assertTrue(p.doSANMove("e1g1") == null)
  }
}
