@file:Suppress("unused", "UNUSED_PARAMETER")

package kotlinx.coroutines

object Dispatchers {
    val IO: CoroutineDispatcher = CoroutineDispatcher()
    val Default: CoroutineDispatcher = CoroutineDispatcher()
    val Main: CoroutineDispatcher = CoroutineDispatcher()
}

class CoroutineDispatcher

class CoroutineScope(val context: Any = Unit) {
    fun launch(dispatcher: CoroutineDispatcher = Dispatchers.Default, block: suspend () -> Unit): Job = Job()
    fun launch(block: suspend () -> Unit): Job = Job()
}

suspend fun <T> withContext(dispatcher: CoroutineDispatcher, block: suspend () -> T): T = block()
suspend fun <T> coroutineScope(block: suspend CoroutineScope.() -> T): T = block(CoroutineScope())
fun <T> async(dispatcher: CoroutineDispatcher, block: suspend () -> T): Deferred<T> = Deferred()

class Job { suspend fun join() {} }
class Deferred<T> { suspend fun await(): T = throw NotImplementedError() }
