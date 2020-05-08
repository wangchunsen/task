package csw.task

import csw.task.exception.MultipleException
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference


enum class ExecutorType {
    ASYNC, IO, NORMAL
}

class TaskExecutor<T>(
    private val context: ExecuteContext,
    private val task: Task<T>,
    private val type: ExecutorType
) {
    companion object {
        fun <O> init(context: ExecuteContext, task: Task<O>): TaskExecutor<O> =
            TaskExecutor(context = context, task = task, type = ExecutorType.NORMAL)
    }

    private val cancelled = AtomicReference<Boolean>(false)
    private val runningThread = AtomicReference<Thread>()
    private val subExecutor: LinkedList<TaskExecutor<*>> = LinkedList()

    /**
     * Execute task
     */
    fun execute(): Result<T> = when (type) {
        ExecutorType.ASYNC, ExecutorType.IO -> {
            val finalResult = Async<T>()
            val job = Runnable {
                val taskResult = taskResult(task)
                finalResult.lift(taskResult)
            }
            when (type) {
                ExecutorType.IO -> context.submitIO(job)
                else -> context.submitTask(job)
            }
            finalResult
        }
        else -> taskResult(task)
    }

    //fork a task to a new executor, and execute async from the current thread
    private fun <O> fork(task: Task<O>): TaskExecutor<O> = when (task) {
        is IOTask -> TaskExecutor(context, task.task, type = ExecutorType.IO)
        is AsyncTask -> TaskExecutor(context, task.task, type = ExecutorType.ASYNC)
        else -> TaskExecutor(context, task, type = ExecutorType.ASYNC)
    }.apply {
        subExecutor.add(this)
    }

    fun cancel(): Boolean {
        if (cancelled.get()) {
            return false
        }
        if (!cancelled.compareAndSet(false, true)) {
            return false
        }
        runningThread.get()?.interrupt()
        return true
    }


    private fun <O, T> flatMapTaskResult(task: FlatMappedTask<O, T>): Result<T> {
        val firstTask = task.task
        val result = Result.async<T>()

        taskResult(firstTask).onDone { res ->
            when (res) {
                is Success -> {
                    try {
                        val nextTask = task.ftMap(res.value)
                        result.lift(taskResult(nextTask))
                    } catch (e: Throwable) {
                        result.finish(Fail(e))
                    }
                }
                is Fail -> result.finish(res)
            }
        }
        return result
    }

    private fun <O, T> sequenceTaskResult(taskRoot: SequenceTask<O, T>): Result<T> {
        val finalResult = Result.async<T>()
        val count = AtomicInteger(0)
        val results = taskRoot.tasks.map { fork(it).execute() }
        results.forEach { result ->
            result.onDone {
                if (count.incrementAndGet() == taskRoot.subTaskSize()) {
                    val values = results.map { it.blockGet() }
                    val fails = values.filter { !it.isSuccess() }.map { (it as Fail).exception }
                    if (fails.isNotEmpty()) {
                        finalResult.finish(Fail(MultipleException(fails)))
                    } else {
                        val finalResultValue = taskRoot.join(values.map { (it as Success).value })
                        finalResult.finish(Success(finalResultValue))
                    }
                }
            }
        }
        return finalResult
    }

    private fun <O> raceTaskResult(task: RaceTask<O>): Result<O> {
        val finalResult = Async<O>()

        val valueRef = AtomicReference<ResultValue<O>>(null)
        val executors: List<TaskExecutor<O>> = task.tasks.map { fork(it) }
        val faillings = ConcurrentLinkedDeque<Throwable>(emptyList())

        executors.forEach { executor ->
            executor.execute().onDone { resv ->
                if (resv.isSuccess() && valueRef.compareAndSet(null, resv)) {
                    finalResult.finish(resv)
                    //cancel all unfinished task
                    executors.forEach { it.cancel() }
                } else if (resv is Fail && !finalResult.isDone()) {
                    faillings.offer(resv.exception)
                    // all racing task failed
                    if (faillings.size == task.tasks.size) {
                        finalResult.finish(Fail(MultipleException(faillings.toList())))
                    }
                }
            }
        }
        return finalResult
    }

    private fun <O> compute(task: ComputeTask<O>): Result<O> {
        runningThread.set(Thread.currentThread())
        return try {
            Result.success((task.evaluator)())
        } catch (e: Throwable) {
            Result.fail<O>(e)
        } finally {
            runningThread.set(null)
            //clean interrupt state
            Thread.interrupted()
        }
    }

    private fun <O> delayTask(task: DelayTask<O>): Result<O> {
        val result = Result.async<O>()
        context.schedule({
            if (!cancelled.get()) {
                result.lift(fork(task.task).execute())
            } else {
                cancel(result)
            }
        }, task.delay)
        return result
    }

    private fun cancel(result: Async<*>) {
        result.finish(Fail(java.lang.Exception("Task cancelled")))
    }


    //this method call is guaranteed in the same thread
    private fun <O> taskResult(task: Task<O>): Result<O> {
        if (cancelled.get())
            return Result.fail(Exception("Task cancelled"))

        return when (task) {
            is ComputeTask ->
                compute(task)
            is InterruptAbleTask ->
                compute(task.task)
            is IOTask, is AsyncTask ->
                fork(task).execute()
            is DelayTask ->
                delayTask(task)
            is FlatMappedTask<*, O> ->
                flatMapTaskResult(task)
            is SequenceTask<*, O> ->
                sequenceTaskResult(task)
            is RaceTask -> raceTaskResult(task)
        }
    }

}