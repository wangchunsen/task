package csw.task


sealed abstract class Task<out T> {
    companion object {
        fun <R> async(func: () -> R): Task<R> = AsyncTask(ComputeTask(func))
        fun <R> of(computer: () -> R): Task<R> = ComputeTask(computer)
        fun <R> io(computer: () -> R): Task<R> = IOTask(ComputeTask(computer))
        fun <O, R> join(tasks: List<Task<O>>, joinFun: (List<O>) -> R): Task<R> {
            require(tasks.isNotEmpty()) { "Can not join empty tasks" }
            return SequenceTask(tasks = tasks.toList(), join = joinFun)
        }
    }
}

fun <T, V> Task<T>.map(func: (T) -> V): Task<V> = Map(this, func)

fun <T, V> Task<T>.flatMap(func: (T) -> Task<V>): Task<V> = Bind(this, func)

typealias AsyncCallback<T> = (T) -> Unit

class Pure<T>(val value: T) : Task<T>()
class Delay<T>(val func: () -> T) : Task<T>()
class Map<A, T>(val task: Task<A>, val func: (A) -> T) : Task<T>()
class Bind<A, T>(val task: Task<A>, val func: (A) -> Task<T>) : Task<T>()
class Async<T>(val callback: (AsyncCallback<T>) -> Unit) : Task<T>()

private inline fun <A, T> Map<A,T>.run(): T = this.func(this.task.run())
private inline fun <A, T> runBind(t: Bind<A, T>): T = t.func(t.task.run()).run()
fun <T> Task<T>.run(): T = when (this) {
    is Pure -> this.value
    is Delay -> this.func()
    is Map<*, T> -> this.run()
    is Bind<* , T> -> runBind(this)
    is Async -> {
        val async = Result.async<T>()
        this.callback { res ->
            async.finish(res)
        }
        async.blockGet().value()
    }
}


/**
 * Run tasks parallelly, and join the result by the join function
 */
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
        ComputeTask {
            func(t)
        }
    }
}

fun <T> Task<T>.sleep(time: Long): Task<T> = DelayTask(this, time)

fun <T> Task<T>.race(other: Task<T>): Task<T> = when (this) {
    is RaceTask -> RaceTask(tasks + other)
    else -> RaceTask(listOf(this, other))
}

fun <T> Task<T>.execute(context: ExecuteContext = ExecuteContext.defaultContext): Result<T> =
    TaskExecutor.init(context, this).execute()

fun <T1 : Any, T2 : Any, R> forkJoin(task1: Task<T1>, task2: Task<T2>, join: (T1, T2) -> R): Task<R> =
    SequenceTask(listOf(task1, task2)) { list -> join(list[0] as T1, list[2] as T2) }
