package com.github.antma.flickering_chess
import kotlin.text.split
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.min
import kotlin.math.max
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

fun pieceToPromotionCharacter(p: Int): Char = when(p) {
  KNIGHT -> 'n'
  BISHOP -> 'b'
  ROOK -> 'r'
  QUEEN -> 'q'
  else -> '?'
}

data class Move(val from: Int, val to: Int, val flags: Int) {
  fun san(): String =
    StringBuilder(5).apply {
      append((((from and 15) + 97)).toChar())
      append((((from shr 4) + 49)).toChar())
      append((((to and 15) + 97)).toChar())
      append((((to shr 4) + 49)).toChar())
      if ((flags and PROMOTION) != 0) {
        append(pieceToPromotionCharacter(flags and 7))
      }
    }.toString()
}
class UndoMove(val move: Move, val piece_from: Int, val piece_to: Int, val castle: Int, val jump: Int, val material_score: Int, val hc: Long, val fifty_move_rule: Int)

private fun manhattanDistance(x: Int, y: Int): Int =
  max(abs( (x and 15) - (y and 15)), abs((x shr 4) - (y shr 4)))

class Position(position_fen: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0") {
  val board = IntArray(128)
  var side = 1
  private var castle = 0
  private var jump = -1
  private var wk = -1
  private var bk = -1
  private var materialScore = 0
  private var hc = 0L
  private var fiftyMoveRule = 100
  private fun hcUpdate(k: Int) {
    val p = board[k]
    if (p != 0) {
      hc = hc xor Zobrist.a[(KING + p) * 128 + k]
    }
  }
  init {
    val l = position_fen.split(' ')
    if (l.size < 5) throw IllegalArgumentException("not enough fen tokens")
    val t = l[0].split('/')
    if (t.size != 8) throw IllegalArgumentException("illegal number of ranks")
    for ( (i, v) in t.withIndex()) {
      val rank = 7 - i
      val off = rank * 16
      var file = 0
      fun put(p: Int) {
        if (file > 7) throw IllegalArgumentException("too many files in rank $rank")
        board[off+file] = p
        file++
      }
      for (c in v) {
        when(c) {
          'k' -> {
            if (bk >= 0) throw IllegalArgumentException("too many black kings")
            bk = off + file
            put(-KING)
          }
          'q' -> put(-QUEEN)
          'r' -> put(-ROOK)
          'b' -> put(-BISHOP)
          'n' -> put(-KNIGHT)
          'p' -> put(-PAWN)
          'K' -> {
            if (wk >= 0) throw IllegalArgumentException("too many white kings")
            wk = off + file
            put(KING)
          }
          'Q' -> put(QUEEN)
          'R' -> put(ROOK)
          'B' -> put(BISHOP)
          'N' -> put(KNIGHT)
          'P' -> put(PAWN)
          in '1' .. '8' -> file += c.code - 48
          else -> throw IllegalArgumentException("illegal piece characted")
        }
      }
      if (file != 8) throw IllegalArgumentException("illegal file $file in rank $rank")
    }
    if (wk < 0) throw IllegalArgumentException("white king is absent")
    if (bk < 0) throw IllegalArgumentException("black king is absent")
    val color = l[1]
    if (color == "w") side = 1
    else if (color == "b") side = -1
    else throw IllegalArgumentException("illegal color")
    if (l[2] != "-") {
      for (c in l[2]) {
        castle = castle or when(c) {
          'Q' -> 1
          'K' -> 2
          'q' -> 4
          'k' -> 8
          else -> throw IllegalArgumentException("illegal castling flag")
        }
      }
    }
    val enPassant = l[3]
    if (enPassant == "-") jump = -1
    else if (enPassant.length != 2) throw IllegalArgumentException("illegal en passant token length")
    else {
      jump = enPassant[0].code - 97
      if (jump !in 0 until 8) throw IllegalArgumentException("illegal en passant rank")
      val enPassantFile = enPassant[1]
      if ((side > 0 && enPassantFile != '6') || (side < 0 && enPassantFile != '3')) {
        throw IllegalArgumentException("illegal en passant file")
      }
    }
    val x = l[4].toIntOrNull(10)
    if (x == null) throw IllegalArgumentException("illegal fifty moves token")
    fiftyMoveRule = 100 - x
    materialScore = computeMaterialScore()
    hc = computeHashCode()
  }
  fun fen(): String = StringBuilder().run {
    for (i in (0 until 8).reversed()) {
      var c = 0
      fun put(ch: Char) {
        if (c != 0) {
          append(c)
        }
        append(ch)
        c = 0
      }
      for (j in 0 until 8) {
        when (board[i*16+j]) {
          -KING -> put('k')
          -QUEEN -> put('q')
          -ROOK -> put('r')
          -BISHOP -> put('b')
          -KNIGHT -> put('n')
          -PAWN -> put('p')
          0 -> c++
          PAWN -> put('P')
          KNIGHT -> put('N')
          BISHOP -> put('B')
          ROOK -> put('R')
          QUEEN -> put('Q')
          KING -> put('K')
          else -> put('?')
        }
      }
      put(if (i == 0) ' ' else '/')
    }
    append(if (side > 0) 'w' else 'b')
    append(' ')
    if (castle == 0) append('-')
    else {
      if ((castle and 2) != 0) append('K')
      if ((castle and 1) != 0) append('Q')
      if ((castle and 8) != 0) append('k')
      if ((castle and 4) != 0) append('q')
    }
    append(' ')
    if (jump < 0) append('-')
    else {
      append(((jump + 97)).toChar())
      append(if (side > 0) '6' else '3')
    }
    append(' ')
    append(100 - fiftyMoveRule)
    toString()
  }
  fun hash(): Long {
    var x = hc xor Zobrist.a[1672 + castle]
    if (jump >= 0) x = x xor Zobrist.a[1664 + jump]
    if (side < 0) x = x xor Zobrist.a[1688]
    return x
  }
  private fun role(x: Int): Int {
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
  private fun pieceMobilityScore(x: Int, dir: IntArray, sliding: Boolean, king: Int, center_score: Int, near_king_score: Int, regular_score: Int): Int {
    var s = 0
    for (delta in dir) {
      var y = x + delta
      while(inside(y)) {
        val p = board[y]
        if (p == 0) {
          s += if (manhattanDistance(king, y) <= 1) near_king_score
          else if (y == 0x33 || y == 0x34 || y == 0x43 || y == 0x44) center_score
          else regular_score
          if (!sliding) break
          y += delta
        } else if (p * side < 0) {
          s += if (manhattanDistance(king, y) <= 1) near_king_score
          else if (y == 0x33 || y == 0x34 || y == 0x43 || y == 0x44) center_score
          else regular_score
          break
        } else {
          break
        }
      }
    }
    return s
  }
  private fun mobilityScore(): Int {
    var s = 0
    for (i in (0 until 128).step(8)) for (k in i until (i + 8)) {
      when(board[k]) {
        KNIGHT -> s += pieceMobilityScore(k, knight_moves, false, bk, center_score = 5, near_king_score = 5, regular_score = 2)
        -KNIGHT -> s -= pieceMobilityScore(k, knight_moves, false, wk, center_score = 5, near_king_score = 5, regular_score = 2)
        BISHOP -> s += pieceMobilityScore(k, bishop_moves, true, bk, center_score = 5, near_king_score = 5, regular_score = 2)
        -BISHOP -> s -= pieceMobilityScore(k, bishop_moves, true, wk, center_score = 5, near_king_score = 5, regular_score = 2)
        ROOK -> s += pieceMobilityScore(k, rook_moves, true, bk, center_score = 5, near_king_score = 5, regular_score = 2)
        -ROOK -> s -= pieceMobilityScore(k, rook_moves, true, wk, center_score = 5, near_king_score = 5, regular_score = 2)
        QUEEN -> s += pieceMobilityScore(k, queen_moves, true, bk, center_score = 5, near_king_score = 5, regular_score = 2)
        -QUEEN -> s -= pieceMobilityScore(k, queen_moves, true, wk, center_score = 5, near_king_score = 5, regular_score = 2)
      }
    }
    return s
  }
  private fun pawnScore(): Int {
    var s = 0
    for (file in 0 until 8) {
      //pass pawns
      if (board[0x60+file] == PAWN) s += 50
      if (board[0x10+file] == -PAWN) s -= 50
      if (board[0x50+file] == PAWN && board[0x60+file] != -PAWN) s += 25
      if (board[0x20+file] == -PAWN && board[0x10+file] != PAWN) s -= 25
    }
    return s
  }
  fun eval(): Int = (materialScore + mobilityScore() + pawnScore()) * side
  private fun enumeratePawnMoves(x: Int, op: (Move) -> Boolean): Move? {
    val i = x shr 4
    val j = x and 15
    val rankBeforePromotion = if (side > 0) 6 else 1
    val forthRank = if (side > 0) 4 else 3
    val firstRank = rankBeforePromotion xor 7
    val captures = if (side > 0) white_pawn_captures else black_pawn_captures
    val y = x + 16 * side
    if (board[y] == 0) {
      if (i == rankBeforePromotion) {
        for (k in KNIGHT .. QUEEN) {
          val m = Move(x, y, k + PROMOTION)
          if (op(m)) return m
        }
      } else {
        val m = Move(x, y, 0)
        if (op(m)) return m
      }
    }
    if (i == firstRank && board[x + 16 * side] == 0 && board[x + 32 * side] == 0) {
      val m = Move(x, x + 32 * side, PAWN_JUMP)
      if (op(m)) return m
    }
    if (jump >= 0 && i == forthRank && abs(j - jump) == 1) {
      val m = Move(x, i * 16 + 16 * side + jump, CAPTURE + EN_PASSANT)
      if (op(m)) return m
    }
    for (delta in captures) {
      val z = x + delta
      if (!inside(z)) continue
      if (board[z] * side < 0) {
        if (i == rankBeforePromotion) {
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
      if (abs(q) == KNIGHT) return true
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
  fun enumerateLegalMoves(op: (Move) -> Boolean): Move? {
    enumerateMoves { m ->
      val u = doMove(m)
      val r = isLegal()
      undoMove(u)
      r && op(m)
    }
    return null
  }
  fun isLegal(): Boolean = if (side < 0) !isAttacked(wk, -1) else !isAttacked(bk, 1)
  fun isCheck(): Boolean = if (side > 0) isAttacked(wk, -1) else isAttacked(bk, 1)
  fun materialScoreDelta(m: Move): Int {
    return if ((m.flags and EN_PASSANT) != 0) {
      tbl_material_score_delta[PAWN]
    } else {
      var ms = 0
      if ((m.flags and PROMOTION) != 0) {
        ms += tbl_material_score_delta[m.flags and 7] - tbl_material_score_delta[PAWN]
      }
      val p = board[m.to]
      if (p != 0) {
        ms += tbl_material_score_delta[abs(p)]
      }
      ms
    }
  }
  fun doMove(m: Move): UndoMove {
    //System.err.println("doMove: " + m.san())
    val p = board[m.from]
    if (p == KING) wk = m.to
    else if (p == -KING) bk = m.to
    val u = UndoMove(m, p, board[m.to], castle, jump, materialScore, hc, fiftyMoveRule)
    materialScore += side * materialScoreDelta(m)
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
          board[0x70] = 0
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
    fiftyMoveRule--
    if ((m.flags and CAPTURE) != 0 || abs(u.piece_from) == PAWN) fiftyMoveRule = 100
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
    materialScore = u.material_score
    hc = u.hc
    fiftyMoveRule = u.fifty_move_rule
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
          board[0x73] = 0
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
    //System.err.println("doSANMove: $san")
    val m = enumerateMoves {
      it.san() == san
    } ?: return null
    val u = doMove(m)
    if (!isLegal()) {
      undoMove(u)
      return null
    }
    return u
  }
  private fun computeMaterialScore(): Int {
    var ms = 0
    for (i in 0 until 8) for (j in 0 until 8) {
      val k = 16 * i + j
      val p = board[k]
      if (p != 0) {
        val q = abs(p)
        if (q != KING) ms += p.sign * tbl_material_score_delta[q]
      }
    }
    return ms
  }
  private fun computeHashCode(): Long {
    var h = 0L
    for (i in 0 until 8) for (j in 0 until 8) {
      val k = 16 * i + j
      val p = board[k]
      if (p != 0) h = h xor Zobrist.a[(KING + p) * 128 + k]
    }
    return h
  }
  fun validate(): String? {
    val ms = computeMaterialScore()
    if (materialScore != ms) return "Material Score expected - $ms, field value - $materialScore"
    val h = computeHashCode()
    if (hc != h) return "hc expected - $h, field value - $hc"
    return null
  }
  fun isLegalPromotion(san: String): Boolean {
    val m = enumerateMoves {
      (it.flags and PROMOTION) != 0 && it.san().startsWith(san)
    } ?: return false
    val u = doMove(m)
    val r = isLegal()
    undoMove(u)
    return r
  }
  fun hasAtLeastOneLegalMove(): Boolean {
    return enumerateMoves {
      val u = doMove(it)
      val res = isLegal()
      undoMove(u)
      res
    } != null
  }
  fun isCheckMate(): Boolean = isCheck() && !hasAtLeastOneLegalMove()
  fun fiftyMoveDraw() = fiftyMoveRule <= 0
  fun insufficientMaterial(): Boolean =
    materialScore == 0 && board.all { it == 0 || abs(it) == KING}
}

enum class GameResult {
  WhiteWins,
  BlackWins,
  Stalemate,
  FiftyMoveRule,
  ThreeFold,
  InsufficientMaterial
}

class Game {
  private val halfMoves = ArrayList<Move>()
  private val h = ArrayList<Long>()
  val pos = Position()
  init {
    h.add(pos.hash())
  }
  fun getResult(): GameResult? {
    if (!pos.hasAtLeastOneLegalMove()) {
      if (pos.isCheck()) return if (pos.side < 0) GameResult.WhiteWins else GameResult.BlackWins
      return GameResult.Stalemate
    }
    if (pos.fiftyMoveDraw()) return GameResult.FiftyMoveRule
    val x = h.lastOrNull()!!
    if (h.count { it == x } >= 3) return GameResult.ThreeFold
    if (pos.insufficientMaterial()) return GameResult.InsufficientMaterial
    return null
  }
  fun doSANMove(san: String): Boolean {
    val p = pos.doSANMove(san) ?: return false
    halfMoves.add(p.move)
    h.add(pos.hash())
    return true
  }
  fun countMoves(): Int = (halfMoves.size + 1) / 2
}

const val MATE_SCORE: Int = 30_000
const val PLY: Int = 10
const val CHECK_EXTENTION = PLY
const val PAWN_PUSH_TO_SEVEN_RANK_EXTENTION = PLY

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

private fun pack(x: Int) = ((x shr 4) shl 3) + (x and 7)
private fun moveIdx(m: Move): Int = (pack(m.from) shl 6) + pack(m.to)

class History {
  private val tbl = IntArray(4096)
  private var maxv = 0
  fun get(m: Move): Int = ((tbl[moveIdx(m)] * 65536.0) / (maxv + 1.0)).toInt()
  fun relax() {
    for (i in tbl.indices) {
      tbl[i] = tbl[i] shr 1
    }
    maxv = maxv shr 1
  }
  fun moveIncr(m: Move) {
    val k = moveIdx(m)
    val v = tbl[k] + 1
    tbl[k] = v
    if (maxv < v) maxv = v
  }
}

class Engine(bits: Int) {
  private val cache = Cache(bits)
  private val history = History()
  private var nodes = 0
  private val h = mutableSetOf<Long>()
  private var maxDepth = 1
  private var maxNodes = 0
  private var useQSearch = true
  private var rootBestMove: Move? = null
  fun setParams(max_depth: Int, max_nodes: Int, use_qsearch: Boolean) {
    this.maxDepth = max_depth
    this.maxNodes = max_nodes
    this.useQSearch = use_qsearch
  }
  private fun qsearch(pos: Position, alpha: Int, beta: Int, ply: Int): Int {
    if (alpha >= MATE_SCORE - (ply + 1)) return alpha
    var legalMoves = 0
    val l = mutableListOf<Pair<Int, Move>>()
    pos.enumerateMoves {
      if ((it.flags and (CAPTURE + PROMOTION)) != 0) {
        l.add(pos.materialScoreDelta(it) to it)
      } else if (legalMoves == 0) {
        val u = pos.doMove(it)
        if (pos.isLegal()) ++legalMoves
        pos.undoMove(u)
      }
      false
    }
    l.sortByDescending { it.first }
    val ev = pos.eval()
    if (legalMoves > 0 && ev >= beta) return ev
    var bestScore = max(alpha, ev)
    for (m in l) {
      val u = pos.doMove(m.second)
      if (pos.isLegal()) {
        ++legalMoves
        var w = -qsearch(pos, -(bestScore + 1), -bestScore, ply + 1)
        if (bestScore < w && w < beta) {
          w = -qsearch(pos, -beta, -bestScore, ply + 1)
        }
        if (bestScore < w) {
          bestScore = w
          if (bestScore >= beta) {
            pos.undoMove(u)
            break
          }
        }
      }
      pos.undoMove(u)
    }
    if (legalMoves == 0) {
      return if (pos.isCheck()) -MATE_SCORE + ply else 0
    }
    return bestScore
  }
  private fun search(pos: Position, alpha: Int, beta: Int, ply: Int, depth: Int): Int {
    if (alpha >= MATE_SCORE - (ply + 1)) return alpha
    nodes++
    val hc = pos.hash()
    //draw
    if ((hc in h) || pos.fiftyMoveDraw()) return 0
    if (depth <= 0) return if (useQSearch) qsearch(pos, alpha, beta, ply) else pos.eval()
    val p = cache.probe(hc)
    if (p != null && p.depth >= depth) {
      when(p.flags) {
        LOWERBOUND -> if (p.score >= beta) return p.score
        UPPERBOUND -> if (p.score <= alpha) return p.score
        else -> return p.score
      }
    }
    val check = pos.isCheck()
    val posExt = if (check) CHECK_EXTENTION else 0
    val l = mutableListOf<Pair<Int, Move>>()
    pos.enumerateMoves {
      val h = if (p != null && p.move == it) {
        MATE_SCORE
      } else {
        pos.materialScoreDelta(it) * 65536 + history.get(it)
      }
      l.add(h to it)
      false
    }
    l.sortByDescending { it.first }
    h.add(hc)
    var bestScore = alpha
    var legalMoves = 0
    var bestMove: Move? = null
    for (m in l) {
      val move = m.second
      val u = pos.doMove(move)
      if (pos.isLegal()) {
        legalMoves++
        var ext = posExt
        val toFile = move.to shr 4
        val pawnPush = (pos.side < 0 && toFile == 6 && pos.board[move.to] == PAWN) ||
                       (pos.side > 0 && toFile == 1 && pos.board[move.to] == -PAWN)
        if (pawnPush && ext < PAWN_PUSH_TO_SEVEN_RANK_EXTENTION) ext = PAWN_PUSH_TO_SEVEN_RANK_EXTENTION
        val d = depth + ext - PLY
        var w = -search(pos, -(bestScore + 1), -bestScore, ply + 1, d)
        if (bestScore < w && w < beta) {
          w = -search(pos, -beta, -bestScore, ply + 1, d)
        }
        if (bestScore < w) {
          bestScore = w
          bestMove = m.second
          if (bestScore >= beta) {
            pos.undoMove(u)
            break
          }
        }
      }
      pos.undoMove(u)
    }
    if (ply == 0) rootBestMove = bestMove
    h.remove(hc)
    if (legalMoves == 0) return if (check) -MATE_SCORE + ply else 0
    if (bestMove != null) {
      history.moveIncr(bestMove)
      if (bestScore < beta) cache.store(CacheSlot(hc, bestMove, depth, bestScore, 0, cache.getGeneration()))
      else cache.store(CacheSlot(hc, bestMove, depth, bestScore, LOWERBOUND, cache.getGeneration()))
    } else {
      cache.store(CacheSlot(hc, p?.move, depth, alpha, UPPERBOUND, cache.getGeneration()))
    }
    return bestScore
  }
  fun rootSearch(pos: Position): Pair<Move?, String> {
    rootBestMove = null
    nodes = 0
    var ev = 0
    val h = pos.hash()
    cache.incGeneration()
    history.relax()
    val sb = StringBuilder()
    for (d in 1 .. maxDepth) {
      require(pos.hash() == h)
      val alpha = max(ev - 50, -MATE_SCORE)
      val beta  = min(ev + 50, MATE_SCORE)
      val w = search(pos, alpha, beta, 0, d * PLY)
      ev =
        if (w <= alpha) search(pos, -MATE_SCORE, beta, 0, d * PLY)
        else if (w >= beta) search(pos, alpha, MATE_SCORE, 0, d * PLY)
        else w
      sb.append("depth: $d, ev: $ev, nodes: $nodes\n")
      if (nodes >= maxNodes) break
    }
    return rootBestMove to sb.toString()
  }
}
