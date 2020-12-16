package engine

class TestCoroutine {

    var thread: Thread? = null                 // The thread this coroutine is using

    val state = Object()            // Condition variable notified when the coroutine state changes
    // "             // Mutex to protect coroutine state

    var coroutineRunning = false       // Is the coroutine currently running? Lock StateMutex before access and notify StateChange on change
    var coroutineTerminated = false    // Has the coroutine terminated? Lock StateMutex before access and notify StateChange on change
    lateinit var name: String                   // The name of this coroutine
}