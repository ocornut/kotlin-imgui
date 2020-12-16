package engine.context

import imgui.*
import imgui.ImGui.end
import imgui.ImGui.endChild
import imgui.ImGui.endGroup
import imgui.ImGui.endTabBar
import imgui.ImGui.popID
import imgui.ImGui.treePop
import IMGUI_HAS_TABLE
import engine.engine.*
import imgui.WindowFlag as Wf

// Main control
fun TestContext.finish() {
    if (runFlags has TestRunFlag.GuiFuncOnly.i)
        return
    val test = this.test!!
    if (test.status == TestStatus.Running)
        test.status = TestStatus.Success
}

val TestContext.isError: Boolean
    get() = test!!.status == TestStatus.Error || abort

val TestContext.isFirstGuiFrame: Boolean
    get () = firstGuiFrame

val TestContext.isFirstTestFrame: Boolean
    get () = frameCount == firstTestFrameCount   // First frame where TestFunc is running (after warm-up frame).

fun TestContext.setGuiFuncEnabled(v: Boolean) {
    runFlags = when {
        v -> runFlags wo TestRunFlag.GuiFuncDisable
        else -> runFlags or TestRunFlag.GuiFuncDisable
    }
}

fun logWarningFunc(userData: Any?, fmt: String) {
    val ctx = userData as TestContext
    ctx.logEx(TestVerboseLevel.Warning, TestLogFlag.None.i, fmt)
}

fun TestContext.recoverFromUiContextErrors() {

    val test = test!!

    // If we are _already_ in a test error state, recovering is normal so we'll hide the log.
    val verbose = test.status != TestStatus.Error || engineIO!!.configVerboseLevel >= TestVerboseLevel.Debug

    if (verbose)
        ImGui.errorCheckEndFrameRecover(::logWarningFunc, this)
    else
        ImGui.errorCheckEndFrameRecover(null, null)
}

internal inline fun <reified T> TestContext.getUserData(): T {
    assert(userData != null)
    return userData as T
} // FIXME: Assert to compare sizes