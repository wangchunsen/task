package csw.task.util

import java.lang.IllegalArgumentException

sealed class Either<out L, out R> {
    companion object {
        fun <L, R> left(lftValue: L): Either<L, R> = Left(lftValue)
        fun <L, R> right(rightValue: R): Either<L, R> = Right(rightValue)
    }

    abstract fun isLeft(): Boolean
    abstract fun isRight(): Boolean

    abstract fun asLeft(): L
    abstract fun asRight(): R
}

data class Left<L>(val value: L) : Either<L, Nothing>() {
    override fun isLeft(): Boolean = true
    override fun isRight(): Boolean = false

    override fun asLeft(): L = value
    override fun asRight(): Nothing {
        throw IllegalArgumentException("Left can not cast to right value")
    }
}

data class Right<R>(val value: R) : Either<Nothing, R>() {
    override fun isLeft(): Boolean = false
    override fun isRight(): Boolean = true

    override fun asRight(): R = value
    override fun asLeft(): Nothing {
        throw IllegalArgumentException("Left can not cast to right value")
    }

    fun <O> map(func: (R) -> O): Right<O> = Right(func(value))
}