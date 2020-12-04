package engine.core

//------------------------------------------------------------------------
// Coroutine implementation using std::thread
// This implements a coroutine using std::thread, with a helper thread for each coroutine (with serialised execution, so threads never actually run concurrently)
//------------------------------------------------------------------------

// The coroutine executing on the current thread (if it is a coroutine thread)
var gThreadCoroutine: TestCoroutine? = null

//-------------------------------------------------------------------------
// ImGuiTestCoroutine
//-------------------------------------------------------------------------

// Coroutine abstraction (see shared/imgui_coroutine_impl_stdthread.h for a suggested implementation of this)

// An arbitrary handle used internally to represent coroutines (NULL indicates no handle)
typealias TestCoroutineMainFunc = (ctx: Any?) -> Unit

// This implements a coroutine (based on a thread, but never executing concurrently) that allows yielding and then resuming from the yield point
// Code using a coroutine should basically do "while (coroutine.Run()) { <do other work> }", whilst coroutine code should do "while (<something>) { <do work>; ImGuiTestCoroutine::Yield(); }"
class TestCoroutine(
        /** The name of this coroutine */
        val name: String,
        val ctx: Any?,
        val func: TestCoroutineMainFunc) : Runnable {

    //    std::condition_variable         StateChange;        // Condition variable notified when the coroutine state changes
//    std::mutex                      StateMutex;         // Mutex to protect coroutine state
    val lock = Object()

    /** Is the coroutine currently running? Lock StateMutex before access and notify StateChange on change */
    var coroutineRunning = false

    /** Has the coroutine terminated? Lock StateMutex before access and notify StateChange on change */
    var coroutineTerminated = false

    init {
        // Set the thread coroutine
        gThreadCoroutine = this
    }

    /** Run the coroutine until the next call to Yield(). Returns TRUE if the coroutine yielded, FALSE if it terminated (or had previously terminated) */
    override fun run() {
        synchronized(lock) {

            coroutineRunning = true
            lock.notifyAll()

            // Run user code, which will then call Yield() when it wants to yield control
            func(ctx)

            // Mark as terminated
            run {
                coroutineTerminated = true
                coroutineRunning = false
                lock.notifyAll()
            }
        }
    }
}