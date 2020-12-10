package engine.context

import engine.engine.*
import glm_.glm
import glm_.vec2.Vec2
import glm_.wo
import imgui.ID
import imgui.clamp
import imgui.hasnt
import imgui.internal.bezierCalc
import imgui.internal.classes.Rect
import imgui.internal.lengthSqr
import imgui.internal.sections.NavLayer
import io.kotest.matchers.shouldBe
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import imgui.WindowFlag as Wf


fun getMouseAimingPos(item: TestItemInfo, flags: TestOpFlags): Vec2 {
    val r = item.rectClipped
    val pos = Vec2()
    //pos = r.GetCenter();
    pos.x = when {
        flags has TestOpFlag.MoveToEdgeL -> r.min.x + 1f
        flags has TestOpFlag.MoveToEdgeR -> r.max.x - 1f
        else -> (r.min.x + r.max.x) * 0.5f
    }
    pos.y = when {
        flags has TestOpFlag.MoveToEdgeU -> r.min.y + 1f
        flags has TestOpFlag.MoveToEdgeD -> r.max.y - 1f
        else -> (r.min.y + r.max.y) * 0.5f
    }
    return pos
}

// [JVM]
fun TestContext.mouseMove(ref: String, flags: TestOpFlags = TestOpFlag.None.i) = mouseMove(TestRef(path = ref), flags)

// [JVM]
fun TestContext.mouseMove(ref: ID, flags: TestOpFlags = TestOpFlag.None.i) = mouseMove(TestRef(ref), flags)

// FIXME-TESTS: This is too eagerly trying to scroll everything even if already visible.
// FIXME: Maybe ImGuiTestOpFlags_NoCheckHoveredId could be automatic if we detect that another item is active as intended?
fun TestContext.mouseMove(ref: TestRef, flags: TestOpFlags = TestOpFlag.None.i) {

    if (isError) return

    REGISTER_DEPTH {
        val g = uiContext!!
        val item = itemInfo(ref)
        val desc = TestRefDesc(ref, item)
        logDebug("MouseMove to $desc")

        if (item == null)
            return
        item.refCount++

        // Focus window before scrolling/moving so things are nicely visible
        if (flags hasnt TestOpFlag.NoFocusWindow)
            windowBringToFront(item.window)

        val window = item.window!!
        val windowInnerRPadded = Rect(window.innerClipRect)
        windowInnerRPadded expand -4f // == WINDOWS_RESIZE_FROM_EDGES_HALF_THICKNESS
        if (item.navLayer == NavLayer.Main && item.rectClipped !in windowInnerRPadded)
            scrollToItemY(ref)

        val pos = item.rectFull.center
        windowTeleportToMakePosVisibleInViewport(window, pos)

        // Move toward an actually visible point
        pos put getMouseAimingPos(item, flags)
        mouseMoveToPos(pos)

        // Focus again in case something made us lost focus (which could happen on a simple hover)
        if (flags hasnt TestOpFlag.NoFocusWindow)
            windowBringToFront(window)// , ImGuiTestOpFlags_Verbose);

        if (!abort && flags hasnt TestOpFlag.NoCheckHoveredId) {
            val hoveredId = g.hoveredIdPreviousFrame
            if (hoveredId != item.id) {
                if (window.flags hasnt Wf.NoResize && flags hasnt TestOpFlag.IsSecondAttempt) {
                    var isResizeCorner = false
                    for (n in 0..1)
                        isResizeCorner = isResizeCorner || (hoveredId == window getResizeID n)
                    if (isResizeCorner) {
                        logDebug("Obstructed by ResizeGrip, trying to resize window and trying again..")
                        val extraSize = window.calcFontSize() * 3f
                        windowResize(window.id, window.size + Vec2(extraSize))
                        mouseMove(ref, flags or TestOpFlag.IsSecondAttempt)
                        item.refCount--
                        return
                    }
                }

                ERRORF_NOHDR("""
                    Unable to Hover $desc:
                    - Expected item %08X in window '${item.window?.name ?: "<NULL>"}', targeted position: (%.1f,%.1f)'\n"
                    - Hovered id was %08X in '${g.hoveredWindow?.name ?: ""}'.""".trimIndent(),
                        item.id, pos.x, pos.y, hoveredId)
            }
        }

        item.refCount--
    }
}

