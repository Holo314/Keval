package com.notkamui.keval

import com.notkamui.keval.ll1Parser.*
import kotlin.test.Test
import kotlin.test.assertEquals

fun Symbol.asString(): String {
    return when (this) {
        is Empty -> "ε"
        is Symbol.Terminal.Custom -> this.symbol.pattern
        is EOS -> "$"
        is Symbol.NonTerminal.Custom -> this.symbol
        is Start -> "S"
    }
}

class LL1Test {
    @Test
    fun parseTableTest(): Unit {
        val A = NonTerminal.of("A")
        val B = NonTerminal.of("B")

        val a = Terminal.of("a")
        val b = Terminal.of("b")
        val c = Terminal.of("c")


        val rules = Rule.rulesSet {
            + (Start to listOf(a, A, B, b))
            + (A to listOf(a, A, c))
            + (A to listOf(Empty))
            + (B to listOf(b, B))
            + (B to listOf(c))
        }


        val ffTable = generateFirstFollowTable(rules)
        assertEquals(Pair(setOf(a), setOf(EOS)), ffTable[Start])
        assertEquals(Pair(setOf(a, Empty), setOf(b, c)), ffTable[A])
        assertEquals(Pair(setOf(b, c), setOf(b)), ffTable[B])

        val pTable = generateParseTable(rules, ffTable)
        assertEquals(setOf(Rule.from(Start to listOf(a, A, B, b))), pTable[Pair(Start, a)])
        assertEquals(setOf(), pTable[Pair(Start, b)])
        assertEquals(setOf(), pTable[Pair(Start, c)])
        assertEquals(setOf(), pTable[Pair(Start, EOS)])
        assertEquals(setOf(Rule.from(A to listOf(a, A, c))), pTable[Pair(A, a)])
        assertEquals(setOf(Rule.from(A to listOf(Empty))), pTable[Pair(A, b)])
        assertEquals(setOf(Rule.from(A to listOf(Empty))), pTable[Pair(A, c)])
        assertEquals(setOf(), pTable[Pair(A, EOS)])
        assertEquals(setOf(), pTable[Pair(B, a)])
        assertEquals(setOf(Rule.from(B to listOf(b, B))), pTable[Pair(B, b)])
        assertEquals(setOf(Rule.from(B to listOf(c))), pTable[Pair(B, c)])
        assertEquals(setOf(), pTable[Pair(B, EOS)])
    }

    @Test
    fun parseTableTest2(): Unit {
        val E_ = NonTerminal.of("E'")
        val T = NonTerminal.of("T")
        val T_ = NonTerminal.of("T'")
        val F = NonTerminal.of("F")

        val plus = Terminal.of("\\+")
        val mul = Terminal.of("\\*")
        val lb = Terminal.of("\\(")
        val rb = Terminal.of("\\)")
        val id = Terminal.of("id")

        val rules = Rule.rulesSet {
            + (Start to listOf(T, E_))
            + (E_ to listOf(plus, T, E_))
            + (E_ to listOf(Empty))
            + (T to listOf(F, T_))
            + (T_ to listOf(mul, F, T_))
            + (T_ to listOf(Empty))
            + (F to listOf(lb, Start, rb))
            + (F to listOf(id))
        }

        val ffTable = generateFirstFollowTable(rules)
        assertEquals(Pair(setOf(lb, id), setOf(EOS, rb)), ffTable[Start])
        assertEquals(Pair(setOf(plus, Empty), setOf(EOS, rb)), ffTable[E_])
        assertEquals(Pair(setOf(lb, id), setOf(EOS, rb, plus)), ffTable[T])
        assertEquals(Pair(setOf(mul, Empty), setOf(EOS, rb, plus)), ffTable[T_])
        assertEquals(Pair(setOf(lb, id), setOf(EOS, rb, plus, mul)), ffTable[F])

        val pTable = generateParseTable(rules, ffTable)
        assertEquals(setOf(Rule.from(Start to listOf(T, E_))), pTable[Pair(Start, lb)])
        assertEquals(setOf(Rule.from(E_ to listOf(Empty))), pTable[Pair(E_, EOS)])
        assertEquals(setOf(), pTable[Pair(T, mul)])
        assertEquals(setOf(Rule.from(T_ to listOf(mul, F, T_))), pTable[Pair(T_, mul)])
        assertEquals(setOf(Rule.from(F to listOf(id))), pTable[Pair(F, id)])
    }

}