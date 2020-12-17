package engine


// The coroutine executing on the current thread (if it is a coroutine thread)
var gThreadCoroutine: TestCoroutine? = null

// A coroutine main function
typealias TestCoroutineMainFunc = (data: Any?) -> Unit

/** ~ImGuiTestCoroutineInterface */
class TestCoroutine
/** ~CreateFunc
 *  ~Coroutine_ImplStdThread_Create */
constructor(
        val func: TestCoroutineMainFunc,
        // The name of this coroutine
        val name: String,
        val engine: TestEngine) : Runnable {

    var thread: Thread? = Thread(this, name)                 // The thread this coroutine is using

    // Condition variable notified when the coroutine state changes
    val state = Object() // Mutex to protect coroutine state

    var coroutineRunning = false       // Is the coroutine currently running? Lock StateMutex before access and notify StateChange on change
    var coroutineTerminated = false    // Has the coroutine terminated? Lock StateMutex before access and notify StateChange on change

    init {
        thread!!.start()
    }

    // The main function for a coroutine thread
    // ~CoroutineThreadMain
    override fun run() {

        // Set the thread coroutine
        gThreadCoroutine = this

        // Wait for initial Run()
        synchronized(state) {
            while (true)
                if (coroutineRunning)
                    break
            state.wait()
        }
        // Run user code, which will then call Yield() when it wants to yield control
        func(engine)

        // Mark as terminated
        synchronized(state) {
            coroutineTerminated = true
            coroutineRunning = false
            state.notifyAll()
        }
    }

    /** ~Coroutine_ImplStdThread_Destroy */
    fun destroy() {
        assert(coroutineTerminated) { "The coroutine needs to run to termination otherwise it may leak all sorts of things and this will deadlock" }
        thread?.let {
            it.join()
            thread = null
        }
    }

    // Yield the current coroutine (can only be called from a coroutine)
    // ~Coroutine_ImplStdThread_Yield
    fun yield() {
        assert(gThreadCoroutine != null) { "This can only be called from a coroutine thread" }

        synchronized(state) {
            coroutineRunning = false
            state.notifyAll()
        }

        // At this point the thread that called RunCoroutine() will leave the "Wait for coroutine to stop" loop
        // Wait until we get started up again
        synchronized(state) {
            while (true) {
                if (coroutineRunning)
                    break // Breakpoint here if you want to catch the point where execution of this coroutine resumes
                state.wait()
            }
        }
    }

    // Run the coroutine until the next call to Yield(). Returns TRUE if the coroutine yielded, FALSE if it terminated (or had previously terminated)
    // ~Coroutine_ImplStdThread_Run
    fun runFunc(): Boolean {

        // Wake up coroutine thread
        synchronized(state) {

            if (coroutineTerminated)
                return false // Coroutine has already finished

            coroutineRunning = true
            state.notifyAll()
        }

        // Wait for coroutine to stop
        synchronized(state) {
            while (true)
                if (!coroutineRunning) {
                    // Breakpoint here to catch the point where we return from the coroutine
                    if (coroutineTerminated)
                        return false // Coroutine finished
                    break
                }
            state.wait()
        }

        return true
    }
}