package engine.context

import imgui.*
import imgui.ImGui.end
import imgui.ImGui.endChild
import imgui.ImGui.endGroup
import imgui.ImGui.endTabBar
import imgui.ImGui.popID
import imgui.ImGui.treePop
import IMGUI_HAS_TABLE
import engine.engine.TestRunFlag
import engine.engine.TestStatus
import engine.engine.TestVerboseLevel
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

// FIXME-ERRORHANDLING: Can't recover from inside BeginTabItem/EndTabItem yet.
// FIXME-ERRORHANDLING: Can't recover from interleaved BeginTabBar/Begin
// FIXME-ERRORHANDLING: Once this function is amazingly sturdy, we should make it a ImGui:: function.. See #1651
// FIXME-ERRORHANDLING: This is flawed as we are not necessarily End/Popping things in the right order, could we somehow store that data...
fun TestContext.recoverFromUiContextErrors() {

    val g = uiContext!!
    val test = test!!

    // If we are _already_ in a test error state, recovering is normal so we'll hide the log.
    val verbose = test.status != TestStatus.Error || engineIO!!.configVerboseLevel >= TestVerboseLevel.Debug

    while (g.currentWindowStack.size > 0) {
        if (IMGUI_HAS_TABLE) {
//                while (g.CurrentTable && (g.CurrentTable->OuterWindow == g.CurrentWindow || g.CurrentTable->InnerWindow == g.CurrentWindow))
//                {
//                    if (verbose) LogWarning("Recovered from missing EndTable() call.")
//                    ImGui::EndTable()
//                }
        }

        while (g.currentTabBar != null) {
            if (verbose) logWarning("Recovered from missing EndTabBar() call.")
            endTabBar()
        }

        val win = g.currentWindow!!

        while (win.dc.treeDepth > 0) {
            if (verbose) logWarning("Recovered from missing TreePop() call.")
            treePop()
        }

        TODO("resync")
        // FIXME: StackSizesBackup[] indices..
//        while (win.dc.groupStack.size > win.dc.stackSizesBackup[1]) {
//            if (verbose) logWarning("Recovered from missing EndGroup() call.")
//            endGroup()
//        }
//
//        while (win.idStack.size > win.dc.stackSizesBackup[0]) {
//            if (verbose) logWarning("Recovered from missing PopID() call.")
//            popID()
//        }
//
//        while (g.colorModifiers.size > g.currentWindow!!.dc.stackSizesBackup[3]) {
//            if (verbose) logWarning("Recovered from missing PopStyleColor() for '${g.colorModifiers.last().col}'")
//            ImGui.popStyleColor()
//        }
//        while (g.styleModifiers.size > g.currentWindow!!.dc.stackSizesBackup[4]) {
//            if (verbose) logWarning("Recovered from missing PopStyleVar().")
//            ImGui.popStyleVar()
//        }

        if (g.currentWindowStack.size == 1) {
            assert(g.currentWindow!!.isFallbackWindow)
            break
        }

        if (win.flags has Wf._ChildWindow) {
            if (verbose) logWarning("Recovered from missing EndChild() call.")
            endChild()
        } else {
            if (verbose) logWarning("Recovered from missing End() call.")
            end()
        }
    }
}

internal inline fun <reified T> TestContext.getUserData(): T {
    assert(userData != null)
    return userData as T
} // FIXME: Assert to compare sizes