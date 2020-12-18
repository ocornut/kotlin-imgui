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
import imgui.internal.linearSweep
import imgui.internal.saturate
import imgui.internal.sections.Axis
import imgui.internal.sections.get
import imgui.internal.sections.set
import io.kotest.matchers.shouldBe
import kotlin.math.abs


// FIXME-TESTS: Mostly the same code as ScrollbarEx()
fun Window.getScrollbarMousePositionForScroll(axis: Axis, scrollV: Float): Vec2 {

    val bb = getScrollbarRect(axis)

    // From Scrollbar():
    //float* scroll_v = &window->Scroll[axis];
    val sizeAvailV = innerRect.max[axis] - innerRect.min[axis]
    val sizeContentsV = contentSize[axis] + windowPadding[axis] * 2f

    // From ScrollbarEx() onward:

    // V denote the main, longer axis of the scrollbar (= height for a vertical scrollbar)
    val scrollbarSizeV = bb.max[axis] - bb.min[axis]

    // Calculate the height of our grabbable box. It generally represent the amount visible (vs the total scrollable amount)
    // But we maintain a minimum size in pixel to allow for the user to still aim inside.
    val winSizeV = (sizeContentsV max sizeAvailV) max 1f
    val grabHPixels = clamp(scrollbarSizeV * (sizeAvailV / winSizeV), g.style.grabMinSize, scrollbarSizeV)

    val scrollMax1 = 1f max (sizeContentsV - sizeAvailV)
    val scrollRatio = saturate(scrollV / scrollMax1)
    val grabV = scrollRatio * (scrollbarSizeV - grabHPixels)   // Grab position

    val position = Vec2()
    position[axis] = bb.min[axis] + grabV + grabHPixels * 0.5f
    position[axis xor 1] = bb.center[axis xor 1]

    return position
}

fun TestContext.scrollTo(window: Window, axis: Axis, scrollTarget: Float) {

    val g = uiContext!!
    if (isError)
        return

    // Early out
    val scrollTargetClamp = clamp(scrollTarget, 0f, window.scrollMax[axis])
    if (abs(window.scroll[axis] - scrollTargetClamp) < 1f)
        return

    REGISTER_DEPTH {

        logDebug("ScrollTo $axis %.1f/%.1f", scrollTarget, window.scrollMax[axis])

        windowBringToFront(window)
//        yield()

        // Try to use Scrollbar if available
        val scrollbarItem = itemInfo(window getScrollbarID axis, TestOpFlag.NoError.i)
        if (scrollbarItem != null && !engineIO!!.configRunFast) {
            val scrollbarRect = window.getScrollbarRect(axis)
            val scrollbarSizeV = scrollbarRect.max[axis] - scrollbarRect.min[axis]
            val windowResizeGripSize = floor((g.fontSize * 1.35f) max (window.windowRounding + 1f + g.fontSize * 0.2f))

            // In case of a very small window, directly use SetScrollX/Y function to prevent resizing it
            // FIXME-TESTS: GetWindowScrollbarMousePositionForScroll doesn't return the exact value when scrollbar grip is too small
            if (scrollbarSizeV >= windowResizeGripSize) {
                val scrollSrc = window.scroll[axis]
                val scrollbarSrcPos = window.getScrollbarMousePositionForScroll(axis, scrollSrc)
                scrollbarSrcPos[axis] = scrollbarSrcPos[axis] min (scrollbarRect.min[axis] + scrollbarSizeV - windowResizeGripSize)
                mouseMoveToPos(scrollbarSrcPos)
                mouseDown(0)
                sleepShort()

                val scrollbarDstPos = window.getScrollbarMousePositionForScroll(axis, scrollTargetClamp)
                mouseMoveToPos(scrollbarDstPos)
                mouseUp(0)
                sleepShort()

                // Verify that things worked
                val scrollResult = window.scroll[axis]
                if (abs(scrollResult - scrollTargetClamp) < 1f)
                    return

                // FIXME-TESTS: Investigate
                logWarning("Failed to set Scroll$axis. Requested %.2f, got %.2f.", scrollTargetClamp, scrollResult)
            }
        }

        // Fallback: manual slow scroll
        // FIXME-TESTS: Consider using mouse wheel
        val remainingFailures = intArrayOf(3)
        while (!abort) {
            if (abs(window.scroll[axis] - scrollTargetClamp) < 1f)
                break

            val scrollSpeed = if (engineIO!!.configRunFast) Float.MAX_VALUE else floor(engineIO!!.scrollSpeed * g.io.deltaTime + 0.99f)
            val scrollNext = linearSweep(window.scroll[axis], scrollTarget, scrollSpeed)
            if (axis == Axis.X)
                window setScrollX scrollNext
            else
                window setScrollY scrollNext

            // Error handling to avoid getting stuck in this function.
            yield()

            if (!scrollErrorCheck(axis, scrollNext, window.scroll[axis], remainingFailures))
                break
        }

        // Need another frame for the result->Rect to stabilize
        yield()
    }
}

