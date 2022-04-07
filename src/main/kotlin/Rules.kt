package com.github.antma.flickering_chess
import kotlin.math.abs
import kotlin.math.sign
import kotlin.collections.mutableSetOf

const val PAWN = 1
const val KNIGHT = 2
const val BISHOP = 3
const val ROOK = 4
const val QUEEN = 5
const val KING = 6

private val knight_moves = intArrayOf(-33, -31, -18, -14, 14, 18, 31, 33)
private val bishop_moves = intArrayOf(-17, -15, 15, 17)
private val rook_moves = intArrayOf(-16, -1, 1, 16)
private val queen_moves = intArrayOf(-17, -16, -15, -1, 1, 15, 16, 17)
private val white_pawn_captures = intArrayOf(15, 17)
private val black_pawn_captures = intArrayOf(-15, -17)

private fun isFlickeringPiece(x: Int): Boolean {
  val p = abs(x)
  return p == ROOK || p == KNIGHT || p == BISHOP
}
private fun inside(x: Int) = (x and 0x88) == 0

const val CAPTURE = 8
const val PROMOTION = 16
const val CASTLING = 32
const val EN_PASSANT = 64
const val PAWN_JUMP = 128

private val tbl_material_score_delta = intArrayOf(0, 100, 300, 300, 500, 1000)

object Zobrist {
  val a = LongArray(128 * 13 + 16 + 8 + 1)
  init {
    val rnd = kotlin.random.Random(239)
    for (i in a.indices) a[i] = rnd.nextLong()
  }
}

data class Move(val from: Int, val to: Int, val flags: Int) {
  fun san(): String =
    StringBuilder(5).apply {
      append((((from and 15) + 97)).toChar())
      append((((from shr 4) + 49)).toChar())
      append((((to and 15) + 97)).toChar())
      append((((to shr 4) + 49)).toChar())
      if ((flags and PROMOTION) != 0) {
        append(when (flags and 7) {
          KNIGHT -> 'n'
          BISHOP -> 'b'
          ROOK -> 'r'
          QUEEN -> 'q'
          else -> '?'
        })
      }
    }.toString()
}
class UndoMove(val move: Move, val piece_from: Int, val piece_to: Int, val castle: Int, val jump: Int, val material_score: Int, val hc: Long, val fifty_move_rule: Int)

