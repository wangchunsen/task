package csw.task

import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit
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

private data class AsyncStatus<T>(
    val value: ResultValue<T>? = null,
    val callbacks: List<Callback<T>> = emptyList(),
    val queue: SynchronousQueue<ResultValue<T>>? = null
) {
    fun addCallback(callback: Callback<T>): AsyncStatus<T> =
        this.copy(callbacks = callbacks + callback)
}

class Async<T> : Result<T>() {
    private val status = AtomicReference<AsyncStatus<T>>(AsyncStatus())

    tailrec fun waitAndGet(timeMilliseconds: Long = 0): ResultValue<T> {
        val st = status.get()
        return when {
            st.value != null -> st.value
            else -> {
                val newStatus = st.copy(queue = SynchronousQueue())
                if (status.compareAndSet(st, newStatus)) {
                    newStatus.queue!!.poll(timeMilliseconds, TimeUnit.MILLISECONDS)
                } else waitAndGet(timeMilliseconds)
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
        var status: AsyncStatus<T>
        do {
            status = this.status.get()
            if (status.value != null) {
                throw Exception("Value has done")
            }
        } while (!this.status.compareAndSet(status, AsyncStatus(value = res)))
        val callbacks = status.callbacks
        callbacks.forEach { func -> func(res) }
        status.queue?.offer(res)
    }

    override tailrec fun onDone(callback: (ResultValue<T>) -> Unit) {
        val res = status.get()
        when {
            res.value != null -> callback(res.value)
            !status.compareAndSet(res, res.addCallback(callback)) -> onDone(callback)
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

    override fun isDone(): Boolean = status.get().value != null
}