package csw.task

import csw.task.util.Either
import csw.task.util.Left
import csw.task.util.Right
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

sealed class Result<out T> {
    companion object {
        fun <V> success(value: V): Done<V> = Done(Success(value))
        fun <V> fail(error: Throwable): Done<V> = Done(Fail(error))
        fun <V> async(): Async<V> = Async()
    }

    abstract fun isDone(): Boolean
    abstract fun onDone(callback: (ResultValue<T>) -> Unit): Unit

    abstract fun <R> map(func: (T) -> R): Result<R>
}

fun <T> Result<T>.blockGet(timeout: Long = 0): ResultValue<T> = when (this) {
    is Done -> value
    is Async -> waitAndGet(timeout)
}

data class Done<T>(val value: ResultValue<T>) : Result<T>() {
    override fun <R> map(func: (T) -> R): Result<R> = when (value) {
        is Success -> success(func(value.value))
        is Fail -> this as Done<R>
    }

    override fun onDone(callback: (ResultValue<T>) -> Unit) {
        callback(value)
    }

    override fun isDone(): Boolean = true
}



typealias Callback<T> = (ResultValue<T>) -> Any

class Async<T> : Result<T>() {

    private val reference = AtomicReference<Either<ResultValue<T>, List<Callback<T>>>>(Either.right(emptyList()))

    fun waitAndGet(timeMilliseconds: Long = 0): ResultValue<T> = when (val rs = reference.get()) {
        is Left -> rs.value
        else -> {
            thisWait(timeMilliseconds)
            when (val rs = reference.get()) {
                is Left -> rs.value
                else -> throw TimeoutException("Waiting timeout before this result is resolved")
            }
        }
    }

    fun doneBy(res: Result<T>) {
        when (res) {
            is Done -> done(res.value)
            is Async -> res.onDone(this::done)
        }
    }

    fun done(res: ResultValue<T>) {
        var refvalue: Either<ResultValue<T>, List<Callback<T>>>
        do {
            refvalue = reference.get()
            if (refvalue.isLeft()) {
                throw Exception("Value has done")
            }
        } while (!reference.compareAndSet(refvalue, Either.left(res)))
        val callbacks = refvalue.asRight()
        callbacks.forEach { func -> func(res) }

        this.thisNotifyAll()
    }

    override tailrec fun onDone(callback: (ResultValue<T>) -> Unit) {
        val res = reference.get()
        when (res) {
            is Left -> callback(res.value)
            is Right ->
                if (!reference.compareAndSet(res, Either.right(res.value + callback))) onDone(callback)
        }
    }

    override fun <R> map(func: (T) -> R): Result<R> = when {
        isDone() -> Done(this.waitAndGet()).map(func)
        else -> {
            val asyncR = Async<R>()
            this.onDone { res ->
                when (res) {
                    is Success -> asyncR.done(Success(func(res.value)))
                    is Fail -> asyncR.done(res)
                }
            }
            asyncR
        }
    }

    override fun isDone(): Boolean = when (reference.get()) {
        is Left -> true
        else -> false
    }

    @Synchronized
    private fun thisWait(timeMilliseconds: Long) {
        (this as java.lang.Object).wait(timeMilliseconds)
    }

    @Synchronized
    private fun thisNotifyAll() {
        (this as java.lang.Object).notifyAll()
    }
}