class Position {
  val board = IntArray(128)
  var side = 1
  var castle = 15
  var jump = -1
  var wk = 0x04
  var bk = 0x74
  var material_score = 0
  private var hc = 0L
  private var fifty_move_rule = 100
  private fun hcUpdate(k: Int) {
    val p = board[k]
    if (p != 0) {
      hc = hc xor Zobrist.a[(KING + p) * 128 + k]
    }
  }
  init {
    for (i in 0 .. 7) {
      board[i + 16] = PAWN
      board[i + 16 * 6] = -PAWN
    }
    board[0] = ROOK
    board[7] = ROOK
    board[0x70] = -ROOK
    board[0x77] = -ROOK
    board[1] = KNIGHT
    board[6] = KNIGHT
    board[0x71] = -KNIGHT
    board[0x76] = -KNIGHT
    board[2] = BISHOP
    board[5] = BISHOP
    board[0x72] = -BISHOP
    board[0x75] = -BISHOP
    board[3] = QUEEN
    board[0x73] = -QUEEN
    board[4] = KING
    board[0x74] = -KING
    for (i in listOf(0, 1, 6, 7)) for (j in 0 until 8) {
      hcUpdate(i * 16 + j)
    }
  }
  fun hash(): Long {
    var x = hc xor Zobrist.a[1672 + castle]
    if (jump >= 0) x = x xor Zobrist.a[1664 + jump]
    if (side < 0) x = x xor Zobrist.a[1688]
    return x
  }
  fun cell(x: Int) = if (inside(x)) 0 else board[x]
  fun role(x: Int): Int {
    val p = board[x]
    if (isFlickeringPiece(p)) {
      return if (p > 0) {
        when(x and 7) {
          0, 7 -> ROOK
          1, 6 -> KNIGHT
          2, 5 -> BISHOP
          else -> p
        }
      } else {
        return when(x and 7) {
          0, 7 -> -ROOK
          1, 6 -> -KNIGHT
          2, 5 -> -BISHOP
          else -> p
        }
      }
    }
    return p
  }
  private fun enumeratePieceMoves(x: Int, dir: IntArray, sliding: Boolean, op: (Move) -> Boolean): Move? {
    for (delta in dir) {
      var y = x + delta
      while(inside(y)) {
        val p = board[y]
        if (p == 0) {
          val m = Move(x, y, 0)
          if (op(m)) return m
          if (!sliding) break
          y += delta
        } else if (p * side < 0) {
          val m = Move(x, y, CAPTURE)
          if (op(m)) return m
          break
        } else {
          break
        }
      }
    }
    return null
  }
  private fun enumeratePawnMoves(x: Int, op: (Move) -> Boolean): Move? {
    val i = x shr 4
    val j = x and 15
    val rank_before_promotion = if (side > 0) 6 else 1
    val forth_rank = if (side > 0) 4 else 3
    val first_rank = rank_before_promotion xor 7
    val captures = if (side > 0) white_pawn_captures else black_pawn_captures
    val y = x + 16 * side
    if (board[y] == 0) {
      if (i == rank_before_promotion) {
        for (k in KNIGHT .. QUEEN) {
          val m = Move(x, y, k + PROMOTION)
          if (op(m)) return m
        }
      } else {
        val m = Move(x, y, 0)
        if (op(m)) return m
      }
    }
    if (i == first_rank && board[x + 16 * side] == 0 && board[x + 32 * side] == 0) {
      val m = Move(x, x + 32 * side, PAWN_JUMP)
      if (op(m)) return m
    }
    if (jump >= 0 && i == forth_rank && abs(j - jump) == 1) {
      val m = Move(x, i * 16 + 16 * side + jump, CAPTURE + EN_PASSANT)
      if (op(m)) return m
    }
    for (delta in captures) {
      val z = x + delta
      if (!inside(z)) continue
      if (board[z] * side < 0) {
        if (i == rank_before_promotion) {
          for (k in KNIGHT .. QUEEN) {
            val m = Move(x, z, k + (PROMOTION + CAPTURE))
            if (op(m)) return m
          }
        } else {
          val m = Move(x, z, CAPTURE)
          if (op(m)) return m
        }
      }
    }
    return null
  }
  private fun go(x: Int, delta: Int): Int {
    var y = x + delta
    var f = true
    while(inside(y)) {
      val p = role(y)
      if (p != 0) {
        if (!f && (p == KING || p == -KING)) return 0
        return p
      }
      f = false
      y += delta
    }
    return 0
  }
  private fun isAttacked(x: Int, s: Int): Boolean {
    for (delta in rook_moves) {
      val q = go(x, delta)
      if (s * q <= 0) continue
      val w = abs(q)
      if (w == ROOK || w == QUEEN || w == KING) return true
    }
    for (delta in bishop_moves) {
      val q = go(x, delta)
      if (s * q <= 0) continue
      val w = abs(q)
      if (w == BISHOP || w == QUEEN || w == KING) return true
    }
    for (delta in knight_moves) {
      val y = x + delta
      if (!inside(y)) continue
      val q = role(y)
      if (s * q <= 0) continue
      if (q == KNIGHT) return true
    }
    for (delta in white_pawn_captures) {
      val y = x - delta * s
      if (inside(y) && board[y] == PAWN * s) return true
    }
    return false
  }
  fun enumerateMoves(op: (Move) -> Boolean): Move? {
    for (i in 0 until 8) for (j in 0 until 8) {
      val x = i * 16 + j
      val p = role(x) * side
      if (p <= 0) continue
      when(p) {
        PAWN, -PAWN -> {
          val m = enumeratePawnMoves(x, op)
          if (m != null) return m
        }
        KNIGHT, -KNIGHT -> {
          val m = enumeratePieceMoves(x, knight_moves, false, op)
          if (m != null) return m
        }
        BISHOP, -BISHOP -> {
          val m = enumeratePieceMoves(x, bishop_moves, true, op)
          if (m != null) return m
        }
        ROOK, -ROOK -> {
          val m = enumeratePieceMoves(x, rook_moves, true, op)
          if (m != null) return m
        }
        QUEEN, -QUEEN -> {
          val m = enumeratePieceMoves(x, queen_moves, true, op)
          if (m != null) return m
        }
        KING, -KING -> {
          val m = enumeratePieceMoves(x, queen_moves, false, op)
          if (m != null) return m
        }
      }
    }
    //castling
    if (side > 0 && board[0x04] == KING && !isAttacked(0x04, -1)) {
      if ((castle and 1) != 0) {
        if (board[0x00] == ROOK && board[0x01] == 0 && board[0x02] == 0 && board[0x03] == 0 && !isAttacked(0x03, -1)) {
          val m = Move(0x04, 0x02, CASTLING)
          if (op(m)) return m
        }
      }
      if ((castle and 2) != 0) {
        if (board[0x07] == ROOK && board[0x05] == 0 && board[0x06] == 0 && !isAttacked(0x05, -1)) {
          val m = Move(0x04, 0x06, CASTLING)
          if (op(m)) return m
        }
      }
    } else if (side < 0 && board[0x74] == -KING && !isAttacked(0x74, 1)) {
      if ((castle and 4) != 0) {
        if (board[0x70] == -ROOK && board[0x71] == 0 && board[0x72] == 0 && board[0x73] == 0 && !isAttacked(0x73, 1)) {
          val m = Move(0x74, 0x72, CASTLING)
          if (op(m)) return m
        }
      }
      if ((castle and 8) != 0) {
        if (board[0x77] == -ROOK && board[0x75] == 0 && board[0x76] == 0 && !isAttacked(0x75, 1)) {
          val m = Move(0x74, 0x76, CASTLING)
          if (op(m)) return m
        }
      }
    }
    return null
  }
  fun isLegal(): Boolean = if (side < 0) !isAttacked(wk, -1) else !isAttacked(bk, 1)
  fun isCheck(): Boolean = if (side > 0) isAttacked(wk, -1) else isAttacked(bk, 1)
  fun materialScoreDelta(m: Move): Int {
    if ((m.flags and EN_PASSANT) != 0) {
      return tbl_material_score_delta[PAWN]
    } else {
      var ms = 0
      if ((m.flags and PROMOTION) != 0) {
        ms += tbl_material_score_delta[m.flags and 7] - tbl_material_score_delta[PAWN]
      }
      val p = board[m.to]
      if (p != 0) {
        ms += tbl_material_score_delta[abs(p)]
      }
      return ms
    }
  }
  fun doMove(m: Move): UndoMove {
    //System.err.println("doMove: " + m.san())
    val p = board[m.from]
    if (p == KING) wk = m.to
    else if (p == -KING) bk = m.to
    val u = UndoMove(m, p, board[m.to], castle, jump, material_score, hc, fifty_move_rule)
    material_score += side * materialScoreDelta(m)
    hcUpdate(m.from)
    hcUpdate(m.to)
    board[m.from] = 0
    board[m.to] = if ((m.flags and PROMOTION) != 0) p.sign * (m.flags and 7) else p
    hcUpdate(m.to)
    jump = if ((m.flags and PAWN_JUMP) != 0) (m.from and 15) else -1
    when (m.from) {
      0x00 -> castle = castle and 14
      0x04 -> castle = castle and 12
      0x07 -> castle = castle and 13
      0x70 -> castle = castle and 11
      0x74 -> castle = castle and 3
      0x77 -> castle = castle and 7
    }
    if ((m.flags and CASTLING) != 0) {
      when (m.to) {
        0x02 -> {
          hcUpdate(0x00)
          board[0x00] = 0
          board[0x03] = ROOK
          hcUpdate(0x03)
        }
        0x06 -> {
          hcUpdate(0x07)
          board[0x07] = 0
          board[0x05] = ROOK
          hcUpdate(0x05)
        }
        0x72 -> {
          hcUpdate(0x70)
          board[0x00] = 0
          board[0x73] = -ROOK
          hcUpdate(0x73)
        }
        0x76 -> {
          hcUpdate(0x77)
          board[0x77] = 0
          board[0x75] = -ROOK
          hcUpdate(0x75)
        }
      }
    }
    if ((m.flags and EN_PASSANT) != 0) {
      val i = m.from shr 4
      val j = m.to and 15
      val k = i * 16 + j
      hcUpdate(k)
      board[k] = 0
    }
    fifty_move_rule--
    if ((m.flags and CAPTURE) != 0 || abs(u.piece_from) == PAWN) fifty_move_rule = 100
    side *= -1
    return u
  }
  fun undoMove(u: UndoMove) {
    val m = u.move
    //System.err.println("undoMove: " + m.san())
    if (u.piece_from == KING) wk = m.from
    else if (u.piece_from == -KING) bk = m.from
    board[m.from] = u.piece_from
    board[m.to] = u.piece_to
    castle = u.castle
    jump = u.jump
    material_score = u.material_score
    hc = u.hc
    fifty_move_rule = u.fifty_move_rule
    if ((m.flags and CASTLING) != 0) {
      when (m.to) {
        0x02 -> {
          board[0x03] = 0
          board[0x00] = ROOK
        }
        0x06 -> {
          board[0x05] = 0
          board[0x07] = ROOK
        }
        0x72 -> {
          board[0x03] = 0
          board[0x70] = -ROOK
        }
        0x76 -> {
          board[0x75] = 0
          board[0x77] = -ROOK
        }
      }
    }
    if ((m.flags and EN_PASSANT) != 0) {
      val i = m.from shr 4
      val j = m.to and 15
      board[i * 16 + j] = side * PAWN
    }
    side *= -1
  }
  fun findPiece(p: Int): Int {
    for (i in (0 until 8 * 16).step(16))
      for (j in i until (i + 8))
        if (board[j] == p)
          return j
    return -1
  }
  fun doSANMove(san: String): UndoMove? {
    System.err.println("doSANMove: $san")
    val m = enumerateMoves {
      it.san() == san
    }
    if (m == null) return null
    val u = doMove(m)
    if (!isLegal()) {
      undoMove(u)
      return null
    }
    return u
  }
  fun validate(): String? {
    var ms = 0
    var h = 0L
    for (i in 0 until 8) for (j in 0 until 8) {
      val k = 16 * i + j
      val p = board[k]
      if (p != 0) {
        val q = abs(p)
        if (q != KING) ms += p.sign * tbl_material_score_delta[q]
        h = h xor Zobrist.a[(KING + p) * 128 + k]
      }
    }
    if (material_score != ms) return "Material Score expected - ${ms}, field value - ${material_score}"
    if (hc != h) return "hc expected - ${h}, field value - ${hc}"
    return null
  }
  fun isPromotion(san: String): Boolean {
    val m = enumerateMoves {
      (it.flags and PROMOTION) != 0 && it.san().startsWith(san)
    }
    return m != null
  }
  fun isCheckMate(): Boolean {
    if (!isCheck()) return false
    return enumerateMoves {
      val u = doMove(it)
      val res = isLegal()
      undoMove(u)
      res
    } == null
  }
  fun fiftyMoveDraw() = fifty_move_rule <= 0
}

