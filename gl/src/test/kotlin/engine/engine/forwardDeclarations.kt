package engine.engine

import engine.KeyState
import engine.TestEngine
import engine.context.*
import gli_.has
import gli_.hasnt
import glm_.f
import glm_.wo
import imgui.*
import imgui.api.gImGui
import imgui.classes.Context
import imgui.internal.sections.InputSource
import shared.osIsDebuggerPresent
import shared.sleepInMilliseconds
import uno.kotlin.NUL
import kotlin.system.exitProcess

//-------------------------------------------------------------------------
// Forward Declarations
//-------------------------------------------------------------------------

//struct ImGuiTest;
//struct ImGuiTestContext;
//struct ImGuiTestEngine;
//struct ImGuiTestEngineIO;
//struct ImGuiTestItemInfo;
//struct ImGuiTestItemList;
//struct ImRect;

inline class TestFlags(val i: Int)         // Flags: See ImGuiTestFlags_
{
    infix fun hasnt(f: TestFlag): Boolean = i hasnt f.i.i
    infix fun has(f: TestFlag): Boolean = i has f.i.i
    infix fun or(f: TestFlag) = TestFlags(i or f.i.i)
}

inline class TestCheckFlags(val i: Int)    // Flags: See ImGuiTestCheckFlags_
{
    infix fun hasnt(f: TestCheckFlag): Boolean = i hasnt f.i.i
}

inline class TestLogFlags(val i: Int) {      // Flags: See ImGuiTestLogFlags_
    infix fun hasnt(f: TestLogFlag) = i hasnt f.i.i
}

inline class TestOpFlags(val i: Int)       // Flags: See ImGuiTestOpFlags_
{
    infix fun has(f: TestOpFlag): Boolean = i has f.i.i
    infix fun hasnt(f: TestOpFlag): Boolean = i hasnt f.i.i
    infix fun or(f: TestOpFlag) = TestOpFlags(i or f.i.i)
}

inline class TestRunFlags(val i: Int) {     // Flags: See ImGuiTestRunFlags_
    infix fun wo(f: TestRunFlag) = TestRunFlags(i wo f.i.i)
    infix fun or(f: TestRunFlag) = TestRunFlags(i or f.i.i)
    infix fun or(f: TestRunFlags) = TestRunFlags(i or f.i)
    infix fun hasnt(f: TestRunFlag) = i hasnt f.i.i
    infix fun has(f: TestRunFlags): Boolean = i has f.i
    infix fun has(f: TestRunFlag): Boolean = i has f.i.i
}

//-------------------------------------------------------------------------
// [SECTION] FORWARD DECLARATIONS
//-------------------------------------------------------------------------

// Private functions

