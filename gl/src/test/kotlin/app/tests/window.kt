package app.tests

import engine.TestEngine
import engine.context.*
import engine.engine.*
import glm_.ext.equal
import glm_.f
import glm_.has
import glm_.i
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import imgui.*
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import imgui.WindowFlag as Wf

//-------------------------------------------------------------------------
// Tests: Window
//-------------------------------------------------------------------------

fun registerTests_Window(e: TestEngine) {

    // ## Test size of an empty window
    e.registerTest("window", "empty").let { t ->
        t.guiFunc = {
            //ImGui::GetStyle().WindowMinSize = ImVec2(10, 10);
            ImGui.begin("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize)
            ImGui.end()
        }
        t.testFunc = {
            val style = ImGui.style
            ImGui.findWindowByName("Test Window")!!.apply {
                CHECK_EQ(size.x, max(style.windowMinSize.x.f, style.windowPadding.x * 2f))
                CHECK_EQ(size.y, ImGui.fontSize + style.framePadding.y * 2f + style.windowPadding.y * 2f)
                contentSize shouldBe Vec2()
                scroll shouldBe Vec2()
            }
        }
    }

    // ## Test that a window starting collapsed performs width/contents size measurement on its first few frames.
    e.registerTest("window", "window_size_collapsed_1").let { t ->
        t.guiFunc = { ctx: TestContext ->
            ImGui.setNextWindowCollapsed(true, Cond.Appearing)
            ImGui.begin("Issue 2336", null, Wf.NoSavedSettings.i)
            ImGui.text("This is some text")
            ImGui.text("This is some more text")
            ImGui.text("This is some more text again")

            val w = ImGui.windowWidth
            if (ctx.frameCount == 0) { // We are past the warm-up frames already
                val expectedW = ImGui.calcTextSize("This is some more text again").x + ImGui.style.windowPadding.x * 2f
                abs(w - expectedW) shouldBeLessThan 1f
            }
            ImGui.end()
        }
    }

    e.registerTest("window", "window_size_contents").let { t ->
        t.flags = t.flags or TestFlag.NoAutoFinish
        t.guiFunc = { ctx: TestContext ->

            val style = ImGui.style

            dsl.window("Test Contents Size 1", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                ImGui.colorButton("test", Vec4(1f, 0.4f, 0f, 1f), ColorEditFlag.NoTooltip.i, Vec2(150))
                val window = ctx.uiContext!!.currentWindow!!
                if (ctx.frameCount > 0)
                    window.contentSize shouldBe Vec2(150f)
            }
            ImGui.setNextWindowContentSize(Vec2(150, 150))
            dsl.window("Test Contents Size 2", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                val window = ctx.uiContext!!.currentWindow!!
                if (ctx.frameCount >= 0)
                    window.contentSize shouldBe Vec2(150.0f)
            }
            ImGui.setNextWindowContentSize(Vec2(150))
            ImGui.setNextWindowSize(Vec2(150) + style.windowPadding * 2f + Vec2(0f, ImGui.frameHeight))
            dsl.window("Test Contents Size 3", null, Wf.NoSavedSettings or Wf.None) {
                val window = ctx.uiContext!!.currentWindow!!
                if (ctx.frameCount >= 0) {
                    window.scrollbar.y shouldBe false
                    window.scrollMax.y shouldBe 0f
                }
            }
            ImGui.setNextWindowContentSize(Vec2(150, 150 + 1))
            ImGui.setNextWindowSize(Vec2(150) + style.windowPadding * 2f + Vec2(0f, ImGui.frameHeight))
            dsl.window("Test Contents Size 4", null, Wf.NoSavedSettings or Wf.None) {
                val window = ctx.uiContext!!.currentWindow!!
                if (ctx.frameCount >= 0) {
                    window.scrollbar.y shouldBe true
                    window.scrollMax.y shouldBe 1f
                }
            }

            if (ctx.frameCount == 2)
                ctx.finish()
        }
    }

    // ## Test that non-integer size/position passed to window gets rounded down and not cause any drift.
    e.registerTest("window", "window_size_unrounded").let { t ->
        t.flags = t.flags or TestFlag.NoAutoFinish
        t.guiFunc = { ctx: TestContext ->
            // #2067
            ImGui.setNextWindowPos(Vec2(901f, 103f), Cond.Once)
            ImGui.setNextWindowSize(Vec2(348.48f, 400f), Cond.Once)
            dsl.window("Issue 2067", null, Wf.NoSavedSettings.i) {
                val pos = ImGui.windowPos
                val size = ImGui.windowSize
                //ctx->LogDebug("%f %f, %f %f", pos.x, pos.y, size.x, size.y);
                pos.x shouldBe 901f; pos.y shouldBe 103f
                size.x shouldBe 348f; size.y shouldBe 400f
            }
            // Test that non-rounded size constraint are not altering pos/size (#2530)
            ImGui.setNextWindowPos(Vec2(901f, 103f), Cond.Once)
            ImGui.setNextWindowSize(Vec2(348.48f, 400f), Cond.Once)
            ImGui.setNextWindowSizeConstraints(Vec2(475.200012f, 0f), Vec2(475.200012f, 100.4f))
            dsl.window("Issue 2530", null, Wf.NoSavedSettings.i) {
                val pos = ImGui.windowPos
                val size = ImGui.windowSize
                //ctx->LogDebug("%f %f, %f %f", pos.x, pos.y, size.x, size.y);
                pos shouldBe Vec2(901f, 103f)
                size shouldBe Vec2(475f, 100f)
            }
            if (ctx.frameCount == 2)
                ctx.finish()
        }
    }

    // ## Test basic window auto resize
    e.registerTest("window", "window_auto_resize_basic").let { t ->
        t.guiFunc = { ctx: TestContext ->
            // FIXME-TESTS: Ideally we'd like a variant with/without the if (Begin) here
            // FIXME: BeginChild() auto-resizes horizontally, this width is calculated by using data from previous frame.
            //  If window was wider on previous frame child would permanently assume new width and that creates a feedback
            //  loop keeping new size.
            ImGui.setNextWindowSize(Vec2(1), Cond.Appearing) // Fixes test failing due to side-effects caused by other tests using window with same name.
            ImGui.begin("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize)
            ImGui.text("Hello World")
            ImGui.beginChild("Child", Vec2(0, 200))
            ImGui.endChild()
            val sz = ImGui.windowSize
            ImGui.end()
            if (ctx.frameCount in 0..2) {
                val style = ImGui.style
                sz.x.i shouldBe (ImGui.calcTextSize("Hello World").x + style.windowPadding.x * 2f).i
                sz.y.i shouldBe (ImGui.frameHeight + ImGui.calcTextSize("Hello World").y + style.itemSpacing.y + 200f + style.windowPadding.y * 2f).i
            }
        }
    }

    // ## Test that uncollapsing an auto-resizing window does not go through a frame where the window is smaller than expected
    e.registerTest("window", "window_auto_resize_uncollapse").let { t ->
        t.guiFunc = {
            // FIXME-TESTS: Ideally we'd like a variant with/without the if (Begin) here
            dsl.window("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                ImGui.text("Some text\nOver two lines\nOver three lines")
            }
        }
        t.testFunc = { ctx: TestContext ->
            val window = ImGui.findWindowByName("Test Window")!!
            ctx.opFlags = ctx.opFlags or TestOpFlag.NoAutoUncollapse
            ctx.setRef("Test Window")
            ctx.windowCollapse(window, false)
            ctx.logDebug("Size %f %f, SizeFull %f %f", window.size.x, window.size.y, window.sizeFull.x, window.sizeFull.y)
            window.size shouldBe window.sizeFull
            val sizeFullWhenUncollapsed = window.sizeFull
            ctx.windowCollapse(window, true)
            ctx.logDebug("Size %f %f, SizeFull %f %f", window.size.x, window.size.y, window.sizeFull.x, window.sizeFull.y)
            val sizeCollapsed = window.size
            sizeFullWhenUncollapsed.y shouldBeGreaterThan sizeCollapsed.y
            sizeFullWhenUncollapsed shouldBe window.sizeFull
            ctx.windowCollapse(window, false)
            ctx.logDebug("Size %f %f, SizeFull %f %f", window.size.x, window.size.y, window.sizeFull.x, window.sizeFull.y)
            assert(window.size.y == sizeFullWhenUncollapsed.y) { "Window should have restored to full size." }
            ctx.yield()
            window.size.y shouldBe sizeFullWhenUncollapsed.y
        }
    }

    // ## Test appending multiple times to a child window (bug #2282)
    e.registerTest("window", "window_append").let { t ->
        t.guiFunc = {
            ImGui.setNextWindowSize(Vec2(200))
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                ImGui.text("Line 1")
            }
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                ImGui.text("Line 2")
                dsl.child("Blah", Vec2(0, 50), true) {
                    ImGui.text("Line 3")
                }
                val pos1 = Vec2(ImGui.cursorScreenPos)
                dsl.child("Blah") {
                    ImGui.text("Line 4")
                }
                val pos2 = ImGui.cursorScreenPos
                pos1 shouldBe pos2 // Append calls to BeginChild() shouldn't affect CursorPos in parent window
                ImGui.text("Line 5")
                val pos3 = Vec2(ImGui.cursorScreenPos)
                dsl.child("Blah") {
                    ImGui.text("Line 6")
                }
                val pos4 = ImGui.cursorScreenPos
                pos3 shouldBe pos4 // Append calls to BeginChild() shouldn't affect CursorPos in parent window
            }
        }
    }

    // ## Test basic focus behavior
    // FIXME-TESTS: This in particular when combined with Docking should be tested with and without ConfigDockingTabBarOnSingleWindows
    e.registerTest("window", "window_focus_1").let { t ->
        t.guiFunc = { ctx: TestContext ->
            dsl.window("AAAA", null, Wf.NoSavedSettings.i) {}
            dsl.window("BBBB", null, Wf.NoSavedSettings.i) {}
            if (ctx.frameCount in 20..39 || ctx.frameCount >= 50) {
                dsl.window("CCCC", null, Wf.NoSavedSettings.i) {}
                dsl.window("DDDD", null, Wf.NoSavedSettings.i) {}
            }
        }
        t.testFunc = { ctx: TestContext ->
            fun id() = ctx.uiContext!!.navWindow!!.id
            id() shouldBe ctx.getID("/BBBB")
            ctx.yieldUntil(19)
            id() shouldBe ctx.getID("/BBBB")
            ctx.yieldUntil(20)
            id() shouldBe ctx.getID("/DDDD")
            ctx.yieldUntil(30)
            ctx.windowFocus("/CCCC")
            id() shouldBe ctx.getID("/CCCC")
            ctx.yieldUntil(39)
            ctx.yieldUntil(40)
            id() shouldBe ctx.getID("/CCCC")

            // When docked, it should NOT takes 1 extra frame to lose focus (fixed 2019/03/28)
            ctx.yieldUntil(41)
            id() shouldBe ctx.getID("/BBBB")

            ctx.yieldUntil(49)
            id() shouldBe ctx.getID("/BBBB")
            ctx.yieldUntil(50)
            id() shouldBe ctx.getID("/DDDD")
        }
    }

    // ## Test popup focus and right-click to close popups up to a given level
    e.registerTest("window", "window_popup_focus").let { t ->
        t.testFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!
            ctx.setRef("Dear ImGui Demo")
            ctx.itemOpen("Popups & Modal windows")
            ctx.itemOpen("Popups")
            ctx.itemClick("Popups/Toggle..")

            val popup1 = g.navWindow!!
            ctx.setRef(popup1)
            ctx.itemClick("Stacked Popup")
            assert(popup1.wasActive)

            val popup2 = g.navWindow!!
            ctx.mouseMove("Bream", TestOpFlag.NoFocusWindow or TestOpFlag.NoCheckHoveredId)
            ctx.mouseClick(1) // Close with right-click
            assert(popup1.wasActive)
            assert(!popup2.wasActive)
            assert(g.navWindow === popup1)
        }
    }

    // ## Test an edge case of calling CloseCurrentPopup() after clicking it (#2880)
    // Previously we would mistakenly handle the click on EndFrame() while popup is already marked as closed, doing a double close
    e.registerTest("window", "window_popup_focus2").let { t ->
        t.guiFunc = { ctx: TestContext ->
            ImGui.begin("Stacked Modal Popups")
            if (ImGui.button("Open Modal Popup 1"))
                ImGui.openPopup("Popup1")
            if (ImGui.beginPopupModal("Popup1", null, Wf.NoSavedSettings.i)) {
                if (ImGui.button("Open Modal Popup 2"))
                    ImGui.openPopup("Popup2")
                ImGui.setNextWindowSize(Vec2(100))
                if (ImGui.beginPopupModal("Popup2", null, Wf.NoSavedSettings.i)) {
                    ImGui.text("Click anywhere")
                    if (ImGui.isMouseClicked(MouseButton.Left))
                        ImGui.closeCurrentPopup()
                    ImGui.endPopup()
                }
                ImGui.endPopup()
            }
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!
            ctx.setRef("Stacked Modal Popups")
            ctx.itemClick("Open Modal Popup 1")
            val nav = g.navWindow!!
            nav.id shouldBe ctx.getID("/Popup1")
            g.openPopupStack.size shouldBe 1
            ctx.setRef("Popup1")
            ctx.itemClick("Open Modal Popup 2")
            nav.id shouldBe ctx.getID("/Popup2")
            g.openPopupStack.size shouldBe 2
            ctx.mouseMoveToPos(nav.rect().center)
            ctx.mouseClick(0)
            g.openPopupStack.size shouldBe 1
            nav.id shouldBe ctx.getID("/Popup1")
        }
    }

    // ## Test closing current popup
    e.registerTest("window", "window_popup_close_current").let { t ->
        t.guiFunc = { ctx: TestContext ->
            ImGui.setNextWindowSize(Vec2())
            ImGui.begin("Popups", null, Wf.NoSavedSettings or Wf.MenuBar)
            if (ImGui.beginMenuBar())
                if (ImGui.beginMenu("Menu"))
                    if (ImGui.beginMenu("Submenu"))
                        if (ImGui.menuItem("Close"))
                            ImGui.closeCurrentPopup()
            ImGui.endMenu()
            ImGui.endMenu()
            ImGui.endMenuBar()
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Popups")
            val stack = ctx.uiContext!!.openPopupStack
            stack.size shouldBe 0
            ctx.menuClick("Menu")
            stack.size shouldBe 1
            ctx.menuClick("Menu/Submenu")
            stack.size shouldBe 2
            ctx.menuClick("Menu/Submenu/Close")
            stack.size shouldBe 0
        }
    }

    // ## Test that child window correctly affect contents size based on how their size was specified.
    e.registerTest("window", "window_child_layout_size").let { t ->
        t.flags = t.flags or TestFlag.NoAutoFinish
        t.guiFunc = { ctx: TestContext ->
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                ImGui.text("Hi")
                dsl.child("Child 1", Vec2(100), true) {}
                if (ctx.frameCount == 2)
                    ctx.uiContext!!.currentWindow!!.contentSize shouldBe Vec2(100, 100 + ImGui.textLineHeightWithSpacing)
                if (ctx.frameCount == 2)
                    ctx.finish()
            }
        }
    }

    // ## Test that child window outside the visible section of their parent are clipped
    e.registerTest("window", "window_child_clip").let { t ->
        t.flags = t.flags or TestFlag.NoAutoFinish
        t.guiFunc = { ctx: TestContext ->
            ImGui.setNextWindowSize(Vec2(200))
            ImGui.begin("Test Window", null, Wf.NoSavedSettings.i)
            ImGui.cursorPos = Vec2()
            ImGui.beginChild("Child 1", Vec2(100))
            if (ctx.frameCount == 2)
                ctx.uiContext!!.currentWindow!!.skipItems shouldBe false
            ImGui.endChild()
            ImGui.cursorPos = Vec2(300)
            ImGui.beginChild("Child 2", Vec2(100))
            if (ctx.frameCount == 2)
                ctx.uiContext!!.currentWindow!!.skipItems shouldBe true
            ImGui.endChild()
            ImGui.end()
            if (ctx.frameCount == 2)
                ctx.finish()
        }
    }

    // ## Test that basic SetScrollHereY call scrolls all the way (#1804)
    // ## Test expected value of ScrollMaxY
    e.registerTest("window", "window_scroll_001").let { t ->
        t.guiFunc = {
            ImGui.setNextWindowSize(Vec2(0f, 500f))
            ImGui.begin("Test Scrolling", null, Wf.NoSavedSettings.i)
            for (n in 0..99)
                ImGui.text("Hello $n\n")
            ImGui.setScrollHereY(0f)
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            val window = ImGui.findWindowByName("Test Scrolling")!!
            val style = ImGui.style

            window.contentSize.y shouldBeGreaterThan 0f
            val scrollY = window.scroll.y
            val scrollMaxY = window.scrollMax.y
            ctx.logDebug("scroll_y = $scrollY")
            ctx.logDebug("scroll_max_y = $scrollMaxY")
            ctx.logDebug("window->SizeContents.y = ${window.contentSize.y}")

            scrollY shouldBeGreaterThan 0f
            scrollY shouldBe scrollMaxY

            val expectedSizeContentsY = 100 * ImGui.textLineHeightWithSpacing - style.itemSpacing.y // Newer definition of SizeContents as per 1.71
            window.contentSize.y.equal(expectedSizeContentsY) shouldBe true

            val expectedScrollMaxY = expectedSizeContentsY + window.windowPadding.y * 2f - window.innerRect.height
            scrollMaxY.equal(expectedScrollMaxY) shouldBe true
        }
    }

    // ## Test that ScrollMax values are correctly zero (we had/have bugs where they are seldom == BorderSize)
    e.registerTest("window", "window_scroll_002").let { t ->
        t.flags = t.flags or TestFlag.NoAutoFinish
        t.guiFunc = { ctx: TestContext ->
            ImGui.begin("Test Scrolling 1", null, Wf.NoSavedSettings or Wf.AlwaysHorizontalScrollbar or Wf.AlwaysAutoResize)
            ImGui.dummy(Vec2(200))
            ctx.uiContext!!.currentWindow!!.apply {
                scrollMax.x shouldBe 0f // FIXME-TESTS: If another window in another test used same name, ScrollMax won't be zero on first frame
                scrollMax.y shouldBe 0f
            }
            ImGui.end()

            ImGui.begin("Test Scrolling 2", null, Wf.NoSavedSettings or Wf.AlwaysVerticalScrollbar or Wf.AlwaysAutoResize)
            ImGui.dummy(Vec2(200))
            ctx.uiContext!!.currentWindow!!.apply {
                scrollMax.x shouldBe 0f
                scrollMax.y shouldBe 0f
            }
            ImGui.end()
            if (ctx.frameCount == 2)
                ctx.finish()
        }
    }

    // ## Test that SetScrollY/GetScrollY values are matching. You'd think this would be obvious! Think again!
    // FIXME-TESTS: With/without menu bars, could we easily allow for test variations that affects both GuiFunc and TestFunc
    e.registerTest("window", "window_scroll_003").let { t ->
        t.guiFunc = {
            dsl.window("Test Scrolling 3", null, Wf.NoSavedSettings.i) {
                for (n in 0..99)
                    ImGui.text("Line $n")
            }
        }
        t.testFunc = { ctx: TestContext ->
            ctx.yield()
            val window = ImGui.findWindowByName("Test Scrolling 3")!!
            window.setScrollY(100f)
            ctx.yield()
            val sy = window.scroll.y
            sy shouldBe 100f
        }
    }

    // ## Test that an auto-fit window doesn't have scrollbar while resizing (FIXME-TESTS: Also test non-zero ScrollMax when implemented)
    e.registerTest("window", "window_scroll_while_resizing").let { t ->
        t.flags = t.flags or TestFlag.NoAutoFinish
        t.guiFunc = { ctx: TestContext ->
            dsl.window("Test Scrolling", null, Wf.NoSavedSettings.i) {
                ImGui.text("Below is a child window")
                dsl.child("blah", Vec2(), false, Wf.HorizontalScrollbar.i) {
                    val childWindow = ctx.uiContext!!.currentWindow!!
                    childWindow.scrollbar.y shouldBe false
                }
                if (ctx.frameCount >= ctx.firstTestFrameCount) {
                    val window = ctx.uiContext!!.currentWindow!!
                    window.scrollbar.y shouldBe false
                    //IM_CHECK(window->ScrollMax.y == 0.0f);    // FIXME-TESTS: 1.71 I would like to make this change but unsure of side effects yet
                    if (ctx.frameCount == 2)
                        ctx.finish()
                }
            }
        }
        t.testFunc = { ctx: TestContext ->
            ctx.windowResize("Test Scrolling", Vec2(400))
            ctx.windowResize("Test Scrolling", Vec2(100))
        }
    }

    // ## Test window moving
    e.registerTest("window", "window_move").let { t ->
        t.guiFunc = {
            ImGui.setNextWindowSize(Vec2(0))
            dsl.window("Movable Window", null, Wf.NoSavedSettings.i) {
                ImGui.textUnformatted("Lorem ipsum dolor sit amet")
            }
        }
        t.testFunc = { ctx: TestContext ->
            val window = ctx.getWindowByRef("Movable Window")!!
            ctx.windowMove("Movable Window", Vec2())
            window.pos shouldBe Vec2()
            ctx.windowMove("Movable Window", Vec2(100, 0))
            window.pos shouldBe Vec2(100, 0)
            ctx.windowMove("Movable Window", Vec2(50, 100))
            window.pos shouldBe Vec2(50, 100)
        }
    }

    // ## Test explicit window positioning
    e.registerTest("window", "window_pos_pivot").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            ImGui.setNextWindowSize(Vec2())
            ImGui.setNextWindowPos(vars.pos, Cond.Always, vars.pivot)
            ImGui.begin("Movable Window", null, Wf.NoSavedSettings.i)
            ImGui.textUnformatted("Lorem ipsum dolor sit amet")
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            val window = ctx.getWindowByRef("Movable Window")!!
            for (n in 0..3)     // Test all pivot combinations.
                for (c in 0..1) { // Test collapsed and uncollapsed windows.
                    ctx.windowCollapse(window, c != 0)
                    vars.pos put (ctx.mainViewportPos + window.size) // Ensure window is tested within a visible viewport.
                    vars.pivot.put((n has 1).i, (n has 2).i)
                    ctx.yield()
                    window.pos shouldBe (vars.pos - window.size * vars.pivot)
                }
        }
    }

    // ## Test window resizing from edges and corners.
    e.registerTest("window", "window_resizing").let { t ->
        t.guiFunc = { ctx: TestContext ->
            ImGui.begin("Test Window", null, Wf.NoSavedSettings or Wf.NoCollapse)
            ImGui.textUnformatted("Lorem ipsum dolor sit amet")
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            val window = ctx.getWindowByRef("Test Window")!!
            for (testData in testDatas) {
                window.setPos(Vec2(100))
                window.setSize(Vec2(200, 50))
                ctx.yield()
                ctx.mouseMoveToPos(window.pos + ((window.size - 1f) * testData.grabPos))
                ctx.mouseDragWithDelta(testData.dragDir)
                window.size shouldBe testData.expectSize
                window.pos shouldBe testData.expectPos
            }
        }
    }
}