class Game {
  private val moves = ArrayList<Move>()
  private val h = ArrayList<Long>()
  val pos = Position()
  init {
    h.add(pos.hash())
  }
  fun getResult(): String? {
    if (pos.isCheckMate()) return (if (pos.side < 0) "White" else "Black") + " wins by checkmate."
    if (pos.fiftyMoveDraw()) return "Draw by 50-move rule"
    val x = h.lastOrNull()!!
    if (h.count { it == x } >= 3) return "Draw by 3-fold repetition"
    return null
  }
  fun doSANMove(san: String): Boolean {
    val p = pos.doSANMove(san)
    if (p == null) return false
    moves.add(p.move)
    h.add(pos.hash())
    return true
  }
}

const val MATE_SCORE: Int = 10_000
const val PLY: Int = 10
const val CHECK_EXTENTION = PLY

const val LOWERBOUND = 1
const val UPPERBOUND = 2

class CacheSlot(val hc: Long, val move: Move?, val depth: Int, val score: Int, val flags: Int, val age: Int)
class Cache(bits: Int) {
  private val size = 1 shl bits
  private val mask = size - 1
  private val cache = arrayOfNulls<CacheSlot>(size)
  private var generation = 0
  fun getGeneration(): Int = generation
  fun incGeneration() { generation++ }
  fun store(slot: CacheSlot) {
    val idx = slot.hc.toInt() and mask
    val p = cache[idx]
    if (p == null || slot.depth >= p.depth || slot.age != generation) cache[idx] = slot
  }
  fun probe(hc: Long): CacheSlot? {
    val idx = hc.toInt() and mask
    val p = cache[idx]
    if (p == null || p.hc != hc) return null
    return p
  }
}

