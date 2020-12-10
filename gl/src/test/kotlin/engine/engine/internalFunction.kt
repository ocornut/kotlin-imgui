package engine.engine

import IMGUI_TEST_ENGINE_DEBUG
import engine.CaptureArgs
import engine.TestEngine
import engine.TestInfoTask
import engine.context.TestActiveFunc
import engine.context.recoverFromUiContextErrors
import imgui.ID
import imgui.IMGUI_DEBUG_TEST_ENGINE
import imgui.toByteArray


//-------------------------------------------------------------------------
// Internal function
//-------------------------------------------------------------------------


// Request information about one item.
// Will push a request for the test engine to process.
// Will return NULL when results are not ready (or not available).
fun TestEngine.findItemInfo(id: ID, debugId: String?): TestItemInfo? {

    assert(id != 0)

    findLocateTask(id)?.let { task ->
        if (task.result.timestampMain + 2 >= frameCount) {
            task.frameCount = frameCount // Renew task
            return task.result
        }
        return null
    }

    // Create task
    val task = TestInfoTask(id, frameCount)
    if (IMGUI_TEST_ENGINE_DEBUG)
        debugId?.let {
            val debugIdSz = debugId.length
            if (debugIdSz < task.debugName.size)
                debugId.toByteArray(task.debugName)
            else {
                val headerSz = task.debugName.size * 0.3f
                val footerSz = task.debugName.size - 2 - headerSz
                assert(headerSz > 0 && footerSz > 0)
                TODO()
//                formatString(task.debugName, "%.*s..%.*s", (int)header_sz, debug_id, (int)footer_sz, debug_id+debug_id_sz-footer_sz)
            }
        }
    infoTasks += task

    return null
}

// FIXME-OPT
infix fun TestEngine.findLocateTask(id: ID): TestInfoTask? = infoTasks.find { it.id == id }

infix fun TestEngine.pushInput(input: TestInput) {
    inputs.queue += input
}

// Yield control back from the TestFunc to the main update + GuiFunc, for one frame.
fun TestEngine.yield() {
    val ctx = testContext

    // Can only yield in the test func!
    if (ctx != null) {
        assert(ctx.activeFunc == TestActiveFunc.TestFunc) { "Can only yield inside TestFunc()!" }

//        engine->IO.YieldFromCoroutine();

        ctx.test?.guiFunc?.let { f ->
            // Call user GUI function
            if (ctx.runFlags hasnt TestRunFlag.GuiFuncDisable) {
                val backupActiveFunc = ctx.activeFunc
                ctx.activeFunc = TestActiveFunc.GuiFunc
                f(ctx)
                ctx.activeFunc = backupActiveFunc
            }

            // Safety net
            //if (ctx->Test->Status == ImGuiTestStatus_Error)
            ctx.recoverFromUiContextErrors()
        }
    }
}

infix fun TestEngine.setDeltaTime(deltaTime: Float) {

    assert(deltaTime >= 0f)
    overrideDeltaTime = deltaTime
}

// ImGuiTestEngine_GetFrameCount -> val

val TestEngine.perfDeltaTime500Average
    get() = perfDeltaTime500.average

//const char*         ImGuiTestEngine_GetVerboseLevelName(ImGuiTestVerboseLevel v)

infix fun TestEngine.captureScreenshot(args: CaptureArgs): Boolean {

    if (io.screenCaptureFunc == null) {
        assert(false)
        return false
    }

    assert(currentCaptureArgs == null) { "Nested captures are not supported." }

    // Graphics API must render a window so it can be captured
    // FIXME: This should work without this, as long as Present vs Vsync are separated (we need a Present, we don't need Vsync)
    val backupFast = io.configRunFast
    io.configRunFast = false

    // Because we rely on window->ContentSize for stitching, let 1 extra frame elapse to make sure any
    // windows which contents have changed in the last frame get a correct window->ContentSize value.
    yield()

    currentCaptureArgs = args
    while (currentCaptureArgs != null)
        yield()

    io.configRunFast = backupFast
    return true
}