infix fun TestContext.scrollToX(scrollX: Float) = scrollTo(getWindowByRef("")!!, Axis.X, scrollX)
infix fun TestContext.scrollToY(scrollY: Float) = scrollTo(getWindowByRef("")!!, Axis.Y, scrollY)

fun TestContext.scrollToTop() {

    if (isError)
        return

    val window = getWindowByRef("")
    check(window != null)
    if (window.scroll.y == 0f)
        return
    scrollToY(0f)
    yield()
}

fun TestContext.scrollToBottom() {

    if (isError)
        return

    val window = getWindowByRef("")
    check(window != null)
    if (window.scroll.y == window.scrollMax.y)
        return
    scrollToY(window.scrollMax.y)
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

        if (item == null)
            return

        // Ensure window size and ScrollMax are up-to-date
        yield()

        val window = item.window!!
        val itemCurrY = floor(item.rectFull.center.y)
        val itemTargetY = floor(window.innerClipRect.center.y)
        val scrollDeltaY = itemTargetY - itemCurrY
        val scrollTargetY = clamp(window.scroll.y - scrollDeltaY, 0f, window.scrollMax.y)

        scrollTo(window, Axis.Y, scrollTargetY)
    }
}

infix fun TestContext.scrollToItemX(ref: TestRef) {

    val g = uiContext!!
    if (isError)
        return

    // If the item is not currently visible, scroll to get it in the center of our window
    REGISTER_DEPTH {
        val item = itemInfo(ref)
        val desc = TestRefDesc(ref, item)
        logDebug("ScrollToItemX $desc")
        if (item == null)
            return

        // Ensure window size and ScrollMax are up-to-date
        yield()

        // TabBar are a special case because they have no scrollbar and rely on ScrollButton "<" and ">"
        // FIXME-TESTS: Consider moving to its own function.
        val tabBar = g.tabBars[item.parentID]
        if (tabBar != null) {
            // Cancel if "##v", because it's outside the tab_bar rect, and will be considered as "not visible" even if it is!
//            if (getID("##v") == item.id)
//                return

            val selectedTabItem = tabBar findTabByID tabBar.selectedTabId
            val targetTabItem = tabBar findTabByID item.id ?: return

            val selectedTabIndex = tabBar.tabs.indexOf(selectedTabItem)
            val targetTabIndex = tabBar.tabs.indexOf(targetTabItem)

            val backupRef = this.ref
            //SetRef(tab_bar->ID);
            setRef(item.parentID)

            if (selectedTabIndex > targetTabIndex) {
                mouseMove("##<")
                for (i in 0 until selectedTabIndex - targetTabIndex)
                    mouseClick(0)
            } else {
                mouseMove("##>")
                for (i in 0 until targetTabIndex - selectedTabIndex)
                    mouseClick(0)
            }

            // Wait for the scroll animation to proceed.
            // We're "loosing" some frames but there is no easy way to force a "tab visibility teleportation"
            if (engineIO!!.configRunFast)
                while (tabBar.scrollingAnim != tabBar.scrollingTarget)
                    yield()

            setRef(backupRef)
        } else {
            val window = item.window!!
            val itemCurrX = floor(item.rectFull.center.x)
            val itemTargetX = floor(window.innerClipRect.center.x)
            val scrollDeltaX = itemTargetX - itemCurrX
            val scrollTargetX = clamp(window.scroll.x - scrollDeltaX, 0f, window.scrollMax.x)

            scrollTo(window, Axis.X, scrollTargetX)
        }
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