class Engine(bits: Int) {
  val cache = Cache(bits)
  var nodes = 0
  val h = mutableSetOf<Long>()
  fun eval(pos: Position): Int {
    nodes++
    return pos.material_score * pos.side
  }
  /*
  fun qsearch(pos: Position, alpha: Int, beta: Int, ply: Int): Int {
    return pos.material_score * pos.side
  }
  */
  fun search(pos: Position, alpha: Int, beta: Int, ply: Int, depth: Int): Int {
    val hc = pos.hash()
    //draw
    if ((hc in h) || pos.fiftyMoveDraw()) return 0
    val check = pos.isCheck()
    val d = if (check) depth + CHECK_EXTENTION else depth
    //if (d <= 0) return qsearch(pos, alpha, beta, ply)
    if (d <= 0) return eval(pos)
    val p = cache.probe(hc)
    if (p != null && p.depth >= depth) {
      when(p.flags) {
        LOWERBOUND -> if (p.score >= beta) return beta
        UPPERBOUND -> if (p.score <= alpha) return alpha
        else -> return p.score
      }
    }
    val l = mutableListOf<Pair<Int, Move>>()
    pos.enumerateMoves {
      val h = if (p != null && p.move == it) {
        MATE_SCORE
      } else {
        pos.materialScoreDelta(it)
      }
      l.add(h to it)
      false
    }
    l.sortByDescending { it.first }
    h.add(hc)
    var best_score = alpha
    var legal_moves = 0
    var best_move: Move? = null
    for (m in l) {
      val u = pos.doMove(m.second)
      if (pos.isLegal()) {
        legal_moves++
        val w = -search(pos, -beta, -best_score, ply + 1, depth - PLY)
        if (best_score < w) {
          best_score = w
          best_move = m.second
          if (best_score >= beta) {
            assert(m.second == u.move)
            pos.undoMove(u)
            break
          }
        }
      }
      assert(m.second == u.move)
      pos.undoMove(u)
    }
    if (best_move != null) {
      if (best_score < beta) cache.store(CacheSlot(hc, best_move, depth, best_score, 0, cache.getGeneration()))
      else cache.store(CacheSlot(hc, best_move, depth, best_score, LOWERBOUND, cache.getGeneration()))
    } else {
      cache.store(CacheSlot(hc, null, depth, alpha, UPPERBOUND, cache.getGeneration()))
    }
    h.remove(hc)
    if (legal_moves == 0) return if (check) -MATE_SCORE + ply else 0
    return best_score
  }
  fun root_search(pos: Position, max_depth: Int, max_nodes: Int): Pair<Move, Int> {
    nodes = 0
    var ev = 0
    val h = pos.hash()
    cache.incGeneration()
    for (d in 1 .. max_depth) {
      require(pos.hash() == h)
      val alpha = ev - 50
      val beta = ev + 50
      val w = search(pos, alpha, beta, 0, d * PLY)
      if (w <= alpha) {
        ev = search(pos, -MATE_SCORE, beta, 0, d * PLY)
      } else if (w >= beta) {
        ev = search(pos, alpha, MATE_SCORE, 0, d * PLY)
      } else {
        ev = w
      }
      if (nodes >= max_nodes) break
    }
    val p = cache.probe(h)
    require(p != null)
    return p.move!! to ev
  }
}
