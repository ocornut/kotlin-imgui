package engine.context

import engine.engine.ERRORF
import engine.engine.TestOpFlag
import glm_.max
import glm_.min
import glm_.vec2.Vec2
import imgui.api.g
import imgui.clamp
import imgui.internal.classes.Window
import imgui.internal.floor
import imgui.internal.saturate
import imgui.internal.sections.Axis
import imgui.internal.sections.get
import imgui.internal.sections.set
import io.kotest.matchers.shouldBe
import kotlin.math.abs


fun Window.getScrollbarMousePositionForScroll(axis: Axis, scrollV: Float): Vec2 {
    // Mostly the same code as ScrollbarEx

    val bb = getScrollbarRect(axis)

    //float* scroll_v = &window->Scroll[axis];
    val sizeAvailV = innerRect.max[axis] - innerRect.min[axis]
    val sizeContentsV = contentSize[axis] + windowPadding[axis] * 2f

    // V denote the main, longer axis of the scrollbar (= height for a vertical scrollbar)
    val scrollbarSizeV = bb.max[axis] - bb.min[axis]

    // Calculate the height of our grabbable box. It generally represent the amount visible (vs the total scrollable amount)
    // But we maintain a minimum size in pixel to allow for the user to still aim inside.
    val winSizeV = (sizeContentsV max sizeAvailV) max 1f
    val grabHPixels = clamp(scrollbarSizeV * (sizeAvailV / winSizeV), g.style.grabMinSize, scrollbarSizeV)
    val grabHNorm = grabHPixels / scrollbarSizeV

    val scrollMax1 = 1f max (sizeContentsV - sizeAvailV)
    val scrollRatio = saturate(scrollV / scrollMax1)
    val grabV = scrollRatio * (scrollbarSizeV - grabHPixels)   // Grab position

    val position = if (axis == Axis.X) Vec2(bb.min.x, bb.center.y) else Vec2(bb.center.x, bb.min.y)
    position[axis] += grabV + grabHPixels * 0.5f
    return position
}

fun TestContext.scrollTo(axis: Axis, scrollTarget: Float) {

    val g = uiContext!!
    if (isError)
        return

    REGISTER_DEPTH {
        val window = getWindowByRef("")
        if (window == null) {
            logError("ScrollTo$axis: failed to get window")
            return
        }

        logDebug("ScrollTo$axis %.1f/%.1f", scrollTarget, window.scrollMax[axis])
        windowBringToFront(window)

        yield()

        val scrollbarRef = "#SCROLL$axis"
        val scrollbar_item = itemInfo(scrollbarRef, TestOpFlag.NoError.i) ?: return

        val scrollbarRect = window.getScrollbarRect(axis)
        val scrollbarSizeV = scrollbarRect.max[axis] - scrollbarRect.min[axis]
        val windowResizeGripSize = floor((g.fontSize * 1.35f) max (window.windowRounding + 1f + g.fontSize * 0.2f))
        val scrollTargetClamp = 0f max (scrollTarget min window.scrollMax[axis])

        // In case of a very small window, directly use SetScroll.. function to prevent resizing it
        val useSetScrollFunction = scrollbarSizeV < windowResizeGripSize
        if (!useSetScrollFunction) {
            // Make sure we don't hover the window resize grip
            val scrollbarSrcPos = window.getScrollbarMousePositionForScroll(axis, window.scroll[axis])
            scrollbarSrcPos[axis] = scrollbarSrcPos[axis] min (scrollbarRect.min[axis] + scrollbarSizeV - windowResizeGripSize)
            mouseMoveToPos(scrollbarSrcPos)

            if (!useSetScrollFunction) {
                val scrollbarDstPos = window.getScrollbarMousePositionForScroll(axis, scrollTargetClamp)
                mouseDown(0)
                mouseMoveToPos(scrollbarDstPos)
                mouseUp(0)
            }
        }

        // FIXME: GetWindowScrollbarMousePositionForScroll doesn't return the exact value when scrollbar grip is too small
        if (useSetScrollFunction || window.scroll[axis] != scrollTargetClamp) {
            if (axis == Axis.X)
                window setScrollX scrollTargetClamp
            else
                window setScrollY scrollTarget
            yield()
        }
    }
}

infix fun TestContext.scrollToX(scrollX: Float) = scrollTo(Axis.X, scrollX)
infix fun TestContext.scrollToY(scrollY: Float) = scrollTo(Axis.Y, scrollY)

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
fun TestContext.scrollToItemY(ref: String, scrollRatioY: Float = 0.5f) = scrollToItemY(TestRef(ref))
fun TestContext.scrollToItemY(ref: TestRef, scrollRatioY: Float = 0.5f) {

//    IM_UNUSED(scroll_ratio_y);

    if (isError) return

    // If the item is not currently visible, scroll to get it in the center of our window
    REGISTER_DEPTH {
        val item = itemInfo(ref)
        val desc = TestRefDesc(ref, item)
        logDebug("ScrollToItemY $desc")

        if (item == null) return
        val window = item.window!!

        // Ensure window size is up-to-date
        yield();

        val itemCurrY = floor(item.rectFull.center.y)
        val itemTargetY = floor(window.innerClipRect.center.y)
        val scrollDeltaY = itemTargetY - itemCurrY
        val scrollTargetY = clamp(window.scroll.y - scrollDeltaY, 0f, window.scrollMax.y)
        if (abs(window.scroll.y - scrollTargetY) < 1f)
            return

        scrollTo(Axis.Y, scrollTargetY)
    }
}

fun TestContext.scrollErrorCheck(axis: Axis, expected: Float, actual: Float, remainingAttempts: IntArray): Boolean {

    if (isError)
        return false

    val THRESHOLD = 1f
    if (abs(actual - expected) <= THRESHOLD)
        return true

    remainingAttempts[0]--
    if (remainingAttempts[0] > 0) {
        logWarning("Failed to set Scroll$axis. Requested %.2f, got %.2f. Will try again.", expected, actual)
        return true
    } else {
        ERRORF("Failed to set Scroll$axis. Requested %.2f, got %.2f. Aborting.", expected, actual)
        return false
    }
}

// Verify that ScrollMax is stable regardless of scrolling position
// - This can break when the layout of clipped items doesn't match layout of unclipped items
// - This can break with non-rounded calls to ItemSize(), namely when the starting position is negative (above visible area)
//   We should ideally be more tolerant of non-rounded sizes passed by the users.
// - One of the net visible effect of an unstable ScrollMax is that the End key would put you at a spot that's not exactly the lowest spot,
//   and so a second press to End would you move again by a few pixels.
// FIXME-TESTS: Make this an iterative, smooth scroll.
infix fun TestContext.scrollVerifyScrollMax(window: Window) {

    window setScrollY 0f
    yield()
    val scrollMax0 = window.scrollMax.y
    window setScrollY window.scrollMax.y
    yield()
    val scrollMax1 = window.scrollMax.y
    scrollMax0 shouldBe scrollMax1
}