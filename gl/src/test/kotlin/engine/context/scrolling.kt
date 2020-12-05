package engine.context

import engine.core.ERRORF
import engine.core.TestRef
import engine.core.TestRefDesc
import imgui.clamp
import imgui.internal.classes.Window
import imgui.internal.floor
import imgui.internal.linearSweep
import io.kotest.matchers.shouldBe
import kotlin.math.abs


fun TestContext.scrollToTop() {
    if (isError)
        return
    val window = getWindowByRef("")
    if (window != null)
        window setScrollY 0f
    else
        logError("ScrollToTop: failed to get window")
    yield()
    yield()
}

fun TestContext.scrollToBottom() {
    if (isError)
        return
    val window = getWindowByRef("")
    if (window != null)
        window setScrollY window.scrollMax.y
    else
        logError("ScrollToBottom: failed to get window")
    yield()
    yield()
}

// [JVM]
fun TestContext.scrollToItemY(ref: String, scrollRatioY: Float = 0.5f) = scrollToItemY(TestRef(path = ref))
fun TestContext.scrollToItemY(ref: TestRef, scrollRatioY: Float = 0.5f) {

//    IM_UNUSED(scroll_ratio_y);

    if (isError) return

    // If the item is not currently visible, scroll to get it in the center of our window
    REGISTER_DEPTH {
        val g = uiContext!!
        val item = itemInfo(ref)
        val desc = TestRefDesc(ref, item)
        logDebug("ScrollToItemY $desc")

        if (item == null) return
        val window = item.window!!

        //if (item->ID == 0xDFFBB0CE || item->ID == 0x87CBBA09)
        //    printf("[%03d] scroll_max_y %f\n", FrameCount, ImGui::GetWindowScrollMaxY(window));

        var failures = 0
        while (!abort) {
            // result->Rect fields will be updated after each iteration.
            val itemCurrY = floor(item.rectFull.center.y)
            val itemTargetY = floor(window.innerClipRect.center.y)
            val scrollDeltaY = itemTargetY - itemCurrY
            val scrollTargetY = clamp(window.scroll.y - scrollDeltaY, 0f, window.scrollMax.y)

            if (abs(window.scroll.y - scrollTargetY) < 1f)
                break

            // FIXME-TESTS: Scroll snap on edge can make this function loops forever.
            // [20191014: Repro is to resize e.g. widgets_checkbox_001 window to be small vertically]

            // FIXME-TESTS: There's a bug which can be repro by moving #RESIZE grips to Layer 0, making window small and trying to resize a dock node host.
            // Somehow SizeContents.y keeps increase and we never reach our desired (but faulty) scroll target.
            val scrollSpeed = if (engineIO!!.configRunFast) Float.MAX_VALUE else floor(engineIO!!.scrollSpeed * g.io.deltaTime + 0.99f)
            val scrollY = linearSweep(window.scroll.y, scrollTargetY, scrollSpeed)
            //printf("[%03d] window->Scroll.y %f + %f\n", FrameCount, window->Scroll.y, scroll_speed);
            //window->Scroll.y = scroll_y;
            window setScrollY scrollY

            yield()

            // Error handling to avoid getting stuck in this function.
            if (abs(window.scroll.y - scrollY) >= 1f) {
                if (++failures < 3)
                    logWarning("ScrollToItemY: failed to set scrolling. Requested %.2f, got %.2f. Will try again.", scrollY, window.scroll.y)
                else {
                    ERRORF("ScrollToItemY: failed to set scrolling. Requested %.2f, got %.2f. Aborting.", scrollY, window.scroll.y)
                    break
                }
            }

            windowBringToFront(window)
        }

        // Need another frame for the result->Rect to stabilize
        yield()
    }
}

// Verify that ScrollMax is stable regardless of scrolling position
// - This can break when the layout of clipped items doesn't match layout of unclipped items
// - This can break with non-rounded calls to ItemSize(), namely when the starting position is negative (above visible area)
//   We should ideally be more tolerant of non-rounded sizes passed by the users.
// - One of the net visible effect of an unstable ScrollMax is that the End key would put you at a spot that's not exactly the lowest spot,
//   and so a second press to End would you move again by a few pixels.
infix fun TestContext.scrollVerifyScrollMax(window: Window) {

    window setScrollY 0f
    yield()
    val scrollMax0 = window.scrollMax.y
    window setScrollY window.scrollMax.y
    yield()
    val scrollMax1 = window.scrollMax.y
    scrollMax0 shouldBe scrollMax1
}