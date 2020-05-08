package csw.task

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

sealed class Result<out T> {
    companion object {
        fun <V> success(value: V): Done<V> = Done(Success(value))
        fun <V> fail(error: Throwable): Done<V> = Done(Fail(error))
        fun <V> async(): Later<V> = Later()
    }

    abstract fun isDone(): Boolean

    abstract fun blockGet(timeMilliseconds: Long = Long.MAX_VALUE): ResultValue<T>
}

data class Done<T>(val value: ResultValue<T>) : Result<T>() {
    override fun isDone(): Boolean = true
    override fun blockGet(timeMilliseconds: Long): ResultValue<T> = value
}


class Later<T> : Result<T>() {
    companion object {
        private data class AsyncStatus<T>(
            val value: ResultValue<T>? = null,
            val latch: CountDownLatch? = null
        )
    }

    private val status = AtomicReference<AsyncStatus<T>>(AsyncStatus())

    override tailrec fun blockGet(timeMilliseconds: Long): ResultValue<T> {
        val st = status.get()
        return when {
            st.value != null -> st.value
            else -> {
                val newStatus = st.copy(latch = CountDownLatch(1))
                if (status.compareAndSet(st, newStatus)) {
                    if (newStatus.latch!!.await(timeMilliseconds, TimeUnit.MILLISECONDS)) newStatus.value!!
                    else throw java.lang.Exception("Time out")
                } else blockGet(timeMilliseconds)
            }
        }
    }

    fun finish(resValue: T) = finish(csw.task.Success(resValue))

    fun finish(res: ResultValue<T>) {
        var status: AsyncStatus<T>
        do {
            status = this.status.get()
            if (status.value != null) {
                throw Exception("Value has done")
            }
        } while (!this.status.compareAndSet(status, AsyncStatus(value = res)))
        status.latch?.countDown()
    }

    override fun isDone(): Boolean = status.get().value != null
}