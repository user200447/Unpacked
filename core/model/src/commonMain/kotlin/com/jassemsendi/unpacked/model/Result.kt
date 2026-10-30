package com.jassemsendi.unpacked.model

/**
 * A typed reason something could not be done. Every layer declares its own implementations (ADR 0009):
 * the library's [DataError], the ingest module's reasons an Export will not open, and so on. An
 * expected failure is returned as a [Result.Error] carrying one of these, never thrown.
 */
public interface Error

/** What a call that can fail for an expected reason returns: the [Success] value or the typed [Error]. */
public sealed interface Result<out D, out E : com.jassemsendi.unpacked.model.Error> {
    public data class Success<out D>(
        val data: D,
    ) : Result<D, Nothing>

    public data class Error<out E : com.jassemsendi.unpacked.model.Error>(
        val error: E,
    ) : Result<Nothing, E>
}

/** A call whose success carries nothing but the fact that it succeeded. */
public typealias EmptyResult<E> = Result<Unit, E>

public inline fun <T, E : Error, R> Result<T, E>.map(map: (T) -> R): Result<R, E> =
    when (this) {
        is Result.Error -> Result.Error(error)
        is Result.Success -> Result.Success(map(data))
    }

/** The next call that depends on this one's value; the first failure in a chain is the chain's failure. */
public inline fun <T, E : Error, R> Result<T, E>.andThen(next: (T) -> Result<R, E>): Result<R, E> =
    when (this) {
        is Result.Error -> Result.Error(error)
        is Result.Success -> next(data)
    }

public inline fun <T, E : Error> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> =
    when (this) {
        is Result.Error -> {
            this
        }

        is Result.Success -> {
            action(data)
            this
        }
    }

public inline fun <T, E : Error> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> =
    when (this) {
        is Result.Error -> {
            action(error)
            this
        }

        is Result.Success -> {
            this
        }
    }

public fun <T, E : Error> Result<T, E>.asEmptyResult(): EmptyResult<E> = map { }