fun TestEngine.startCalcSourceLineEnds() {
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

fun TestEngine.clearInput() {
    assert(uiContextTarget != null)
    inputs.apply {
        mouseButtonsValue = 0
        keyMods = KeyMod.None.i
        queue.clear()
    }
    inputs.simulatedIO.apply {
        keyCtrl = false
        keyShift = false
        keyAlt = false
        keySuper = false
        mouseDown.fill(false)
        keysDown.fill(false)
        navInputs.fill(0f)
        clearInputCharacters()
    }
    applyInputToImGuiContext()
}

fun TestEngine.applyInputToImGuiContext() {

    assert(uiContextTarget != null)
    val g = uiContextTarget!!

    val mainIo = g.io
    val simulatedIo = inputs.simulatedIO

    mainIo.mouseDrawCursor = true

    val useSimulatedInputs = useSimulatedInputs()
    if (useSimulatedInputs) {
        assert(testContext != null)

        // Clear host IO queues (because we can't easily just memcpy the vectors)
        if (inputs.applyingSimulatedIO == 0)
            simulatedIo.mousePos put mainIo.mousePos

        inputs.applyingSimulatedIO = 2
        mainIo.clearInputCharacters()

        // Process input requests/queues
        if (inputs.queue.isNotEmpty()) {
            for (input in inputs.queue)
                when (input.type) {
                    TestInputType.Key -> {
                        inputs.keyMods = when (input.state) {
                            KeyState.Down -> inputs.keyMods or input.keyMods
                            else -> inputs.keyMods wo input.keyMods
                        }

                        if (input.key != Key.Count) {
                            val idx = mainIo.keyMap[input.key]
                            if (idx in simulatedIo.keysDown.indices)
                                simulatedIo.keysDown[idx] = input.state == KeyState.Down
                        }
                    }
                    TestInputType.Nav -> {
                        assert(input.navInput != NavInput.Count)
                        simulatedIo.navInputs[input.navInput] = (input.state == KeyState.Down).f
                    }
                    TestInputType.Char -> {
                        assert(input.char != NUL)
                        mainIo.addInputCharacter(input.char)
                    }
                    else -> Unit
                }
            inputs.queue.clear()
        }

        // Apply mouse position
        simulatedIo.mousePos put inputs.mousePosValue
        //main_io.WantSetMousePos = true;
        for (n in simulatedIo.mouseDown.indices)
            simulatedIo.mouseDown[n] = inputs.mouseButtonsValue has (1 shl n)

        // Apply keyboard mods
        simulatedIo.keyCtrl = inputs.keyMods has KeyMod.Ctrl
        simulatedIo.keyAlt = inputs.keyMods has KeyMod.Alt
        simulatedIo.keyShift = inputs.keyMods has KeyMod.Shift
        simulatedIo.keySuper = inputs.keyMods has KeyMod.Super
        simulatedIo.keyMods = inputs.keyMods


        // Apply to real IO
        mainIo.apply {
            mousePos put simulatedIo.mousePos
            simulatedIo.mouseDown.copyInto(mouseDown)
            mouseWheel = simulatedIo.mouseWheel
            mouseWheelH = simulatedIo.mouseWheelH
            keyCtrl = simulatedIo.keyCtrl
            keyShift = simulatedIo.keyShift
            keyAlt = simulatedIo.keyAlt
            keySuper = simulatedIo.keySuper
            keyMods = simulatedIo.keyMods
            simulatedIo.keysDown.copyInto(keysDown)
            simulatedIo.navInputs.copyInto(navInputs)
        }

        // FIXME-TESTS: This is a bit of a mess, ideally we should be able to swap/copy/isolate IO without all that fuss..
        simulatedIo.navInputs.fill(0f)
        simulatedIo.clearInputCharacters()
    } else
        inputs.queue.clear()
}

fun TestEngine.useSimulatedInputs(): Boolean =
        uiContextActive?.let { isRunningTests && testContext!!.runFlags hasnt TestRunFlag.GuiFuncOnly } ?: false

fun TestEngine.processTestQueue() {

    // Avoid tracking scrolling in UI when running a single test
    val trackScrolling = testsQueue.size > 1 || (testsQueue.size == 1 && testsQueue[0].runFlags has TestRunFlag.CommandLine)
    val io = ImGui.io
    val settingsIniBackup = io.iniFilename
    io.iniFilename = null

    var ranTests = 0
    this.io.runningTests = true
    for (runTask in testsQueue) {
        val test = runTask.test!!
        assert(test.status == TestStatus.Queued)

        if (abort) {
            test.status = TestStatus.Unknown
            continue
        }

        // FIXME-TESTS: Blind mode not supported
        assert(uiContextTarget != null)
        assert(uiContextActive == null)
        uiContextActive = uiContextTarget
        uiSelectedTest = test
        test.status = TestStatus.Running

        val ctx = TestContext()
        ctx.test = test
        ctx.engine = this
        ctx.engineIO = this.io
        ctx.inputs = inputs
        ctx.gatherTask = gatherTask
        ctx.userData = null
        ctx.uiContext = uiContextActive
        ctx.perfStressAmount = this.io.perfStressAmount
        ctx.runFlags = runTask.runFlags
//        #ifdef helpers.getIMGUI_HAS_DOCK
//                ctx.HasDock = true
//        #else
        ctx.hasDock = false
//        #endif
        testContext = ctx
        updateHooks()
        if (trackScrolling)
            uiSelectAndScrollToTest = test

        ctx.logEx(TestVerboseLevel.Info, TestLogFlag.NoHeader.i, "----------------------------------------------------------------------")

        // Test name is not displayed in UI due to a happy accident - logged test name is cleared in
        // ImGuiTestEngine_RunTest(). This is a behavior we want.
        ctx.logWarning("Test: '${test.category}' '${test.name}'..")
        test.userData?.let {
            userData = it
        }
        // Run test with a custom data type in the stack
        ctx.userData = userDataBuffer
        runTest(ctx)
        ranTests++

        assert(testContext === ctx)

        assert(uiContextActive === uiContextTarget)
        testContext = null
        uiContextActive = null
        updateHooks()

        // Auto select the first error test
        //if (test->Status == ImGuiTestStatus_Error)
        //    if (engine->UiSelectedTest == NULL || engine->UiSelectedTest->Status != ImGuiTestStatus_Error)
        //        engine->UiSelectedTest = test;
    }
    this.io.runningTests = false

    abort = false
    testsQueue.clear()

    //ImGuiContext& g = *engine->UiTestContext;
    //if (g.OpenPopupStack.empty())   // Don't refocus Test Engine UI if popups are opened: this is so we can see remaining popups when implementing tests.
    if (ranTests != 0 && this.io.configTakeFocusBackAfterTests)
        uiFocus = true
    io.iniFilename = settingsIniBackup
}

fun TestEngine.clearTests() {
//    for (int n = 0; n < engine->TestsAll.Size; n++)
//    IM_DELETE(engine->TestsAll[n]);
    testsAll.clear()
    testsQueue.clear()
}

infix fun TestEngine.preNewFrame(uiCtx: Context) {

    if (uiContextTarget !== uiCtx)
        return
    assert(uiCtx === gImGui)
    val g = uiCtx

    // Inject extra time into the imgui context
    if (overrideDeltaTime >= 0f) {
        uiCtx.io.deltaTime = overrideDeltaTime
        overrideDeltaTime = -1f
    }

    // NewFrame() will increase this so we are +1 ahead at the time of calling this
    frameCount = g.frameCount + 1
    testContext?.let { testCtx ->
        val t0 = testCtx.runningTime
        val t1 = t0 + uiCtx.io.deltaTime
        testCtx.frameCount++
        testCtx.runningTime = t1
        updateWatchdog(uiCtx, t0, t1)
    }

    perfDeltaTime100 += g.io.deltaTime
    perfDeltaTime500 += g.io.deltaTime
    perfDeltaTime1000 += g.io.deltaTime
    perfDeltaTime2000 += g.io.deltaTime

    if (isRunningTests && !abort) {
        // Abort testing by holding ESC
        // When running GuiFunc only main_io == simulated_io we test for a long hold.
        val mainIo = g.io
        val simulatedIo = inputs.simulatedIO
        val keyIdxEscape = g.io.keyMap[Key.Escape]
        val useSimulatedInputs = useSimulatedInputs()

        val abort = keyIdxEscape != -1 && when {
            useSimulatedInputs -> mainIo.keysDown[keyIdxEscape] && !simulatedIo.keysDown[keyIdxEscape]
            else -> mainIo.keysDownDuration[keyIdxEscape] > 0.3f
        }
        if (abort) {
            testContext?.logWarning("KO: User aborted (pressed ESC)")
            abort()
        }
    }

    applyInputToImGuiContext()
    updateHooks()
}

// FIXME: Trying to abort a running GUI test won't kill the app immediately.
fun TestEngine.updateWatchdog(uiCtx: Context, t0: Double, t1: Double) {

    val testCtx = testContext!!

    if (!io.configRunFast || osIsDebuggerPresent())
        return

    if (testCtx.runFlags has TestRunFlag.ManualRun)
        return

    val timerWarn = if (io.configRunWithGui) 30f else 15f
    val timerKillTest = if (io.configRunWithGui) 60f else 30f
    val timerKillApp = if (io.configRunWithGui) Float.MAX_VALUE else 35f

    // Emit a warning and then fail the test after a given time.
    if (t0 < timerWarn && t1 >= timerWarn)
        testCtx.logWarning("[Watchdog] Running time for '${testCtx.test!!.name}' is >%.f seconds, may be excessive.", timerWarn)
    if (t0 < timerKillTest && t1 >= timerKillTest) {
        testCtx.logError("[Watchdog] Running time for '${testCtx.test!!.name}' is >%.f seconds, aborting.", timerKillTest)
        CHECK(false)
    }

    // Final safety watchdog in case the TestFunc is calling Yield() but never returning.
    // Note that we are not catching infinite loop cases where the TestFunc may be running but not yielding..
    if (t0 < timerKillApp + 5f && t1 >= timerKillApp + 5f) {
        testCtx.logError("[Watchdog] Emergency process exit as the test didn't return.")
        exitProcess(1)
    }
}

infix fun TestEngine.postNewFrame(uiCtx: Context) {

    if (uiContextTarget !== uiCtx)
        return
    assert(uiCtx == gImGui)

    captureContext.postNewFrame()
    captureTool.context.postNewFrame()

    // Restore host inputs
    val wantSimulatedInputs = uiContextActive != null && isRunningTests && testContext!!.runFlags hasnt TestRunFlag.GuiFuncOnly
    if (!wantSimulatedInputs) {
        val mainIo = uiCtx.io
        //IM_ASSERT(engine->UiContextActive == NULL);
        if (inputs.applyingSimulatedIO > 0) {
            // Restore
            inputs.applyingSimulatedIO--
            mainIo.mousePos put inputs.hostLastMousePos
            //main_io.WantSetMousePos = true;
        } else // Backup
            if (ImGui.isMousePosValid(mainIo.mousePos)) {
                inputs.mousePosValue put mainIo.mousePos
                inputs.hostLastMousePos put mainIo.mousePos
            }
    }

    // Garbage collect unused tasks
    val LOCATION_TASK_ELAPSE_FRAMES = 20
    infoTasks.removeAll { it.frameCount < frameCount - LOCATION_TASK_ELAPSE_FRAMES && it.result.refCount == 0 }

    // Slow down whole app
    if (toolSlowDown)
        sleepInMilliseconds(toolSlowDownMs)

    // Call user GUI function
    runGuiFunc()

    // Process on-going queues in a coroutine
    // Run the test coroutine. This will resume the test queue from either the last point the test called YieldFromCoroutine(),
    // or the loop in ImGuiTestEngine_TestQueueCoroutineMain that does so if no test is running.
    // If you want to breakpoint the point execution continues in the test code, breakpoint the exit condition in YieldFromCoroutine()
//    engine->IO.CoroutineRunFunc(engine->TestQueueCoroutine);

    // Update hooks and output flags
    updateHooks()

    // Disable vsync
    this.io.renderWantMaxSpeed = this.io.configNoThrottle
    if (this.io.configRunFast && this.io.runningTests)
        testContext?.let {
            if (it.runFlags hasnt TestRunFlag.GuiFuncOnly)
                this.io.renderWantMaxSpeed = true
        }
}

fun TestEngine.runGuiFunc() {
    val ctx = testContext
    if (ctx != null) {
        val test = ctx.test
        test?.guiFunc?.let { guiFunc ->
            test.guiFuncLastFrame = ctx.uiContext!!.frameCount
            if (ctx.runFlags hasnt TestRunFlag.GuiFuncDisable) {
                val backupActiveFunc = ctx.activeFunc
                ctx.activeFunc = TestActiveFunc.GuiFunc
                guiFunc(ctx)
                ctx.activeFunc = backupActiveFunc
            }

            // Safety net
            //if (ctx->Test->Status == ImGuiTestStatus_Error)
            ctx.recoverFromUiContextErrors()
        }
        ctx.firstGuiFrame = false
    }
}

fun TestEngine.runTest(ctx: TestContext) {

    // Clear ImGui inputs to avoid key/mouse leaks from one test to another
    clearInput()

    val test = ctx.test!!
    ctx.frameCount = 0
    ctx.setRef("")
    ctx setInputMode InputSource.Mouse
    ctx.uiContext!!.navInputSource = InputSource.NavKeyboard
    ctx.clipboard = ByteArray(0)
    ctx.genericVars.clear()
    test.testLog.clear()

    // Setup buffered clipboard
    val i = ctx.uiContext!!.io
//    typedef const char* (*ImGuiGetClipboardTextFn)(void* user_data)
//    typedef void        (*ImGuiSetClipboardTextFn)(void* user_data, const char* text)
    val backupGetClipboardTextFn = i.getClipboardTextFn
    val backupSetClipboardTextFn = i.setClipboardTextFn
    val backupClipboardUserData = i.clipboardUserData
    i.getClipboardTextFn = { userData_ ->
        val ctx_ = userData_ as TestContext
        ctx_.clipboard.cStr
    }
    i.setClipboardTextFn = { userData_, text ->
        val ctx_ = userData_ as TestContext
        ctx_.clipboard = text.toByteArray()
    }
    i.clipboardUserData = ctx

    // Mark as currently running the TestFunc (this is the only time when we are allowed to yield)
    assert(ctx.activeFunc == TestActiveFunc.None)
    val backupActiveFunc = ctx.activeFunc
    ctx.activeFunc = TestActiveFunc.TestFunc
    ctx.firstGuiFrame = test.guiFunc != null

    // Warm up GUI
    // - We need one mandatory frame running GuiFunc before running TestFunc
    // - We add a second frame, to avoid running tests while e.g. windows are typically appearing for the first time, hidden,
    // measuring their initial size. Most tests are going to be more meaningful with this stabilized base.
    if (test.flags hasnt TestFlag.NoWarmUp) {
        ctx.frameCount -= 2
        ctx.yield()
        ctx.yield()
    }
    ctx.firstTestFrameCount = ctx.frameCount

    // Call user test function (optional)
    if (ctx.runFlags has TestRunFlag.GuiFuncOnly)
    // No test function
        while (!abort && test.status == TestStatus.Running)
            ctx.yield()
    else {
        // Sanity check
        if (test.guiFunc != null)
            assert(test.guiFuncLastFrame == ctx.uiContext!!.frameCount)

        // Test function
        test.testFunc?.invoke(ctx) ?: run {
            // No test function
            if (test.flags has TestFlag.NoAutoFinish)
                while (!abort && test.status == TestStatus.Running)
                    ctx.yield()
        }

        // Recover missing End*/Pop* calls.
        ctx.recoverFromUiContextErrors()

        if (!io.configRunFast)
            ctx.sleepShort()

        while (io.configKeepGuiFunc && !abort) {
            ctx.runFlags = ctx.runFlags or TestRunFlag.GuiFuncOnly
            ctx.yield()
        }
    }

    assert(currentCaptureArgs == null) { "Active capture was not terminated in the test code." }

    // Process and display result/status
    if (test.status == TestStatus.Running)
        test.status = TestStatus.Success

    if (abort && test.status != TestStatus.Error)
        test.status = TestStatus.Unknown

    when {
        test.status == TestStatus.Success -> {
            if (ctx.runFlags hasnt TestRunFlag.NoSuccessMsg)
                ctx.logInfo("Success.")
        }
        abort -> ctx.logWarning("Aborted.")
        test.status == TestStatus.Error -> ctx.logError("${test.name} test failed.")
        else -> ctx.logWarning("Unknown status.")
    }

    // Additional yields to avoid consecutive tests who may share identifiers from missing their window/item activation.
    ctx.setGuiFuncEnabled(false)
    ctx.yield()
    ctx.yield()

    // Restore active func
    ctx.activeFunc = backupActiveFunc

    // Restore back-end clipboard functions TODO
    i.getClipboardTextFn = backupGetClipboardTextFn
    i.setClipboardTextFn = backupSetClipboardTextFn
    i.clipboardUserData = backupClipboardUserData
}

fun TestEngine.updateHooks() {

    var wantHooking = false

    //if (engine->TestContext != NULL)
    //    want_hooking = true;

    if (infoTasks.isNotEmpty())
        wantHooking = true
    if (findByLabelTask.inLabel != null)
        wantHooking = true
    if (gatherTask.inParentID != 0)
        wantHooking = true
    if (stackTool.queryStackId != 0)
        wantHooking = true

    // Update test engine specific hooks
    val uiCtx = uiContextTarget!!
    assert(uiCtx.testEngine === this)
    uiCtx.testEngineHookItems = wantHooking

    uiCtx.testEngineHookIdInfo = 0
    stackTool.queryIdInfoOutput?.let {
        uiCtx.testEngineHookIdInfo = it.id
    }
}


// Settings
//static void* ImGuiTestEngine_SettingsReadOpen(ImGuiContext*, ImGuiSettingsHandler*, const char* name);
//static void  ImGuiTestEngine_SettingsReadLine(ImGuiContext*, ImGuiSettingsHandler*, void* entry, const char* line);
//static void  ImGuiTestEngine_SettingsWriteAll(ImGuiContext* imgui_ctx, ImGuiSettingsHandler* handler, ImGuiTextBuffer* buf);
