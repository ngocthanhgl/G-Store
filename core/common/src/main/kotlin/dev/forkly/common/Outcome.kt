package dev.forkly.common

/** Minimal result type — domain errors stay explicit, never exceptions-as-control-flow. */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val failure: String) : Outcome<Nothing>
}
