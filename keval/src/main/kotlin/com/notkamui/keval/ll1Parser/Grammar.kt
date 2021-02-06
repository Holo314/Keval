package com.notkamui.keval.ll1Parser

/**
 * A class that represent the set of all possible symbols, it is partitioned into 2 classes:
 *
 *  [Terminal] - the class that represent all of the terminal symbols
 *
 *  [NonTerminal] - the class that represent all non-terminal symbols
 */
sealed class Symbol {
    sealed class Terminal : Symbol() {
        companion object {
            fun of(symbol: Regex) = Custom(symbol)
            fun of(symbol: String) = Custom(symbol.toRegex())
        }
        data class Custom internal constructor(val symbol: Regex) : Terminal() {
            init {
                require(symbol.pattern.isNotBlank()) { "blank symbols are not allowed" }
            }
        }

        /**
         * A symbol that represent the end of sequence, it is used only internally and should not be added to a rule
         */
        internal object EOS : Terminal()

        /**
         * A symbol that represent the empty sequence, if it appears in a rule it must appear alone and rules that it appears on represent "remove symbol" rules.
         *
         * For example: `A -> Empty` is the rule "when the non-terminal A appear, one can remove it"
         */
        object Empty : Terminal()
    }

    sealed class NonTerminal : Symbol() {
        companion object {
            fun of(symbol: String) = Custom(symbol)
        }
        data class Custom internal constructor(val symbol: String) : NonTerminal() {
            init {
                require(symbol.isNotBlank()) { "value cannot be blank" }
            }
        }

        /**
         * A symbol that represent the starting point of the parsing, one should build his grammar starting from this symbol
         */
        object Start : NonTerminal()
    }
}

/**
 * A class that represent a production rule for the grammar
 */
data class Rule private constructor(val head: NonTerminal, val body: List<Symbol>) {
    init {
        require(body.isNotEmpty()) { "The rule must not be empty, use ${Symbol.Terminal.Empty} to represent empty sequence" }
        if (body[0] is Symbol.NonTerminal) {
            require(body[0] != head) { "Left recursion is not allowed" }
        }
        if (body.filterNot { it is Symbol.Terminal.EOS }.size > 1) {
            require(Symbol.Terminal.Empty !in body) { "The empty terminal cannot appear with other rules" }
        }
    }

    companion object {
        fun from(definition: Pair<NonTerminal, List<Symbol>>) = Rule(definition.first, definition.second)
        fun rulesSet(generator: RuleResource.() -> Unit) = RuleResource(mutableSetOf()).apply(generator).mutableSet.toSet()

        data class RuleResource(val mutableSet: MutableSet<Rule>) {
            operator fun Pair<NonTerminal, List<Symbol>>.unaryPlus() = mutableSet.add(Rule(first, second))
        }
    }
}

typealias Empty = Symbol.Terminal.Empty
typealias Terminal = Symbol.Terminal
typealias Start = Symbol.NonTerminal.Start
typealias NonTerminal = Symbol.NonTerminal
internal typealias EOS = Symbol.Terminal.EOS
