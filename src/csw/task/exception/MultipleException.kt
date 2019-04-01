package csw.task.exception

import java.lang.Exception

class MultipleException (val exceptions:List<Throwable>): Exception(){
}