fun TestContext.mouseMoveToPos(target: Vec2) {

    val g = uiContext!!
    if (isError) return

    REGISTER_DEPTH {
        logDebug("MouseMoveToPos from (%.0f,%.0f) to (%.0f,%.0f)", inputs!!.mousePosValue.x, inputs!!.mousePosValue.y, target.x, target.y)

        if (engineIO!!.configRunFast) {
            inputs!!.mousePosValue put target
            engine!!.yield()
            engine!!.yield()
            return
        }

        // Simulate slower movements. We use a slightly curved movement to make the movement look less robotic.

        // Calculate some basic parameters
        val startPos = inputs!!.mousePosValue
        val delta = target - startPos
        val length2 = delta.lengthSqr
        val length = if (length2 > 0.0001f) sqrt(length2) else 1f
        val invLength = 1f / length

        // Calculate a vector perpendicular to the motion delta
        val perp = Vec2(delta.y, -delta.x) * invLength

        // Calculate how much wobble we want, clamped to max out when the delta is 100 pixels (shorter movements get less wobble)
        val positionOffsetMagnitude = clamp(length, 1f, 100f) * engineIO!!.mouseWobble

        // Wobble positions, using a sine wave based on position as a cheap way to get a deterministic offset
        val intermediatePosA = startPos + (delta * 0.3f)
        val intermediatePosB = startPos + (delta * 0.6f)
        intermediatePosA += perp * sin(intermediatePosA.y * 0.1f) * positionOffsetMagnitude
        intermediatePosB += perp * cos(intermediatePosB.y * 0.1f) * positionOffsetMagnitude

        // We manipulate Inputs->MousePosValue without reading back from g.IO.MousePos because the later is rounded.
        // To handle high framerate it is easier to bypass this rounding.
        var currentDist = 0f // Our current distance along the line (in pixels)
        while (true) {
            val moveSpeed = engineIO!!.mouseSpeed * g.io.deltaTime

            //if (g.IO.KeyShift)
            //    move_speed *= 0.1f;

            currentDist += moveSpeed // Move along the line

            // Calculate a parametric position on the direct line that we will use for the curve
            var t = currentDist * invLength
            t = clamp(t, 0f, 1f)
            t = 1f - (cos(t * glm.πf) + 1f) * 0.5f // Generate a smooth curve with acceleration/deceleration

            //ImGui::GetOverlayDrawList()->AddCircle(target, 10.0f, IM_COL32(255, 255, 0, 255));

            if (t >= 1f) {
                inputs!!.mousePosValue put target
                engine!!.yield()
                engine!!.yield()
                return
            } else {
                // Use a bezier curve through the wobble points
                inputs!!.mousePosValue put bezierCalc(startPos, intermediatePosA, intermediatePosB, target, t)
                //ImGui::GetOverlayDrawList()->AddBezierCurve(start_pos, intermediate_pos_a, intermediate_pos_b, target, IM_COL32(255,0,0,255), 1.0f);
                engine!!.yield()
            }
        }
    }
}

/** This always teleport the mouse regardless of fast/slow mode. Useful e.g. to set initial mouse position for a GIF recording. */
infix fun TestContext.mouseTeleportToPos(target: Vec2) {
    if (isError)
        return

    REGISTER_DEPTH {
        logDebug("MouseTeleportToPos from (%.0f,%.0f) to (%.0f,%.0f)", inputs!!.mousePosValue.x, inputs!!.mousePosValue.y, target.x, target.y)

        inputs!!.mousePosValue put target
        yield()
        yield()
    }
}


