package engine.engine

import engine.*
import glm_.i
import glm_.max
import glm_.vec2.Vec2
import glm_.vec4.Vec4i
import imgui.*
import imgui.ImGui.addHook
import imgui.api.g
import imgui.api.gImGui
import imgui.classes.*
import imgui.internal.classes.Rect
import imgui.internal.classes.Window
import imgui.internal.round
import imgui.internal.sections.Axis
import imgui.internal.sections.ItemStatusFlags
import imgui.internal.sections.NavLayer
import kool.free
import java.nio.ByteBuffer
import kotlin.reflect.KMutableProperty0

//-------------------------------------------------------------------------
// ImGuiTestEngine API
//-------------------------------------------------------------------------

// Functions: Initialization


// Create test engine
fun TestEngine.bindImGuiContext(uiCtx: Context) {
    assert(uiContextTarget == null)

    uiContextVisible = uiCtx
    uiContextBlind = null
    uiContextTarget = uiContextVisible
    uiContextActive = null

    // Add .ini handle for ImGuiWindow type
//    ImGuiSettingsHandler ini_handler
//    ini_handler.TypeName = "TestEngine"
//    ini_handler.TypeHash = ImHashStr("TestEngine")
//    ini_handler.ReadOpenFn = ImGuiTestEngine_SettingsReadOpen
//    ini_handler.ReadLineFn = ImGuiTestEngine_SettingsReadLine
//    ini_handler.WriteAllFn = ImGuiTestEngine_SettingsWriteAll
//    imgui_context->SettingsHandlers.push_back(ini_handler)

    // Install generic context hooks facility
    uiCtx addHook ContextHook(
            type = ContextHookType.Shutdown,
            callback = { uiCtx: Context, hook: ContextHook -> hook.userData as TestEngine unbindImGuiContext uiCtx },
            userData = this)

    uiCtx addHook ContextHook(
            type = ContextHookType.NewFramePre,
            callback = { uiCtx: Context, hook: ContextHook -> hook.userData as TestEngine preNewFrame uiCtx },
            userData = this)

    uiCtx addHook ContextHook(
            type = ContextHookType.NewFramePost,
            callback = { uiCtx: Context, hook: ContextHook -> hook.userData as TestEngine postNewFrame uiCtx },
            userData = this)

    // Install custom test engine hook data
    if (gTestEngine == null)
        gTestEngine = this
    assert(uiCtx.testEngine == null)
    uiCtx.testEngine = this
}

infix fun TestEngine.unbindImGuiContext(uiCtx: Context) {

    assert(uiContextTarget === uiCtx)

    coroutineStopAndJoin()

//    #if IMGUI_VERSION_NUM >= 17701
    assert(uiCtx.testEngine === this)
    uiCtx.testEngine = null

    // Remove .ini handler
    assert(gImGui === uiCtx)
    ImGui.findSettingsHandler("TestEngine")?.let {
//    TODO()
//        imguiContext->SettingsHandlers.erase(imgui_context->SettingsHandlers.Data + imgui_context->SettingsHandlers.index_from_ptr(ini_handler))
    }
//    #endif

    // Remove hook
    if (gTestEngine === this)
        gTestEngine = null

    uiContextVisible = null
    uiContextBlind = null
    uiContextTarget = null
    uiContextActive = null
}

// Create test context and attach to imgui context
//ImGuiTestEngine*    ImGuiTestEngine_CreateContext(ImGuiContext* imgui_context) => TestEngine constructor

// Destroy test engine. Call after ImGui::DestroyContext() so test engine specific ini data gets saved.
fun TestEngine.shutdownContext() {

    // Shutdown coroutine
    coroutineStopAndJoin()
    uiContextTarget?.let(::unbindImGuiContext)

    userDataBuffer?.free()
    userDataBuffer = null
//    userDataBufferSize = 0

    clearTests()

    infoTasks.clear()

    // Release hook
    if (gTestEngine === this)
        gTestEngine = null
}

fun TestEngine.start() {

    assert(!started)

    startCalcSourceLineEnds()

    // Create our coroutine
    // (we include the word "Main" in the name to facilitate filtering for both this thread and the "Main Thread" in debuggers)
    if (testQueueCoroutine == null)
        TODO()
//        testQueueCoroutine = this.io.coroutineFuncs->CreateFunc(ImGuiTestEngine_TestQueueCoroutineMain, "Main Dear ImGui Test Thread", engine);

    started = true
}

fun TestEngine.stop() {

    assert(started)

    coroutineStopAndJoin()
    started = false
}

