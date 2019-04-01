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
    private val computePool: ForkJoinPool = ForkJoinPool.commonPool(),
    private val ioPool: ThreadPoolExecutor = defaultIOPool()
) {
    private val timer = Executors.newScheduledThreadPool(0)

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
        ioPool.execute(job)
    }

    fun submitTask(job:Runnable):ForkJoinTask<*> = computePool.submit(job)

    fun publisError(error:Throwable): Unit {

    }
}