// TODO: click time argument (seconds and/or frames)
fun TestContext.mouseClick(button: Int = 0) {

    if (isError) return

    REGISTER_DEPTH {
        logDebug("MouseClick $button")

        // Make sure mouse buttons are released
        assert(inputs!!.mouseButtonsValue == 0)

        yield()

        // Press
        uiContext!!.io.mouseClickedTime[button] = -Double.MAX_VALUE // Prevent accidental double-click from happening ever
        inputs!!.mouseButtonsValue = 1 shl button
        yield()
        inputs!!.mouseButtonsValue = 0

        yield() // Let the imgui frame finish, start a new frame.
        // Now NewFrame() has seen the mouse release.
        yield() // Let the imgui frame finish, now e.g. Button() function will return true. Start a new frame.
        // At this point, we are in a new frame but our windows haven't been Begin()-ed into, so anything processed by Begin() is not valid yet.
    }
}

// TODO: click time argument (seconds and/or frames)
fun TestContext.mouseDoubleClick(button: Int = 0) {

    if (isError) return

    REGISTER_DEPTH {
        logDebug("MouseDoubleClick $button")

        yield()
        uiContext!!.io.mouseClickedTime[button] = -Double.MAX_VALUE // Prevent accidental double-click followed by single click
        for (n in 0..1) {
            inputs!!.mouseButtonsValue = 1 shl button
            yield()
            inputs!!.mouseButtonsValue = 0
            yield()
        }
        yield() // Give a frame for items to react
    }
}

fun TestContext.mouseDown(button: Int = 0) {

    if (isError) return

    REGISTER_DEPTH {
        logDebug("MouseDown $button")

        uiContext!!.io.mouseClickedTime[button] = -Double.MAX_VALUE // Prevent accidental double-click from happening ever
        inputs!!.mouseButtonsValue = 1 shl button
        yield()
    }
}

fun TestContext.mouseUp(button: Int = 0) {

    if (isError) return

    REGISTER_DEPTH {
        logDebug("MouseUp $button")

        inputs!!.mouseButtonsValue = inputs!!.mouseButtonsValue wo (1 shl button)
        yield()
    }
}


fun TestContext.mouseLiftDragThreshold(button: Int = 0) {

    if (isError) return

    uiContext!!.io.apply {
        mouseDragMaxDistanceAbs[button] put mouseDragThreshold
        mouseDragMaxDistanceSqr[button] = mouseDragThreshold * mouseDragThreshold * 2
    }
}

fun TestContext.mouseClickOnVoid(mouseButton: Int = 0) {

    val g = uiContext!!
    if (isError)
        return

    REGISTER_DEPTH {
        logDebug("MouseClickOnVoid $mouseButton")

        // FIXME-TESTS: Would be nice if we could find a suitable position (e.g. by sampling points in a grid)
        val voidPos = mainViewportPos + 1
        val windowMinPos = voidPos + g.style.touchExtraPadding + 4f + 1f // FIXME: Should use WINDOWS_RESIZE_FROM_EDGES_HALF_THICKNESS

        for (window in g.windows)
            if (window.rootWindow === window && window.wasActive)
                if (windowMinPos in window.rect())
                    windowMove(window.name, windowMinPos)

        // Click top-left corner which now is empty space.
        mouseMoveToPos(voidPos)
        g.hoveredWindow shouldBe null

        // Clicking empty space should clear navigation focus.
        mouseClick(mouseButton)
        //IM_CHECK(g.NavId == 0); // FIXME: Clarify specs
        g.navWindow shouldBe null
    }
}

fun TestContext.mouseDragWithDelta(delta: Vec2, button: Int = 0) {
    val g = uiContext!!
    if (isError)
        return

    REGISTER_DEPTH {
        logDebug("MouseDragWithDelta $button (%.1f, %.1f)", delta.x, delta.y)

        mouseDown(button)
        mouseMoveToPos(g.io.mousePos + delta)
        mouseUp(button)
    }
}