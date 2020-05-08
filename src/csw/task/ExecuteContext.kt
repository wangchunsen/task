package csw.task

import csw.task.util.CancelAble
import java.util.concurrent.*

private fun defaultIOPool(): ThreadPoolExecutor {
    val pool = ThreadPoolExecutor(
        40,
        40,
        60,
        TimeUnit.SECONDS,
        LinkedBlockingDeque()
    )

    pool.allowCoreThreadTimeOut(true)
    return pool
}

class ExecuteContext(
    private val computePool: Lazy<ForkJoinPool> = lazy { ForkJoinPool.commonPool() },
    private val ioPool: Lazy<ThreadPoolExecutor> = lazy { defaultIOPool() }
) {
    companion object {
        val defaultContext: ExecuteContext by lazy { ExecuteContext() }
    }

    private val timer by lazy { Executors.newScheduledThreadPool(0) }

    fun schedule(func: () -> Any, delay: Long): CancelAble {
        val task = Runnable {
            func()
        }
        val future = timer.schedule(task, delay, TimeUnit.MILLISECONDS)

        return object : CancelAble {
            override fun cancel(): Boolean = future.cancel(true)
        }
    }

    fun submitIO(job: Runnable) {
        ioPool.value.execute(job)
    }

    fun submitTask(job: Runnable): ForkJoinTask<*> = computePool.value.submit(job)

    fun publishError(error: Throwable): Unit {
        "".toInt()
    }
}