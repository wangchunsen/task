package csw.task

fun main() {
    paralTasksTest()
}

fun paralTasksTest() {
    val tasks = (0..20).map { index ->
        Task.io {
            println("Task $index started")
            Thread.sleep(1000)
            println("Task $index finished")
            index

        }
    }

    val result = Task.join(tasks) {
        it.sum()
    }.execute()

    println(result.blockGet())
}

fun demo() {
    val context = ExecuteContext()
    val task1 = Task.io {
        println("I am started")
        try {
            Thread.sleep(5000)
        } catch (e: InterruptedException) {
            println("I am interrupted")
            e.printStackTrace()
            throw e
        }
        123
    }

    val task11 = Task.of {
        println("I am started")
        try {
            Thread.sleep(5000)
        } catch (e: InterruptedException) {
            println("I am interrupted")
            e.printStackTrace()
            throw e
        }
        12311
    }

    val race = task1.race(Task.io {
        Thread.sleep(1800)
        456
    }).race(task11)

    val execute = TaskExecutor.init(context, race).execute()

    println(execute.blockGet())
}