private class TestData(val grabPos: Vec2,        // window->Pos + window->Size * grab_pos
                       val dragDir: Vec2, val expectPos: Vec2, val expectSize: Vec2)

private val testDatas = arrayOf(
        TestData(Vec2(0.5, 0), Vec2(0, -10), Vec2(100, 90), Vec2(200, 60)),    // From top edge go up
        TestData(Vec2(1, 0), Vec2(10, -10), Vec2(110, 90), Vec2(200, 50)),    // From top-right corner, no resize, window is moved
        TestData(Vec2(1, 0.5), Vec2(10, 0), Vec2(100, 100), Vec2(210, 50)),    // From right edge go right
        TestData(Vec2(1, 1), Vec2(10, 10), Vec2(100, 100), Vec2(210, 60)),    // From bottom-right corner go right-down
        TestData(Vec2(0.5, 1), Vec2(0, 10), Vec2(100, 100), Vec2(200, 60)),    // From bottom edge go down
        TestData(Vec2(0, 1), Vec2(-10, 10), Vec2(90, 100), Vec2(210, 60)),    // From bottom-left corner go left-down
        TestData(Vec2(0, 0.5), Vec2(-10, 0), Vec2(90, 100), Vec2(210, 50)),    // From left edge go left
        TestData(Vec2(0, 0), Vec2(-10, -10), Vec2(90, 90), Vec2(200, 50)))    // From left-top edge, no resize, window is moved