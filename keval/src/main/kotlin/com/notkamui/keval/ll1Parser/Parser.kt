package com.notkamui.keval.ll1Parser

/**
 * Type aliases for mapping from [NonTerminal] to set of [Terminal]
 *
 *  - [FirstSets] represent the FIRST set
 *  - [FollowSets] represent the FOLLOW set
 */
typealias FirstSets = Map<NonTerminal, Set<Terminal>>
typealias FollowSets = Map<NonTerminal, Set<Terminal>>

/**
 * Create a (lazy-infinite) sequence of the form: `V, F(V), F(F(V)),...`
 * Where V=[startValue] and F=[this]
 */
fun <T> ((T) -> T).recursiveSeqFrom(startValue: T): Sequence<T> = sequence {
    yield(startValue)
    yieldAll(this@recursiveSeqFrom.recursiveSeqFrom(this@recursiveSeqFrom(startValue)))
}

/**
 * Create a (lazy-infinite) sequence of the form: `V, F(V), F(F(V)),...`
 * Where V=[startValue] and F=[func]
 */
fun <T> recursiveSeqFrom(startValue: T, func: (T) -> T): Sequence<T> {
    return func.recursiveSeqFrom(startValue)
}

/**
 * Find the fixed point of the sequence `V, F(V), F(F(V)),...`
 * Where V=[startValue] and F=[this]
 */
fun <T> ((T) -> T).fixPointFrom(startValue: T): T {
    return this.recursiveSeqFrom(startValue)
            .zipWithNext()
            .takeWhile { it.first != it.second }
            .last().second
}

/**
 * Find the fixed point of the sequence `V, F(V), F(F(V)),...`
 * Where V=[startValue] and F=[func]
 */
fun <T> fixPointFrom(startValue: T, func: (T) -> T): T {
    return func.fixPointFrom(startValue)
}

/**
 * Get the FIRST set of all the [NonTerminal] from [rules]
 */
fun getFirstSets(rules: Collection<Rule>): FirstSets {
    val rulesMap = rules
            .groupBy { it.head }
            .toMap()
            .mapValues { it.value.map { rule -> rule.body } }

    val initialSets: FirstSets = rulesMap.mapValues { emptySet() }

    fun nextStage(currentState: FirstSets): FirstSets {
        fun nextInnerStage(body: List<Symbol>, state: FirstSets): Set<Terminal> {
            return when (val firstElement = body.plus(Empty)[0]) {
                is Terminal -> setOf(firstElement)
                is NonTerminal -> when (Empty) {
                    !in state[firstElement]!! -> state[firstElement]!!
                    else -> (state[firstElement]!! - Empty) + nextInnerStage(body.drop(1), state)
                }
            }
        }

        return currentState.mapValues { symbolState ->
            symbolState.value + rulesMap[symbolState.key]!!.fold(emptySet()) { acc, ruleBody ->
                acc + nextInnerStage(ruleBody, currentState)
            }
        }
    }

    return ::nextStage.fixPointFrom(initialSets)
}

/**
 * Get the FIRST set of a tail of a body of a [Rule]
 */
private fun getFirstSetOfBody(firstSetsOfNonTerminals: FirstSets, body: List<Symbol>): Set<Terminal> {
    return when (val first = body.plus(Empty)[0]) {
        is Terminal -> setOf(first)
        is NonTerminal -> when (Empty) {
            in firstSetsOfNonTerminals[first]!! -> getFirstSetOfBody(firstSetsOfNonTerminals, body.drop(1)) +
                    firstSetsOfNonTerminals[first]!!
            else -> firstSetsOfNonTerminals[first]!!
        }
    }
}

/**
 * Get the FIRST set of the tail that appear after each [NonTerminal], that is, given the rule `A -> aAbBCc`
 *
 * [getFirstSetAfterNonTerminal] of `aAbBc` will return a map of:
 *
 *  - `A` to [getFirstSetOfBody] of `bBc`
 *  - `B` to [getFirstSetOfBody] of `Cc`
 *  - `C` to [getFirstSetOfBody] of `c`
 */
private fun getFirstSetAfterNonTerminal(body: List<Symbol>, firstSetsOfNonTerminals: FirstSets): FollowSets {
    return body.asSequence()
            .filter { it is NonTerminal }
            .mapIndexed { i, symbol -> symbol to i + 1 }
            .map {
                it.first as NonTerminal to when {
                    it.second < body.size -> getFirstSetOfBody(firstSetsOfNonTerminals, body.drop(it.second + 1))
                    else -> getFirstSetOfBody(firstSetsOfNonTerminals, listOf(Empty))
                }
            }
            .toMap()
}

