package app.tests

import engine.TestEngine
import engine.context.*
import engine.engine.registerTest
import glm_.vec2.Vec2
import imgui.*
import imgui.api.gImGui
import imgui.internal.classes.Window
import imgui.internal.sections.InputSource
import imgui.internal.sections.NavLayer
import imgui.internal.sections.get
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kool.getValue
import kool.setValue
import imgui.WindowFlag as Wf

//-------------------------------------------------------------------------
// Tests: Nav
//-------------------------------------------------------------------------

fun registerTests_Nav(e: TestEngine) {

    // ## Test opening a new window from a checkbox setting the focus to the new window.
    // In 9ba2028 (2019/01/04) we fixed a bug where holding ImGuiNavInputs_Activate too long on a button would hold the focus on the wrong window.
    e.registerTest("nav", "nav_basic").let { t ->
        t.testFunc = { ctx: TestContext ->
            ctx setInputMode InputSource.Nav
            ctx.setRef("Hello, world!")
            ctx.itemUncheck("Demo Window")
            ctx.itemCheck("Demo Window")

            val g = ctx.uiContext!!
            assert(g.navWindow!!.id == ctx.getID("Dear ImGui Demo"))
        }
    }

    // ## Test that CTRL+Tab steal active id (#2380)
    e.registerTest("nav", "nav_ctrl_tab_takes_activeid_away").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            dsl.window("Test window 1", null, Wf.NoSavedSettings.i) {
                ImGui.text("This is test window 1")
                ImGui.inputText("InputText", vars.str1)
            }
            dsl.window("Test window 2", null, Wf.NoSavedSettings.i) {
                ImGui.text("This is test window 2")
                ImGui.inputText("InputText", vars.str2)
            }
        }
        t.testFunc = { ctx: TestContext ->
            // FIXME-TESTS: Fails if window is resized too small
            ctx setInputMode InputSource.Nav
            ctx.setRef("Test window 1")
            ctx.itemInput("InputText")
            ctx.keyCharsAppend("123")
            ctx.uiContext!!.activeId shouldBe ctx.getID("InputText")
            ctx.keyPressMap(Key.Tab, KeyMod.Ctrl.i)
            ctx.uiContext!!.activeId shouldBe 0
            ctx.sleep(1f)
        }
    }

    // ## Test that ESC deactivate InputText without closing current Popup (#2321, #787)
    e.registerTest("nav", "nav_esc_popup").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            var popupOpen by vars::bool1
            var fieldActive by vars::bool2
            var popupId by vars::id

            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                if (ImGui.button("Open Popup"))
                    ImGui.openPopup("Popup")

                popupOpen = ImGui.beginPopup("Popup", Wf.NoSavedSettings.i)
                if (popupOpen) {
                    popupId = ImGui.currentWindow.id
                    ImGui.inputText("Field", vars.str1)
                    fieldActive = ImGui.isItemActive
                    ImGui.endPopup()
                }
            }
        }
        t.testFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            val popupOpen by vars::bool1
            val fieldActive by vars::bool2

            // FIXME-TESTS: Come up with a better mechanism to get popup ID
            var popupId by vars::id
            popupId = 0

            ctx.setRef("Test Window")
            ctx.itemClick("Open Popup")

            while (popupId == 0 && !ctx.isError)
                ctx.yield()

            ctx.setRef(popupId)
            ctx.itemClick("Field")
            assert(popupOpen)
            assert(fieldActive)

            ctx.keyPressMap(Key.Escape)
            assert(popupOpen)
            assert(!fieldActive)
        }
    }

    // ## Test that Alt toggle layer, test that AltGr doesn't.
    e.registerTest("nav", "nav_menu_alt_key").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val windowContent = {
                dsl.menuBar {
                    dsl.menu("Menu") {
                        ImGui.text("Blah")
                    }
                }
            }

            when (ctx.genericVars.step) {
                0 -> {
                    ImGui.begin("Test window", null, Wf.NoSavedSettings or Wf.MenuBar)
                    windowContent()
                    ImGui.end()
                }
                1 -> {
                    if (!ImGui.isPopupOpen("Test window"))
                        ImGui.openPopup("Test window")
                    if (ImGui.beginPopupModal("Test window", null, Wf.NoSavedSettings or Wf.MenuBar)) {
                        windowContent()
                        ImGui.endPopup()
                    }
                }
            }
        }
        t.testFunc = { ctx: TestContext ->
            ctx.uiContext!!.apply {
                // FIXME-TESTS: Fails if window is resized too small
                val g = ctx.uiContext!!
                (g.io.configFlags has ConfigFlag.NavEnableKeyboard) shouldBe true
                //ctx->SetInputMode(ImGuiInputSource_Nav);
                ctx.setRef("Test window")

                for (step in 0..1) {
                    ctx.genericVars.step = step // Enable modal popup?
                    ctx.yield()

                    g.openPopupStack.size shouldBe step
                    g.navLayer shouldBe NavLayer.Main
                    ctx.keyPressMap(Key.Count, KeyMod.Alt.i)
                    g.navLayer shouldBe NavLayer.Menu
                    g.navId shouldBe ctx.getID("##menubar/Menu")

                    ctx.keyPressMap(Key.Count, KeyMod.Alt.i)
                    g.navLayer shouldBe NavLayer.Main
                    ctx.keyPressMap(Key.Count, KeyMod.Alt or KeyMod.Ctrl)
                    g.navLayer shouldBe NavLayer.Main
                }
            }
        }
    }

    // ## Test navigation home and end keys
    e.registerTest("nav", "nav_home_end_keys").let { t ->
        t.guiFunc = {
            ImGui.setNextWindowSize(Vec2(100, 150))
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                for (i in 0..9)
                    ImGui.button("Button $i")
            }
        }
        t.testFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!
            (g.io.configFlags has ConfigFlag.NavEnableKeyboard) shouldBe true
            val window = ImGui.findWindowByName("Test Window")!!
            ctx.setRef("Test window")
            ctx.setInputMode(InputSource.Nav)

            // FIXME-TESTS: This should not be required but nav init request is not applied until we start navigating, this is a workaround
            ctx.keyPressMap(Key.Count, KeyMod.Alt.i)
            ctx.keyPressMap(Key.Count, KeyMod.Alt.i)

            assert(g.navId == window.getID("Button 0"))
            assert(window.scroll.y == 0f)
            // Navigate to the middle of window
            for (i in 0..4)
                ctx.keyPressMap(Key.DownArrow)
            assert(g.navId == window.getID("Button 5"))
            assert(window.scroll.y > 0 && window.scroll.y < window.scrollMax.y)
            // From the middle to the end
            ctx.keyPressMap(Key.End)
            assert(g.navId == window.getID("Button 9"))
            assert(window.scroll.y == window.scrollMax.y)
            // From the end to the start
            ctx.keyPressMap(Key.Home)
            assert(g.navId == window.getID("Button 0"))
            assert(window.scroll.y == 0f)
        }
    }

    // ## Test vertical wrap-around in menus/popups
    e.registerTest("nav", "nav_menu_wraparound").let { t ->
        t.testFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!
            ctx.setRef("Dear ImGui Demo")
            ctx.menuClick("Menu")
            ctx.keyPressMap(Key.Count, KeyMod.Alt.i) // FIXME
            g.navId shouldBe ctx.getID("/##Menu_00/New")
            ctx.navKeyPress(NavInput._KeyUp)
            g.navId shouldBe ctx.getID("/##Menu_00/Quit")
            ctx.navKeyPress(NavInput._KeyDown)
            g.navId shouldBe ctx.getID("/##Menu_00/New")
        }
    }

    // ## Test CTRL+TAB window focusing
    e.registerTest("nav", "nav_ctrl_tab_focusing").let { t ->
        t.guiFunc = {
            dsl.window("Window 1", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                ImGui.textUnformatted("Not empty space")
            }

            dsl.window("Window 2", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                ImGui.button("Button Out")
                dsl.child("Child", Vec2(50), true) {
                    ImGui.button("Button In")
                }
            }
        }
        t.testFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!
            val window2 = ctx.getWindowByRef("Window 2")!!

            // FIXME-TESTS: Facilitate usage of variants
            val testCount = if (ctx.hasDock) 2 else 1
            for (testN in 0 until testCount) {
                ctx.logDebug("TEST CASE $testN")
//                #ifdef IMGUI_HAS_DOCK
//                    ctx->DockMultiClear("Window 1", "Window 2", NULL)
//                if (test_n == 1)
//                    ctx->DockWindowInto("Window 1", "Window 2")
//                #endif

                // Set up window focus order.
                ctx.windowFocus("Window 1")
                ctx.windowFocus("Window 2")

                ctx.keyPressMap(Key.Tab, KeyMod.Ctrl.i)
                assert(g.navWindow === ctx.getWindowByRef("Window 1"))

                // Intentionally perform a "SLOW" ctrl-tab to make sure the UI appears!
                //ctx->KeyPressMap(ImGuiKey_Tab, ImGuiKeyModFlags_Ctrl);
                ctx.keyDownMap(Key.Count, KeyMod.Ctrl.i)
                ctx.keyPressMap(Key.Tab)
                ctx.sleepNoSkip(0.5f, 0.1f)
                ctx.keyUpMap(Key.Count, KeyMod.Ctrl.i)
                assert(g.navWindow === ctx.getWindowByRef("Window 2"))

                // Set up window focus order, focus child window.
                ctx.windowFocus("Window 1")
                ctx.windowFocus("Window 2") // FIXME: Needed for case when docked
                ctx.itemClick("Window 2\\/Child_%08X/Button In".format(window2.getID("Child")))

                ctx.keyPressMap(Key.Tab, KeyMod.Ctrl.i)
                assert(g.navWindow === ctx.getWindowByRef("Window 1"))
            }
        }
    }

    // ## Test NavID restoration during CTRL+TAB focusing
    e.registerTest("nav", "nav_ctrl_tab_nav_id_restore").let { t ->
        t.guiFunc = {
            dsl.window("Window 1", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                ImGui.button("Button 1")
            }

            dsl.window("Window 2", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                dsl.child("Child", Vec2(50), true) {
                    ImGui.button("Button 2")
                }
            }
        }
        t.testFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!

            // FIXME-TESTS: Facilitate usage of variants
            val testCount = if (ctx.hasDock) 2 else 1
            for (testN in 0 until testCount) {
                ctx.logDebug("TEST CASE $testN")
//                #ifdef IMGUI_HAS_DOCK
//                    ctx->DockMultiClear("Window 1", "Window 2", NULL)
//                if (test_n == 1)
//                    ctx->DockWindowInto("Window 2", "Window 1")
//                #endif

                val window2 = ctx.getWindowByRef("Window 2")!!

                val win1ButtonRef = "Window 1/Button 1"
                val win2ButtonRef = "Window 2\\/Child_%08X/Button 2".format(window2.getID("Child"))

                // Focus Window 1, navigate to the button
                ctx.windowFocus("Window 1")
                ctx.navMoveTo(win1ButtonRef)

                // Focus Window 2, ensure nav id was changed, navigate to the button
                ctx.windowFocus("Window 2")
                assert(ctx.getID(win1ButtonRef) != g.navId)
                ctx.navMoveTo(win2ButtonRef)

                // Ctrl+Tab back to previous window, check if nav id was restored
                ctx.keyPressMap(Key.Tab, KeyMod.Ctrl.i)
                assert(ctx.getID(win1ButtonRef) == g.navId)

                // Ctrl+Tab back to previous window, check if nav id was restored
                ctx.keyPressMap(Key.Tab, KeyMod.Ctrl.i)
                assert(ctx.getID(win2ButtonRef) == g.navId)
            }
        }
    }

    // ## Test NavID restoration when focusing another window or STOPPING to submit another world
    e.registerTest("nav", "nav_focus_restore").let { t ->
        t.guiFunc = { ctx: TestContext ->
            dsl.window("Window 1", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                ImGui.button("Button 1")
                ImGui.button("Button 2")
                ImGui.beginChild("Child", Vec2(100), true)
                ImGui.button("Button 3")
                ImGui.button("Button 4")
                ImGui.endChild()
                ImGui.button("Button 5")
            }

            if (ctx.genericVars.bool1)
                dsl.window("Window 2", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                    ImGui.button("Button 2")
                }
        }
        t.testFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!

            // FIXME-TESTS: Facilitate usage of variants
            val testCount = if (ctx.hasDock) 2 else 1
            for (testN in 0 until testCount) {
                ctx.logDebug("TEST CASE $testN")
                ctx.genericVars.bool1 = true
                ctx.yield(2)
//                #ifdef IMGUI_HAS_DOCK
//                    ctx->DockMultiClear("Window 1", "Window 2", NULL)
//                if (test_n == 1)
//                    ctx->DockWindowInto("Window 2", "Window 1")
//                #endif
                ctx.windowFocus("Window 1")
                ctx.navMoveTo("Window 1/Button 1")

                ctx.windowFocus("Window 2")
                ctx.navMoveTo("Window 2/Button 2")

                ctx.windowFocus("Window 1")
                assert(g.navId == ctx.getID("Window 1/Button 1"))

                ctx.windowFocus("Window 2")
                assert(g.navId == ctx.getID("Window 2/Button 2"))

                ctx.genericVars.bool1 = false
                ctx.yield(2)
                assert(g.navId == ctx.getID("Window 1/Button 1"))
            }

            // Test entering child window and leaving it.
            ctx.windowFocus("Window 1")
            ctx.navMoveTo("Window 1/Child")
            g.navId shouldBe ctx.getID("Window 1/Child")
            val nav = g.navWindow!!
            (nav.flags hasnt Wf._ChildWindow) shouldBe true

            ctx.navActivate()                                   // Enter child window
            (nav.flags has Wf._ChildWindow) shouldBe true
            ctx.navKeyPress(NavInput._KeyDown)                  // Manipulate something
            //IM_CHECK(g.NavId == ctx->ItemInfo("/**/Button 4")->ID); // Can't easily include child window name in ID because the / gets inhibited...
            g.navId shouldBe ctx.getID("Button 4", nav.id)
            ctx.navActivate()
            ctx.navKeyPress(NavInput.Cancel)                    // Leave child window
            g.navId shouldBe ctx.getID("Window 1/Child")    // Focus resumes last location before entering child window
        }
    }

    // ## Test NavID restoration after activating menu item.
    e.registerTest("nav", "nav_focus_restore_menu").let { t ->
        t.testFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!

            // FIXME-TESTS: Facilitate usage of variants
            val testCount = if (ctx.hasDock) 2 else 1
            for (testN in 0 until testCount) {
                ctx.logDebug("TEST CASE $testN")
//                #ifdef IMGUI_HAS_DOCK
//                    ctx->WindowRef(ImGuiTestRef())
//                ctx->DockMultiClear("Dear ImGui Demo", "Hello, world!", NULL)
//                if (test_n == 0)
//                    ctx->DockWindowInto("Dear ImGui Demo", "Hello, world!")
//                #endif
                ctx.setRef("Dear ImGui Demo")
                // Focus item.
                ctx.navMoveTo("Configuration")
                // Focus menu.
                ctx.keyPressMap(Key.Count, KeyMod.Alt.i)
                // Open menu, focus first item in the menu.
                ctx.navActivate()
                // Activate first item in the menu.
                ctx.navActivate()
                // Verify NavId was restored to initial value.
                assert(g.navId == ctx.getID("Configuration"))
            }
        }
    }

    // ## Test loss of navigation focus when clicking on empty viewport space (#3344).
    e.registerTest("nav", "nav_focus_clear_on_void").let { t ->
        t.testFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!
            ctx.setRef("Dear ImGui Demo")
            ctx.itemOpen("Help")
            ctx.itemClose("Help")
            g.navId shouldBe ctx.getID("Help")
            g.navWindow shouldBe ctx.getWindowByRef(ctx.refID)
            (g.io.configFlags has ConfigFlag.NavEnableKeyboard) shouldBe true // FIXME-TESTS: Should test for both cases.
            g.io.wantCaptureMouse shouldBe true
            // FIXME-TESTS: This depends on ImGuiConfigFlags_NavNoCaptureKeyboard being cleared. Should test for both cases.
            g.io.wantCaptureKeyboard shouldBe true

            ctx.mouseClickOnVoid(0)
            //IM_CHECK(g.NavId == 0); // Clarify specs
            g.navWindow shouldBe null
            g.io.wantCaptureMouse shouldBe false
            g.io.wantCaptureKeyboard shouldBe false
        }
    }

    // ## Test navigation in popups that are appended across multiple calls to BeginPopup()/EndPopup(). (#3223)
    e.registerTest("nav", "nav_appended_popup").let { t ->
        t.guiFunc = { ctx: TestContext ->
            if (ImGui.beginMainMenuBar()) {
                if (ImGui.beginMenu("Menu")) {
                    ImGui.menuItem("a")
                    ImGui.menuItem("b")
                    ImGui.endMenu()
                }
                ImGui.endMainMenuBar()
            }
            if (ImGui.beginMainMenuBar()) {
                if (ImGui.beginMenu("Menu")) {
                    ImGui.menuItem("c")
                    ImGui.menuItem("d")
                    ImGui.endMenu()
                }
                ImGui.endMainMenuBar()
            }
        }
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("##MainMenuBar")

            // Open menu, focus first "a" item.
            ctx.menuClick("Menu")
            ctx.keyPressMap(Key.Count, KeyMod.Alt.i) // FIXME
            ctx.setRef(ctx.uiContext!!.navWindow!!)

            // Navigate to "c" item.
            ImGui.focusID shouldBe ctx.getID("a")
            ctx.navKeyPress(NavInput._KeyDown)
            ctx.navKeyPress(NavInput._KeyDown)
            ImGui.focusID shouldBe ctx.getID("c")
            ctx.navKeyPress(NavInput._KeyDown)
            ctx.navKeyPress(NavInput._KeyDown)
            ImGui.focusID shouldBe ctx.getID("a")
        }
    }

    // ## Test nav from non-visible focus with keyboard/gamepad. With gamepad, the navigation resumes on the next visible item instead of next item after focused one.
    e.registerTest("nav", "nav_from_clipped_item").let { t ->
        t.guiFunc = { ctx: TestContext ->

            val g = gImGui!!

            ImGui.setNextWindowSize(ctx.genericVars.vec2, Cond.Always)
            ImGui.begin("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysVerticalScrollbar or Wf.AlwaysHorizontalScrollbar)
            for (y in 0..5)
                for (x in 0..5) {
                    ImGui.button("Button $x,$y")
                    if (x < 5)
                        ImGui.sameLine()

                    // Window size is such that only 3x3 buttons are visible at a time.
                    if (ctx.genericVars.vec2.y == 0f && y == 3 && x == 2)
                        ctx.genericVars.vec2 put (ImGui.cursorPos + g.style.scrollbarSize) // FIXME: Calculate Window Size from Decoration Size
                }
            ImGui.end()

        }
        t.testFunc = { ctx: TestContext ->

            ctx.setRef("Test Window")
            val window = ctx.getWindowByRef("")!!

            val getFocusItemRect = { window: Window ->
                window.navRectRel[NavLayer.Main].apply { translate(window.pos) }
            }

            // Test keyboard & gamepad behaviors
            for (n in 0..1) {

                val inputSource = if (n == 0) InputSource.NavKeyboard else InputSource.NavGamepad
                ctx.setInputMode(InputSource.Nav)
                ctx.uiContext!!.navInputSource = inputSource  // FIXME-NAV: Should be set by above ctx->SetInputMode(ImGuiInputSource_NavGamepad) call.

                // Down
                ctx.navMoveTo("Button 0,0")
                ctx.scrollToX(0f)
                ctx.scrollToBottom()
                ctx.navKeyPress(NavInput._KeyDown)
                if (inputSource == InputSource.NavKeyboard)
                    ImGui.focusID shouldBe ctx.getID("Button 0,1")     // Started Nav from Button 0,0 (Previous NavID)
                else
                    ImGui.focusID shouldBe ctx.getID("Button 0,3")      // Started Nav from Button 0,2 (Visible)

                ctx.navKeyPress(NavInput._KeyUp)                             // Move to opposite direction than previous operation in order to trigger scrolling focused item into view
                (getFocusItemRect(window) in window.innerRect) shouldBe true  // Ensure item scrolled into view is fully visible

                // Up
                ctx.navMoveTo("Button 0,4")
                ctx.scrollToX(0f)
                ctx.scrollToTop()
                ctx.navKeyPress(NavInput._KeyUp)
                if (inputSource == InputSource.NavKeyboard)
                    ImGui.focusID shouldBe ctx.getID("Button 0,3")
                else
                    ImGui.focusID shouldBe ctx.getID("Button 0,2")

                ctx.navKeyPress(NavInput._KeyDown)
                (getFocusItemRect(window) in window.innerRect) shouldBe true

                // Right
                ctx.navMoveTo("Button 0,0")
                ctx.scrollToX(window.scrollMax.x)
                ctx.scrollToTop()
                ctx.navKeyPress(NavInput._KeyRight)
                if (inputSource == InputSource.NavKeyboard)
                    ImGui.focusID shouldBe ctx.getID("Button 1,0")
                else
                    ImGui.focusID shouldBe ctx.getID("Button 3,0")

                ctx.navKeyPress(NavInput._KeyLeft)
                (getFocusItemRect(window) in window.innerRect) shouldBe true

                // Left
                ctx.navMoveTo("Button 4,0")
                ctx.scrollToX(0f)
                ctx.scrollToTop()
                ctx.navKeyPress(NavInput._KeyLeft)
                if (inputSource == InputSource.NavKeyboard)
                    ImGui.focusID shouldBe ctx.getID("Button 3,0")
                else
                    ImGui.focusID shouldBe ctx.getID("Button 2,0")

                ctx.navKeyPress(NavInput._KeyRight)
                (getFocusItemRect(window) in window.innerRect) shouldBe true
            }
        }
    }

    // ## Test PageUp/PageDown/Home/End keys.
    e.registerTest("nav", "nav_page_end_home_keys").let { t ->
        t.guiFunc = { ctx: TestContext ->

            ImGui.setNextWindowSize(Vec2(100, ctx.genericVars.float1), Cond.Always)

            // FIXME-NAV: Lack of ImGuiWindowFlags_NoCollapse breaks window scrolling without activable items.
            ImGui.begin("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysVerticalScrollbar or Wf.NoCollapse)
            for (i in 0..19) {
                if (ctx.genericVars.step == 0)
                    ImGui.textUnformatted("OK $i")
                else
                    ImGui.button("OK $i")
                if (i == 2)
                    ctx.genericVars.float1 = ImGui.cursorPosY
            }
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->

            ctx.setRef("Test Window")
            val g = ctx.uiContext!!
            val window = ctx.getWindowByRef("")!!

            // Test page up/page down/home/end keys WITHOUT any navigable items.
            ctx.genericVars.step = 0
            window.scrollMax.y shouldBeGreaterThan 0f               // We have a scrollbar
            window setScrollY 0f                    // Reset starting position.

            ctx.keyPressMap(Key.PageDown)                // Scrolled down some, but not to the bottom.
            (0 < window.scroll.y && window.scroll.y < window.scrollMax.y) shouldBe true
            ctx.keyPressMap(Key.End)                     // Scrolled all the way to the bottom.
            window.scroll.y shouldBe window.scrollMax.y
            val lastScroll = window.scroll.y               // Scrolled up some, but not all the way to the top.
            ctx.keyPressMap(Key.PageUp)
            (0 < window.scroll.y && window.scroll.y < lastScroll) shouldBe true
            ctx.keyPressMap(Key.Home)                    // Scrolled all the way to the top.
            window.scroll.y shouldBe 0f

            // Test page up/page down/home/end keys WITH navigable items.
            ctx.genericVars.step = 1
            ctx.yield(2)
            //g.NavId = 0;
            window.navRectRel.forEach { it.put(0f, 0f, 0f, 0f) }
            ctx.keyPressMap(Key.PageDown)
            g.navId shouldBe ctx.getID("OK 0")            // FIXME-NAV: Main layer had no focus, key press simply focused it. We preserve this behavior across multiple test runs in the same session by resetting window->NavRectRel.
            ctx.keyPressMap(Key.PageDown)
            g.navId shouldBe ctx.getID("OK 2")            // Now focus changes to a last visible button.
            window.scroll.y shouldBe 0f                    // Window is not scrolled.
            ctx.keyPressMap(Key.PageDown)
            g.navId shouldBe ctx.getID("OK 5")            // Focus item on the next "page".
            window.scroll.y shouldBeGreaterThan 0f
            ctx.keyPressMap(Key.End)                     // Focus last item.
            window.scroll.y shouldBe window.scrollMax.y
            g.navId shouldBe ctx.getID("OK 19")
            ctx.keyPressMap(Key.PageUp)
            g.navId shouldBe ctx.getID("OK 17")           // Focus first item visible in the last section.
            window.scroll.y shouldBe window.scrollMax.y  // Window is not scrolled.
            ctx.keyPressMap(Key.PageUp)
            g.navId shouldBe ctx.getID("OK 14")           // Focus first item of previous "page".
            (0 < window.scroll.y && window.scroll.y < window.scrollMax.y) shouldBe true // Window is not scrolled.
            ctx.keyPressMap(Key.Home)
            g.navId shouldBe ctx.getID("OK 0")           // Focus very first item.
            window.scroll.y shouldBe 0f                   // Window is scrolled to the start.
        }
    }
}