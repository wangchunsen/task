package csw.task

sealed class Task<T> {
    companion object {
        fun <R> async(func: () -> R): Task<R> = AsyncTask(ValueTask(func))
        fun <R> of(computer: () -> R): Task<R> = ValueTask(computer)
        fun <R> io(computer: () -> R): Task<R> = IOTask(ValueTask(computer))
        fun <O, R> join(tasks: List<Task<O>>, joinFun: (List<O>) -> R): Task<R> {
            require(tasks.isNotEmpty()) { "Can not join empty tasks" }
            return SequenceTask(tasks = tasks.toList(), join = joinFun)
        }
    }

    abstract fun <R> map(func: (T) -> R): Task<R>

    abstract fun <R> flatMap(func: (T) -> Task<R>): Task<R>
}

class ValueTask<T>(val evaluator: () -> T) : Task<T>() {
    override fun <R> map(func: (T) -> R): Task<R> = ValueTask(evaluator = { func(evaluator()) })

    override fun <R> flatMap(func: (T) -> Task<R>): Task<R> = FlatMappedTask(this, func)
}


class DelayTask<T>(val task: Task<T>, val delay: Long) : Task<T>() {
    override fun <R> map(func: (T) -> R): Task<R> =
        DelayTask(task = task.map(func), delay = delay)

    override fun <R> flatMap(func: (T) -> Task<R>): Task<R> =
        DelayTask(task = task.flatMap(func), delay = delay)
}

class FlatMappedTask<O, T>(val task: Task<O>, val ftMap: (O) -> Task<T>) : Task<T>() {
    override fun <V> map(func: (T) -> V): Task<V> {
        val fmap: (O) -> Task<V> = { t -> ftMap(t).map(func) }
        return FlatMappedTask(task = task, ftMap = fmap)
    }

    override fun <R> flatMap(func: (T) -> Task<R>): Task<R> = FlatMappedTask(this, func)
}

class AsyncTask<T>(val task: Task<T>) : Task<T>() {
    override fun <R> map(func: (T) -> R): Task<R> = AsyncTask(task = task.map(func))

    override fun <R> flatMap(func: (T) -> Task<R>): Task<R> = AsyncTask(task = task.flatMap(func))
}

class IOTask<T>(val task: Task<T>) : Task<T>() {
    override fun <R> map(func: (T) -> R): Task<R> = IOTask(task.map(func))

    override fun <R> flatMap(func: (T) -> Task<R>): Task<R> = FlatMappedTask(task = this.task, ftMap = func)
}


class SequenceTask<O, T>(
    val tasks: List<Task<O>>,
    val join: (List<O>) -> T
) : Task<T>() {
    override fun <R> map(func: (T) -> R): Task<R> {
        return SequenceTask(tasks) { values ->
            val join1: T = join(values)
            func(join1)
        }
    }

    override fun <R> flatMap(func: (T) -> Task<R>): Task<R> = FlatMappedTask(this, func)

    fun subTaskSize(): Int = tasks.size
}

/**
 * Race the sub tasks, take the first finished as result
 * All the other not finish yet task will be cancelled and interrupted if possible
 */
class RaceTask<T>(val tasks: List<Task<T>>) : Task<T>() {
    override fun <R> flatMap(func: (T) -> Task<R>): Task<R> = FlatMappedTask(task = this, ftMap = func)

    override fun <R> map(func: (T) -> R): Task<R> = flatMap { t ->
        ValueTask {
            func(t)
        }
    }
}

fun <T> Task<T>.sleep(time: Long): Task<T> = DelayTask(this, time)

fun <T> Task<T>.race(other: Task<T>): Task<T> = when (this) {
    is RaceTask -> RaceTask(tasks + other)
    else -> RaceTask(listOf(this, other))
}


//fun <T> race(vararg tasks: Task<T>): Task<T> {
//    require(tasks.size > 1) { "Can not race less that 2 tasks" }
//    val tasksCount = tasks.size
//    val failCount = AtomicInteger(tasksCount)
//    return SequenceTask(tasks.toList()) { resultValue, _, executor ->
//        when {
//            resultValue.isSuccess() -> {
//                executor.cancel()
//                resultValue
//            }
//            failCount.incrementAndGet() == tasksCount -> Fail(Exception("All tasks fail"))
//            else -> null
//        }
//    }
//}
