The purpose of this library is make writing async code easier.
For example, if we want to make sub tasks and join the result, the code would as easy as below:
```kotlin
fun main() {
    val context = ExecuteContext()

    val subTasks: List<Task<Int>> = (0..1000).map {
        Task.of {
            (0..100).sum()
        }
    }

    val task = Task.join(subTasks) { it.sum() }

    TaskExecutor.init(context, task).execute()
}
```
The 1000 sub tasks will run in parallel, and when all the sub tasks finished, then join the results by sum function.  

For the case that take the first finished task as result, this library provide a function `race`, like:
```kotlin
fun main(){
    val context = ExecuteContext()
    val task1 = Task.io {
        println("I am started")
        try {
            Thread.sleep(5000)
        }catch (e:InterruptedException){
            println("I am interrupted")
            e.printStackTrace()
            throw e
        }
       123
   }

    val task11 = Task.of{
        println("I am started")
        try {
            Thread.sleep(5000)
        }catch (e:InterruptedException){
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
``` 
The advantage of the `race` method is that it will cancel and interrupt all other unfinished tasks when the after the first finish task, this will save some resource of the server. 