// Call every frame after framebuffer swap, will process screen capture.
fun TestEngine.postRender() {

    if (this.io.configFixedDeltaTime != 0f)
        setDeltaTime(this.io.configFixedDeltaTime)

    // Capture a screenshot from main thread while coroutine waits
    currentCaptureArgs?.let {
        captureContext.screenCaptureFunc = this.io.screenCaptureFunc
        captureContext.screenCaptureUserData = this.io.screenCaptureUserData
        val status = captureContext.captureUpdate(it)
        if (status != CaptureStatus.InProgress) {
            if (status == CaptureStatus.Done)
                captureTool.lastSaveFileName = it.outSavedFileName
            //else
            //    ImFileDelete(engine->CurrentCaptureArgs->OutSavedFileName);
            currentCaptureArgs = null
        }
    }
}


// Functions: Main


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

fun TestEngine.queueTest(test: Test, runFlags: TestRunFlags = TestRunFlag.None.i) {

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

//ImGuiTestEngineIO&  ImGuiTestEngine_GetIO(ImGuiTestEngine* engine) [JVM] -> Class
fun TestEngine.abort() {
    abort = true
    testContext!!.abort = true
}

// FIXME: Clarify difference between this and io.RunningTests
val TestEngine.isRunningTests: Boolean
    get() = testsQueue.isNotEmpty()

infix fun TestEngine.isRunningTest(test: Test): Boolean = testsQueue.any { it.test === test }

fun TestEngine.coroutineStopRequest() {
    if (testQueueCoroutine != null)
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

fun TestEngine.printResultSummary() {
    val (countTested, countSuccess) = result
    val res = if (countSuccess == countTested) "OK" else "KO"
    println("Tests Result: $res\n($countSuccess/$countTested tests passed)")
}


// Functions: UI


fun TestEngine.showTestWindow(pOpen: KMutableProperty0<Boolean>? = null) {

    if (uiFocus) {
        ImGui.setNextWindowFocus()
        uiFocus = false
    }
    ImGui.begin("Dear ImGui Test Engine", pOpen)// , ImGuiWindowFlags_MenuBar);

//    #if 0
//    if (0 && ImGui::BeginMenuBar())
//    {
//        if (ImGui::BeginMenu("Options"))
//        {
//            const bool busy = ImGuiTestEngine_IsRunningTests(engine)
//            ImGui::MenuItem("Run fast", NULL, &engine->IO.ConfigRunFast, !busy)
//            ImGui::MenuItem("Run in blind context", NULL, &engine->IO.ConfigRunBlind)
//            ImGui::MenuItem("Debug break on error", NULL, &engine->IO.ConfigBreakOnError)
//            ImGui::EndMenu()
//        }
//        ImGui::EndMenuBar()
//    }
//    #endif


    // Options
    ImGui.pushStyleVar(StyleVar.FramePadding, Vec2())
    ImGui.checkbox("Fast", io::configRunFast); helpTooltip("Run tests as fast as possible (no vsync, no delay, teleport mouse, etc.).")
    ImGui.sameLine()
    ImGui.pushDisabled()
    ImGui.checkbox("Blind", io::configRunBlind)
    ImGui.popDisabled()
    helpTooltip("<UNSUPPORTED>\nRun tests in a blind ui context.")
    ImGui.sameLine()
    ImGui.checkbox("Stop", io::configStopOnError); helpTooltip("Stop running tests when hitting an error.")
    ImGui.sameLine()
    ImGui.checkbox("DbgBrk", io::configBreakOnError); helpTooltip("Break in debugger when hitting an error.")
    ImGui.sameLine()
    ImGui.checkbox("KeepGUI", io::configKeepGuiFunc); helpTooltip("Keep GUI function running after test function is finished.")
    ImGui.sameLine()
    ImGui.checkbox("Refocus", io::configTakeFocusBackAfterTests); helpTooltip("Set focus back to Test window after running tests.")
    ImGui.sameLine()
    ImGui.setNextItemWidth(60f * io.dpiScale)
    _i = io.configVerboseLevel.i
    if (ImGui.dragInt("Verbose", ::_i, 0.1f, 0, TestVerboseLevel.COUNT.i - 1, io.configVerboseLevel.name))
        io.configVerboseLevelOnError = io.configVerboseLevel
    io.configVerboseLevel = TestVerboseLevel(_i)
    ImGui.sameLine()
    ImGui.setNextItemWidth(30f)
    ImGui.dragInt("Perf Stress Amount", io::perfStressAmount, 0.1f, 1, 20); helpTooltip("Increase workload of performance tests (higher means longer run).")
    ImGui.popStyleVar()
    ImGui.separator()

    // FIXME-DOGFOODING: It would be about time that we made it easier to create splitters, current low-level splitter behavior is not easy to use properly.
    // FIXME-SCROLL: When resizing either we'd like to keep scroll focus on something (e.g. last clicked item for list, bottom for log)
    // See https://github.com/ocornut/imgui/issues/319
    val availY = ImGui.contentRegionAvail.y - ImGui.style.itemSpacing.y
    val minSize0 = ImGui.frameHeight * 1.2f
    val minSize1 = ImGui.frameHeight * 1
    var logHeight = uiLogHeight
    var listHeight = (availY - uiLogHeight) max minSize0
    run {
        val window = ImGui.currentWindow
        val y = ImGui.cursorScreenPos.y + listHeight + round(ImGui.style.itemSpacing.y * 0.5f)
        val splitterBb = Rect(window.workRect.min.x, y - 1, window.workRect.max.x, y + 1)
        _f = listHeight
        _f1 = logHeight
        ImGui.splitterBehavior(splitterBb, ImGui.getID("splitter"), Axis.Y, ::_f, ::_f1, minSize0, minSize1, 3f)
        uiLogHeight = logHeight
        listHeight = _f
        logHeight = _f1
//        ImGui.debugDrawItemRect()
    }

    // TESTS
    ImGui.beginChild("List", Vec2(0, listHeight), false, WindowFlag.NoScrollbar.i)
    dsl.tabBar("##Tests", TabBarFlag.NoTooltip.i) {
        dsl.tabItem("TESTS") { showTestGroup(this, TestGroup.Tests, uiFilterTests) }
        dsl.tabItem("PERFS") { showTestGroup(this, TestGroup.Perfs, uiFilterPerfs) }
    }
    ImGui.endChild()
    uiSelectAndScrollToTest = null

    // LOG
    ImGui.beginChild("Log", Vec2(0f, logHeight))
    dsl.tabBar("##tools") {
        dsl.tabItem("LOG") {
            ImGui.text(uiSelectedTest?.run { "Log for $category: $name" } ?: "N/A")
            if (ImGui.smallButton("Clear"))
                uiSelectedTest?.testLog?.clear()
            ImGui.sameLine()
            if (ImGui.smallButton("Copy to clipboard"))
                uiSelectedTest?.let { ImGui.clipboardText = it.testLog.buffer.toString() }
            ImGui.separator()

            ImGui.beginChild("Log")
            uiSelectedTest?.let {
                drawTestLog(it, true)
                if (ImGui.scrollY >= ImGui.scrollMaxY)
                    ImGui.setScrollHereY()
            }
            ImGui.endChild()
        }

        // Tools
        dsl.tabItem("TOOLS") {
            val io = ImGui.io
            ImGui.text("%.3f ms/frame (%.1f FPS)", 1000f / io.framerate, io.framerate)
            ImGui.text("TestEngine: HookItems: ${g.testEngineHookItems.i}, HookPushId: ${(g.testEngineHookIdInfo != 0).i}, InfoTasks: ${infoTasks.size}")
            ImGui.separator()

            ImGui.checkbox("Stack Tool", stackTool::visible)
            ImGui.checkbox("Capture Tool", captureTool::visible)
            ImGui.checkbox("Slow down whole app", ::toolSlowDown)
            ImGui.sameLine(); ImGui.setNextItemWidth(70f * this.io.dpiScale)
            ImGui.sliderInt("##ms", ::toolSlowDownMs, 0, 400, "%d ms")

            ImGui.checkboxFlags("io.ConfigFlags: NavEnableKeyboard", io::configFlags, ConfigFlag.NavEnableKeyboard.i)
            ImGui.checkboxFlags("io.ConfigFlags: NavEnableGamepad", io::configFlags, ConfigFlag.NavEnableGamepad.i)
//            if (helpers.getIMGUI_HAS_DOCK)
//                ImGui.checkbox("io.ConfigDockingAlwaysTabBar", io::configDockingAlwaysTabBar)

            ImGui.dragFloat("DpiScale", this.io::dpiScale, 0.005f, 0f, 0f, "%.2f")
            val filterCallback: InputTextCallback = { data: InputTextCallbackData -> data.eventChar == ',' || data.eventChar == ';' }
            ImGui.inputText("Branch/Annotation", this.io.gitBranchName, InputTextFlag.CallbackCharFilter.i, filterCallback)
        }

        // FIXME-TESTS: Need to be visualizing the samples/spikes.
        dsl.tabItem("PERFS") {
            val dt1 = 1.0 / ImGui.io.framerate
            val fpsNow = 1.0 / dt1
            val dt100 = perfDeltaTime100.average
            val dt1000 = perfDeltaTime1000.average
            val dt2000 = perfDeltaTime2000.average

            //if (engine->PerfRefDeltaTime <= 0.0 && engine->PerfRefDeltaTime.IsFull())
            //    engine->PerfRefDeltaTime = dt_2000;

            ImGui.checkbox("Unthrolled", io::configNoThrottle)
            ImGui.sameLine()
            if (ImGui.button("Pick ref dt"))
                perfRefDeltaTime = dt2000

            val dtRef = perfRefDeltaTime
            ImGui.text("[ref dt]    %6.3f ms", perfRefDeltaTime * 1000)
            ImGui.text("[last 0001] %6.3f ms (%.1f FPS) ++ %6.3f ms", dt1 * 1000.0, 1.0 / dt1, (dt1 - dtRef) * 1000)
            ImGui.text("[last 0100] %6.3f ms (%.1f FPS) ++ %6.3f ms ~ converging in %.1f secs", dt100 * 1000.0, 1.0 / dt100, (dt1 - dtRef) * 1000, 100.0 / fpsNow)
            ImGui.text("[last 1000] %6.3f ms (%.1f FPS) ++ %6.3f ms ~ converging in %.1f secs", dt1000 * 1000.0, 1.0 / dt1000, (dt1 - dtRef) * 1000, 1000.0 / fpsNow)
            ImGui.text("[last 2000] %6.3f ms (%.1f FPS) ++ %6.3f ms ~ converging in %.1f secs", dt2000 * 1000.0, 1.0 / dt2000, (dt1 - dtRef) * 1000, 2000.0 / fpsNow)

            //PlotLines("Last 100", &engine->PerfDeltaTime100.Samples.Data, engine->PerfDeltaTime100.Samples.Size, engine->PerfDeltaTime100.Idx, NULL, 0.0f, dt_1000 * 1.10f, ImVec2(0.0f, GetFontSize()));
            val plotSize = Vec2(0f, ImGui.frameHeight * 3)
            val ma = perfDeltaTime500
//            ImGui.plotLines("Last 500", TODO
//                    { n -> val ma = (MovingAverageDouble) data; return (float)(ma->Samples[n] * 1000); },
//                    ma, ma->Samples.Size, 0*ma->Idx, NULL, 0.0f, (float)(ImMax(dt_100, dt_1000) * 1000.0 * 1.2f), plot_size)
        }
    }
    ImGui.endChild()

    ImGui.end()

    // Stack Tool
    if (stackTool.visible)
        stackTool.showStackToolWindow(this, stackTool::visible)

    // Capture Tool
    captureTool.context.screenCaptureFunc = io.screenCaptureFunc
    captureTool.context.screenCaptureUserData = io.screenCaptureUserData
    if (captureTool.visible)
        captureTool.showCaptureToolWindow(captureTool::visible)
}


// Function pointers for IO structure
// (also see imgui_te_coroutine.h for coroutine functions)
typealias TestEngineSrcFileOpenFunc = (filename: String, line: Int, userData: Any?) -> Unit
typealias TestEngineScreenCaptureFunc = (extend: Vec4i, pixels: ByteBuffer, userData: Any?) -> Boolean

// IO structure
class TestEngineIO {

    // Inputs: Functions
    var srcFileOpenFunc: TestEngineSrcFileOpenFunc? = null     // (Optional) To open source files
    var srcFileOpenUserData: Any? = null     // (Optional) User data for SrcFileOpenFunc
    var screenCaptureFunc: TestEngineScreenCaptureFunc? = null  // (Optional) To capture graphics output
    var screenCaptureUserData: Any? = null   // (Optional) User data for ScreenCaptureFunc

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
    var configLogToDebugger = false
    var configTakeFocusBackAfterTests = true
    var configNoThrottle = false       // Disable vsync for performance measurement or fast test running
    var configFixedDeltaTime = 0f        // Use fixed delta time instead of calculating it from wall clock
    var dpiScale = 1f
    var mouseSpeed = 800f            // Mouse speed (pixel/second) when not running in fast mode
    var mouseWobble = 0.25f            // How much wobble to apply to the mouse (pixels per pixel of move distance) when not running in fast mode
    var scrollSpeed = 1600f          // Scroll speed (pixel/second) when not running in fast mode
    var typingSpeed = 30f            // Char input speed (characters/second) when not running in fast mode
    var perfStressAmount = 1           // Integer to scale the amount of items submitted in test
    var gitBranchName = ""        // e.g. fill in branch name

    // Outputs: State
    var runningTests = false
    var renderWantMaxSpeed = false
}

// Result of an ItemInfo query
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
class TestItemList : Iterable<TestItemInfo> {
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

    // For range-for

    override fun iterator(): Iterator<TestItemInfo> = list.iterator()
}


// Helper to output a string showing the Path, ID or Debug Label based on what is available (some items only have ID as we couldn't find/store a Path)
class TestRefDesc(val ref: TestRef, val item: TestItemInfo? = null) {
    override fun toString(): String = ref.path?.let { "'$it' > %08X".format(ref.id) }
            ?: "%08X > '${item?.debugLabel}'".format(ref.id)
}