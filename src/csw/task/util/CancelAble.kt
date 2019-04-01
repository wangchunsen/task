package csw.task.util

@FunctionalInterface
interface CancelAble {
    fun cancel(): Boolean
}