package engine.core

import engine.TestEngine
import engine.TestRunTask
import engine.pathFindFilename
import glm_.max
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import glm_.vec4.Vec4i
import imgui.*
import imgui.classes.Context
import imgui.classes.InputTextCallbackData
import imgui.classes.TextFilter
import imgui.internal.classes.Rect
import imgui.internal.classes.Window
import imgui.internal.sections.Axis
import imgui.internal.sections.ItemStatusFlags
import imgui.internal.sections.NavLayer
import kool.free
import java.nio.ByteBuffer
import kotlin.reflect.KMutableProperty0
import imgui.TabBarFlag as Tbf
import imgui.WindowFlag as Wf

//-------------------------------------------------------------------------
// ImGuiTestEngine API
//-------------------------------------------------------------------------

// Functions

// Create test context and attach to imgui context
fun testEngine_createContext(imguiContext: Context): TestEngine {
    val engine = TestEngine().apply {
        uiContextVisible = imguiContext
//        engine->UiContextBlind = NULL
        uiContextTarget = uiContextVisible
//        engine->UiContextActive = NULL
    }

    // Setup hook
    if (gHookingEngine == null)
        gHookingEngine = engine

    Hook.preNewFrame = ::hookPrenewframe
    Hook.postNewFrame = ::hookPostnewframe
    Hook.itemAdd = ::hookItemAdd
    Hook.itemInfo = ::hookItemInfo
    Hook.log = ::hookLog

    // Add .ini handle for ImGuiWindow type
//    ImGuiSettingsHandler ini_handler
//    ini_handler.TypeName = "TestEngine"
//    ini_handler.TypeHash = ImHashStr("TestEngine")
//    ini_handler.ReadOpenFn = ImGuiTestEngine_SettingsReadOpen
//    ini_handler.ReadLineFn = ImGuiTestEngine_SettingsReadLine
//    ini_handler.WriteAllFn = ImGuiTestEngine_SettingsWriteAll
//    imgui_context->SettingsHandlers.push_back(ini_handler)

    return engine
}

fun TestEngine.shutdownContext() {

    // Shutdown coroutine
    coroutineStopAndJoin()

    uiContextVisible = null
    uiContextBlind = null
    uiContextTarget = null
    uiContextActive = null

    userDataBuffer?.free()
    userDataBuffer = null
//    userDataBufferSize = 0

    clearTests()
    clearLocateTasks()

    // Release hook
    if (gHookingEngine === this)
        gHookingEngine = null
}

//ImGuiTestEngineIO&  ImGuiTestEngine_GetIO(ImGuiTestEngine* engine) [JVM] -> Class
fun TestEngine.abort() {
    abort = true
    testContext!!.abort = true
}






fun TestEngine.registerTest(category: String, name: String, srcFile: String? = null, srcLine: Int = 0): Test {

    val group = if (category == "perf") TestGroup.Perfs else TestGroup.Tests

    val t = Test()
    t.group = group
    t.category = category
    t.name = name
    t.sourceFile = srcFile
    t.sourceFileShort = srcFile
    t.sourceLine = srcLine
    t.sourceLineEnd = srcLine
    testsAll += t

    // Find filename only out of the fully qualified source path
    srcFile?.let { t.sourceFileShort = pathFindFilename(srcFile) }

    return t
}

fun TestEngine.queueTests(group: TestGroup, filterStr: String? = null, runFlags: TestRunFlags = TestRunFlag.None.i) {
    assert(group.i < TestGroup.COUNT.i)
    val filter = TextFilter()
    testsAll.filter { it.group == group && filter.passFilter(it.name!!) }.forEach { queueTest(it, runFlags) }
}

fun TestEngine.queueTest(test: Test, runFlags: TestRunFlags) {

    if (isRunningTest(test))
        return

    // Detect lack of signal from imgui context, most likely not compiled with IMGUI_ENABLE_TEST_ENGINE=1
    if (frameCount < uiContextTarget!!.frameCount - 2) {
        abort()
        assert(false) { "Not receiving signal from core library. Did you call ImGuiTestEngine_CreateContext() with the correct context? Did you compile imgui/ with IMGUI_ENABLE_TEST_ENGINE=1?" }
        test.status = TestStatus.Error
        return
    }

    test.status = TestStatus.Queued

    testsQueue += TestRunTask(test, runFlags)
}

val TestEngine.isRunningTests get() = testsQueue.isNotEmpty()

infix fun TestEngine.isRunningTest(test: Test): Boolean = testsQueue.any { it.test === test }

fun TestEngine.calcSourceLineEnds() {
    TODO()
//    if (engine->TestsAll.empty())
//    return;
//
//    ImVector<int> line_starts;
//    line_starts.reserve(engine->TestsAll.Size);
//    for (int n = 0; n < engine->TestsAll.Size; n++)
//    line_starts.push_back(engine->TestsAll[n]->SourceLine);
//    ImQsort(line_starts.Data, (size_t)line_starts.Size, sizeof(int), [](const void* lhs, const void* rhs) { return (*(const int*)lhs) - *(const int*)rhs; });
//
//    for (int n = 0; n < engine->TestsAll.Size; n++)
//    {
//        ImGuiTest* test = engine->TestsAll[n];
//        for (int m = 0; m < line_starts.Size - 1; m++) // FIXME-OPT
//        if (line_starts[m] == test->SourceLine)
//        test->SourceLineEnd = ImMax(test->SourceLine, line_starts[m + 1]);
//    }
}

fun TestEngine.printResultSummary() {
    val (countTested, countSuccess) = result
    val res = if (countSuccess == countTested) "OK" else "KO"
    println("Tests Result: $res\n($countSuccess/$countTested tests passed)")
}

