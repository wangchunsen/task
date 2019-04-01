package csw.task

import java.lang.Exception

sealed class ResultValue<out T> {
    abstract fun isSuccess(): Boolean
    abstract fun value(): T
    abstract fun <R> map(func: (T) -> R): ResultValue<R>
}


data class Success<T>(val value: T) : ResultValue<T>() {
    override fun <R> map(func: (T) -> R): ResultValue<R> = Success(func(value))

    override fun value(): T = this.value

    override fun isSuccess(): Boolean = true
}


data class Fail(val exception: Throwable) : ResultValue<Nothing>() {
    override fun <R> map(func: (Nothing) -> R): ResultValue<R> = this

    override fun value(): Nothing = throw Exception("Can not get value for fail")

    override fun isSuccess(): Boolean = false
}