package engine

import engine.context.TestContext
import engine.engine.*
import glm_.vec2.Vec2
import imgui.ID
import imgui.ImGui
import imgui.KeyMod
import imgui.cStr
import imgui.classes.Context
import imgui.classes.IO
import imgui.classes.TextFilter
import imgui.internal.sections.ItemStatusFlag
import unsigned.toULong
import java.nio.ByteBuffer
import kotlin.reflect.KMutableProperty0


//-------------------------------------------------------------------------
// DATA STRUCTURES
//-------------------------------------------------------------------------

// [Internal] Locate item position/window/state given ID.
class TestInfoTask(
        // Input
        var id: ID = 0,
        var frameCount: Int = -1        // Timestamp of request
) {
    // Input
    var debugName = ByteArray(64)  // char[64] // Debug string representing the queried ID

    // Output
    val result = TestItemInfo()

    override fun toString() = "id=${id.toULong()} frameCount=$frameCount debugName=${debugName.cStr}"
}

// Gather item list in given parent ID.
class TestGatherTask {

    // Input
    var inParentID: ID = 0
    var inDepth = 0

    // Output/Temp
    var outList: TestItemList? = null
    var lastItemInfo: TestItemInfo? = null
}

// Processed by test queue
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
class TestEngine(uiCtx: Context) {

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
    val infoTasks = ArrayList<TestInfoTask>()
    val gatherTask = TestGatherTask()
    val findByLabelTask = TestFindByLabelTask()
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
    var uiFilterFailingOnly = false
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
    val stackTool = StackTool()
    val captureTool = CaptureTool()
    lateinit var captureContext: CaptureContext
    var currentCaptureArgs: CaptureArgs? = null
    var backupConfigRunFast = false
    var backupConfigNoThrottle = false

    // Functions
    /** ~ImGuiTestEngine_CreateContext */
    init {
        bindImGuiContext(uiCtx)
    }

    fun destroy() {
        assert(testQueueCoroutine == null)
        uiContextBlind?.destroy()
    }
}

// Find item ID given a label and a parent id
class TestFindByLabelTask {

    // Input
    var inBaseId: ID = 0                           // A known base ID which appears before wildcard ID(s)
    var inLabel: String? = null                        // A label string which appears on ID stack after unknown ID(s)
    var inFilterItemStatusFlags = ItemStatusFlag.None.i     // Flags required for item to be returned

    // Output
    var outItemId: ID = 0                          // Result item ID
}

class StackLevelInfo {
    var id:                 ID = 0
    var queryStarted = false
    var querySuccess = false       // Obtained infos from PushID() hook
    var desc = ""
}

class StackTool {
    var visible = false
    var queryStackId: ID = 0           // Stack id to query details for
    var queryStep = -1
    var queryIdInfoOutput: StackLevelInfo? = null   // Current stack level we're hooking PushID for
    var queryIdInfoTimestamp = -1
    val results = ArrayList<StackLevelInfo>()

    fun showStackToolWindow(engine: TestEngine, pOpen: KMutableProperty0<Boolean>) {

        val g = engine.uiContextVisible!!
        if (!ImGui.begin("Stack Tool", pOpen)) {
            ImGui.end()
            return
        }

        // Quick status
        val hoveredId = g.hoveredIdPreviousFrame
        val activeId = g.activeId
        val hoveredIdInfo = if(hoveredId != 0) engine.findItemInfo(hoveredId, "") else null
        val activeIdInfo = if(activeId != 0) engine.findItemInfo(activeId, "") else null
        ImGui.text("HoveredId: 0x%08X (\"${hoveredIdInfo?.debugLabel ?: ""}\")", hoveredId)
        ImGui.text("ActiveId:  0x%08X (\"${activeIdInfo?.debugLabel ?: ""}\")", activeId)
        if (ImGui.button("Item Picker..."))
            ImGui.debugStartItemPicker()
        ImGui.separator()

        // Display decorated stack
        for (n in results.indices) {

            val info = results[n]

            ImGui.text("0x%08X", info.id)
            ImGui.sameLine(ImGui.calcTextSize("0xDDDDDDDD  ").x)

            // Source: window name (because the root ID don't call GetID() and so doesn't get hooked)
            if (info.desc.isEmpty() && n == 0) {
                val window = ImGui.findWindowByID(info.id)
                if (window != null) {
                    ImGui.text("str \"${window.name}\"")
                    continue
                }
            }

            // Source: GetD() hooks
            // Priority over ItemInfo() because we frequently use patterns like: PushID(str), Button("") (same id)
            if (info.querySuccess) {
                ImGui.text(info.desc)
                continue
            }

            // Source: ItemInfo()
            // FIXME: Ambiguity between empty label (which is a string) and custom ID (which is no)
//            #if 1
            val newInfo = engine.findItemInfo(info.id, "")
            if (newInfo != null)            {
                ImGui.text("??? \"${newInfo.debugLabel}\"")
                continue
            }
//            #endif

            ImGui.text("???")
        }

        ImGui.end()

        updateQueries(engine)
    }

    fun updateQueries(engine: TestEngine) {
        // Steps
        // -1 Idle
        //  0 Query stack
        //  + Query each stack level
        val g = engine.uiContextVisible!!
        val queryId = if(g.activeId != 0) g.activeId else g.hoveredIdPreviousFrame
        if (queryStackId != queryId) {
            queryStackId = queryId
            queryStep = 0
            results.clear()
        }

        // We can only perform 1 ID Info query every frame.
        // This is designed so the ImGui:: doesn't have to pay a non-trivial cost.
        queryIdInfoOutput?.also {
            if (it.querySuccess)
                queryStep++
            else if (g.frameCount + 2 >= queryIdInfoTimestamp)
                queryStep++ // Drop query for this level (e.g. level 0 doesn't have a result)
        }

        if (queryStep >= 1) {
            val level = queryStep - 1
            if (level in results.indices) {
                // Start query for one level of the ID stack
                queryIdInfoOutput = results[level]
                queryIdInfoTimestamp = g.frameCount
                queryIdInfoOutput!!.queryStarted = true
                assert(!queryIdInfoOutput!!.querySuccess)
            }
            else {
                queryIdInfoOutput = null
                queryIdInfoTimestamp = -1
            }
        }
        engine.updateHooks()
    }

}