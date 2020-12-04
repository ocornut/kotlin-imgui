package engine

import engine.context.TestContext
import engine.core.*
import glm_.vec2.Vec2
import imgui.ID
import imgui.KeyMod
import imgui.cStr
import imgui.classes.Context
import imgui.classes.IO
import imgui.classes.TextFilter
import unsigned.toULong
import java.nio.ByteBuffer


//-------------------------------------------------------------------------
// DATA STRUCTURES
//-------------------------------------------------------------------------

// Gather items in given parent scope.
class TestGatherTask {
    var parentID: ID = 0
    var depth = 0
    var outList: TestItemList? = null
    var lastItemInfo: TestItemInfo? = null
}

// [Internal] Locate item position/window/state given ID.
class TestLocateTask(
        var id: ID = 0,
        var frameCount: Int = -1        // Timestamp of request
) {
    var debugName = ByteArray(64)  // char[64]
    val result = TestItemInfo()
    override fun toString() = "id=${id.toULong()} frameCount=$frameCount debugName=${debugName.cStr}"
}

class TestRunTask(var test: Test? = null,
                  var runFlags: TestRunFlags = TestRunFlag.None.i)

class TestInputs {
    val simulatedIO = IO()
    var applyingSimulatedIO = 0
    val mousePosValue = Vec2()             // Own non-rounded copy of MousePos in order facilitate simulating mouse movement very slow speed and high-framerate
    val hostLastMousePos = Vec2()
    var mouseButtonsValue = 0x00        // FIXME-TESTS: Use simulated_io.MouseDown[] ?
    var keyMods = KeyMod.None.i   // FIXME-TESTS: Use simulated_io.KeyXXX ?
    val queue = ArrayList<TestInput>()
}

// [Internal] Test Engine Context
class TestEngine {

    val io = TestEngineIO()
    var uiContextVisible: Context? = null        // imgui context for visible/interactive needs
    var uiContextBlind: Context? = null           // FIXME: Unsupported
    var uiContextTarget: Context? = null         // imgui context for testing == io.ConfigRunBlind ? UiBlindContext : UiVisibleContext when running tests, otherwise NULL.
    var uiContextActive: Context? = null         // imgui context for testing == UiContextTarget or NULL

    var started = false
    var frameCount = 0
    var overrideDeltaTime = -1f      // Inject custom delta time into imgui context to simulate clock passing faster than wall clock time.
    val testsAll = ArrayList<Test>()
    val testsQueue = ArrayList<TestRunTask>()
    var testContext: TestContext? = null
    val locateTasks = ArrayList<TestLocateTask>()
    val gatherTask = TestGatherTask()
    var userDataBuffer: ByteBuffer? = null
    var userData: Any? = null
    /** Coroutine to run the test queue */
    var testQueueCoroutine: TestCoroutine? = null
    /** Flag to indicate that we are shutting down and the test queue coroutine should stop */
    var testQueueCoroutineShouldExit = false

    // Inputs
    var inputs = TestInputs()

    // UI support
    var abort = false
    var uiFocus = false
    var uiSelectAndScrollToTest: Test? = null
    var uiSelectedTest: Test? = null
    val uiFilterTests = TextFilter()
    val uiFilterPerfs = TextFilter()
    var uiLogHeight = 150f

    // Performance Monitor
    var perfRefDeltaTime = 0.0
    val perfDeltaTime100 = MovingAverageDouble(100)
    val perfDeltaTime500 = MovingAverageDouble(500)
    val perfDeltaTime1000 = MovingAverageDouble(1000)
    val perfDeltaTime2000 = MovingAverageDouble(2000)


    // Tools
    var toolSlowDown = false
    var toolSlowDownMs = 100
    val captureTool = CaptureTool()
    lateinit var captureContext: CaptureContext
    var currentCaptureArgs: CaptureArgs? = null
    var runFastBackupValue = false

    // Functions
    fun destroy() {
        assert(testQueueCoroutine == null)
        uiContextBlind?.destroy()
    }
}