/**
 * Get the FIRST set of all the [NonTerminal] from [rules] where it assumes [firstSets] = [getFirstSets] of [rules]
 */
fun getFollowSets(rules: Collection<Rule>, firstSets: FirstSets): FollowSets {
    val symbols = rules.map { it.head }.toSet()
    val initialSets: FollowSets = symbols.map {
        it to when (it) {
            is Start -> setOf(Symbol.Terminal.EOS)
            else -> emptySet()
        }
    }.toMap()

    fun nextStageByRule(rule: Rule, state: FollowSets): FollowSets {
        val followingTerminals = getFirstSetAfterNonTerminal(rule.body, firstSets)
        val withEmptyFollow = followingTerminals
                .filter { Empty in it.value }
                .mapValues { state[rule.head]!! + it.value - Empty }

        return state.mapValues {
            when (val head = it.key) {
                in withEmptyFollow -> it.value + withEmptyFollow[head]!!
                in followingTerminals -> it.value + followingTerminals[head]!!
                else -> it.value
            }
        }
    }

    return fixPointFrom(initialSets) { value ->
        rules.fold(value) { acc, rule ->
            acc.mapValues { it.value + nextStageByRule(rule, value)[it.key]!! }
        }
    }
}

/**
 * Get the FIRST set of all the [NonTerminal] from [rules]
 *
 * This function computes the FIRST set from [rules], so if you already have that set, use the other [getFollowSets] function
 */
fun getFollowSets(rules: Collection<Rule>): FollowSets {
    return getFollowSets(rules, getFirstSets(rules))
}

/**
 * A typealias that represent a table who column are `[NonTerminal] symbols | FIRST | FOLLOW`
 */
typealias FirstFollowTable = Map<NonTerminal, Pair<Set<Terminal>, Set<Terminal>>>

/**
 * Generates a table `[NonTerminal] | FIRST | FOLLOW` using specific [rules]
 */
fun generateFirstFollowTable(rules: Collection<Rule>): FirstFollowTable {
    require(rules.any { it.head == Start }) { "The rules must contain at least one rule that start with the starter non-terminal" }
    val firstSets = getFirstSets(rules)
    val followSets = getFollowSets(rules, firstSets)
    return firstSets.mapValues { Pair(it.value, followSets[it.key]!!) }
}

/**
 * A typealias that represent a parse table [NonTerminal]×[Terminal] to set of [Rule]
 */
typealias ParseTable = Map<Pair<NonTerminal, Terminal>, Set<Rule>>

/**
 * Generates a parse table [NonTerminal]×[Terminal] to set of [Rule] using specific [rules] and assuming [firstFollowTable] = [generateFirstFollowTable] of [rules]
 */
fun generateParseTable(rules: Collection<Rule>, firstFollowTable: FirstFollowTable): ParseTable {
    val firstSets = firstFollowTable.mapValues { it.value.first }
    val followSets = firstFollowTable.mapValues { it.value.second }

    @Suppress("UNCHECKED_CAST")
    val symbols: Pair<List<Terminal>, List<NonTerminal>> = rules
            .flatMap { it.body + it.head }
            .partition { it is Terminal } as Pair<List<Terminal>, List<NonTerminal>>

    val terminals = symbols.first.toSet() + Symbol.Terminal.EOS - Empty
    val nonTerminal = symbols.second.toSet()
    val combinations: List<Pair<NonTerminal, Terminal>> =
            nonTerminal.flatMap { lhs -> terminals.map { rhs -> lhs to rhs } }

    return combinations.map {
        it to rules.filter { rule -> rule.head == it.first }
                .filter { rule ->
                    val firstOfBody = getFirstSetOfBody(firstSets, rule.body)

                    Empty in firstOfBody && it.second in followSets[it.first]!!
                            || it.second in firstOfBody
                }.toSet()
    }.toMap()
}

/**
 * Generates a parse table [NonTerminal]×[Terminal] to set of [Rule] using specific [rules]
 *
 * This function generates [FirstFollowTable], so if you already computed it use the other [generateParseTable]
 */
fun generateParseTable(rules: Collection<Rule>): ParseTable {
    return generateParseTable(rules, generateFirstFollowTable(rules))
}



fun main() {
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
        +(Start to listOf(T, E_))
        +(E_ to listOf(plus, T, E_))
        +(E_ to listOf(Empty))
        +(T to listOf(F, T_))
        +(T_ to listOf(mul, F, T_))
        +(T_ to listOf(Empty))
        +(F to listOf(lb, Start, rb))
        +(F to listOf(id))
    }

    val ffTable = generateFirstFollowTable(rules)
    val pTable = generateParseTable(rules, ffTable)

}