fun TestEngine.coroutineStopRequest() {
    if(testQueueCoroutine != null)
        testQueueCoroutineShouldExit = true
}

fun TestEngine.coroutineStopAndJoin() {
    testQueueCoroutine?.let {
        // Run until the coroutine exits
        testQueueCoroutineShouldExit = true
        TODO()
//        while (true) {
//            if (!io.coroutineRunFunc(engine->TestQueueCoroutine))
//            break;
//        }
//        engine->IO.CoroutineDestroyFunc(engine->TestQueueCoroutine);
//        engine->TestQueueCoroutine = NULL;
    }
}


val TestEngine.result: Pair<Int, Int>
    get() {
        var countTested = 0
        var countSuccess = 0
        for (test in testsAll) {
            if (test.status == TestStatus.Unknown) continue
            assert(test.status != TestStatus.Queued && test.status != TestStatus.Running)
            countTested++
            if (test.status == TestStatus.Success)
                countSuccess++
        }
        return countTested to countSuccess
    }

// Function pointers for IO structure
typealias TestEngineNewFrameFunc = (_: TestEngine, userData: Any?) -> Boolean
typealias TestEngineEndFrameFunc = (_: TestEngine, userData: Any?) -> Boolean
typealias TestEngineSrcFileOpenFunc = (filename: String, line: Int, userData: Any?) -> Unit
typealias TestEngineScreenCaptureFunc = (extend: Vec4i, pixels: ByteBuffer, userData: Any?) -> Boolean

// IO structure
class TestEngineIO {
    var userData: Any? = null
    var srcFileOpenFunc: TestEngineSrcFileOpenFunc? = null     // (Optional) To open source files
    var screenCaptureFunc: TestEngineScreenCaptureFunc? = null  // (Optional) To capture graphics output

//    ImGuiTestCoroutineInterface*        CoroutineFuncs = NULL;          // (Required) Coroutine functions (see imgui_te_coroutines.h)

    // Inputs: Options
    var configRunWithGui = false       // Run without graphics output (e.g. command-line)
    var configRunFast = true           // Run tests as fast as possible (teleport mouse, skip delays, etc.)
    var configRunBlind = false         // Run tests in a blind ImGuiContext separated from the visible context
    var configStopOnError = false      // Stop queued tests on test error
    var configBreakOnError = false     // Break debugger on test error
    var configKeepGuiFunc = false      // Keep test GUI running at the end of the test
    var configVerboseLevel = TestVerboseLevel.Warning
    var configVerboseLevelOnError = TestVerboseLevel.Info
    var configLogToTTY = false
    var configTakeFocusBackAfterTests = true
    var configNoThrottle = false       // Disable vsync for performance measurement
    var dpiScale = 1f
    var mouseSpeed = 800f            // Mouse speed (pixel/second) when not running in fast mode
    var mouseWobble = 0.25f            // How much wobble to apply to the mouse (pixels per pixel of move distance) when not running in fast mode
    var scrollSpeed = 1600f          // Scroll speed (pixel/second) when not running in fast mode
    var typingSpeed = 30f            // Char input speed (characters/second) when not running in fast mode
    var perfStressAmount = 1           // Integer to scale the amount of items submitted in test
    var gitBranchName = ""        // e.g. fill in branch name

    // Outputs: State
    var runningTests = false
}

// Result of an ItemLocate query
class TestItemInfo {
    var refCount = 0               // User can increment this if they want to hold on the result pointer across frames, otherwise the task will be GC-ed.
    var navLayer = NavLayer.Main              // Nav layer of the item
    var depth = 0              // Depth from requested parent id. 0 == ID is immediate child of requested parent id.
    var timestampMain = -1         // Timestamp of main result (all fields)
    var timestampStatus = -1       // Timestamp of StatusFlags
    var id: ID = 0                     // Item ID
    var parentID: ID = 0               // Item Parent ID (value at top of the ID stack)
    var window: Window? = null              // Item Window
    var rectFull = Rect()        // Item Rectangle
    var rectClipped = Rect()     // Item Rectangle (clipped with window->ClipRect at time of item submission)
    var statusFlags: ItemStatusFlags = 0            // Item Status flags (fully updated for some items only, compare TimestampStatus to FrameCount)
    var debugLabel/*[32]*/ = ""         // Shortened label for debugging purpose
}

// Result of an ItemGather query
class TestItemList {
    val list = ArrayList<TestItemInfo>()
    val map = mutableMapOf<ID, Int>()

    fun clear() {
        list.clear()
        map.clear()
    }

    fun reserve(capacity: Int) = list.ensureCapacity(capacity)
    operator fun get(n: Int): TestItemInfo = getByIndex(n)
    infix fun getByIndex(n: Int): TestItemInfo = list[n]
    infix fun getByID(id: ID): Int? = map[id]
    infix fun getOrAddByKey(key: ID): TestItemInfo = map[key]?.let { list[it] }
            ?: add().also { map[key] = list.lastIndex }

    fun add(): TestItemInfo = TestItemInfo().also { list += it }
    val size get() = list.size
    val lastIndex get() = list.lastIndex
}


// Helper to output a string showing the Path, ID or Debug Label based on what is available (some items only have ID as we couldn't find/store a Path)
class TestRefDesc(val ref: TestRef, val item: TestItemInfo? = null) {
    override fun toString(): String = ref.path?.let { "'$it' > %08X".format(ref.id) }
            ?: "%08X > '${item?.debugLabel}'".format(ref.id)
}