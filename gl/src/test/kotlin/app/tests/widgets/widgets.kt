package app.tests

import app.tests.widgets.registerTests_Widgets_button
import engine.TestEngine
import engine.context.*
import engine.engine.CHECK
import engine.engine.TestOpFlag
import engine.engine.TestRunFlag
import engine.engine.registerTest
import glm_.ext.equal
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import imgui.*
import imgui.internal.classes.Rect
import imgui.internal.classes.TabBar
import imgui.internal.hash
import imgui.internal.sections.ItemStatusFlag
import imgui.internal.sections.has
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import unsigned.Ubyte
import unsigned.Uint
import unsigned.Ulong
import unsigned.Ushort
import kotlin.reflect.KMutableProperty0
import imgui.WindowFlag as Wf

//-------------------------------------------------------------------------
// Tests: Widgets
//-------------------------------------------------------------------------

fun registerTests_Widgets(e: TestEngine) {

    registerTests_Widgets_button(e)

    // ## Test checkbox click
    e.registerTest("widgets", "widgets_checkbox_001").let { t ->
        t.guiFunc = { ctx: TestContext ->
            dsl.window("Window1", null, Wf.NoSavedSettings.i) {
                ImGui.checkbox("Checkbox", ctx.genericVars::bool1)
            }
        }
        t.testFunc = { ctx: TestContext ->
            // We use WindowRef() to ensure the window is uncollapsed.
            ctx.genericVars.bool1 shouldBe false
            ctx.setRef("Window1")
            ctx.itemClick("Checkbox")
            ctx.genericVars.bool1 shouldBe true
        }
    }

    // ## Test all types with DragScalar().
    e.registerTest("widgets", "widgets_datatype_1").let { t ->
//        TODO("resync")
    }

    // ## Test DragInt() as InputText
    // ## Test ColorEdit4() as InputText (#2557)
    e.registerTest("widgets", "widgets_as_input").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                ImGui.dragInt("Drag", vars::int1)
                ImGui.colorEdit4("Color", vars.vec4)
            }
        }
        t.testFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            ctx.setRef("Test Window")

            vars.int1 shouldBe 0
            ctx.itemInput("Drag")
            ctx.uiContext!!.activeId shouldBe ctx.getID("Drag")
            ctx.keyCharsAppendEnter("123")
            vars.int1 shouldBe 123

            ctx.itemInput("Color##Y")
            ctx.uiContext!!.activeId shouldBe ctx.getID("Color##Y")
            ctx.keyCharsAppend("123")
            vars.vec4.y.equal(123f / 255f) shouldBe true
            ctx.keyPressMap(Key.Tab)
            ctx.keyCharsAppendEnter("200")
            (vars.vec4.x.equal(0f / 255f)) shouldBe true
            (vars.vec4.y.equal(123f / 255f)) shouldBe true
            (vars.vec4.z.equal(200f / 255f)) shouldBe true
        }
    }

    // ## Test Sliders and Drags clamping values
    e.registerTest("widgets", "widgets_drag_slider_clamping").let { t ->
        class DragSliderVars(var dragValue: Float = 0f, var dragMin: Float = 0f, var dragMax: Float = 1f,
                             var sliderValue: Float = 0f, var sliderMin: Float = 0f, var sliderMax: Float = 0f,
                             var flags: SliderFlags = SliderFlag.None.i)
        t.userData = DragSliderVars()
        t.guiFunc = { ctx: TestContext ->

            val vars = ctx.getUserData<DragSliderVars>()
            ImGui.begin("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize)
            val format = "%.3f"
            ImGui.sliderFloat("Slider", vars::sliderValue, vars.sliderMin, vars.sliderMax, format, vars.flags)
            ImGui.dragFloat("Drag", vars::dragValue, 1f, vars.dragMin, vars.dragMax, format, vars.flags)
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->

            val g = ImGui.currentContext!!
            val vars = ctx.getUserData<DragSliderVars>()
            ctx.setRef("Test Window")
            val flags = arrayOf(SliderFlag.None, SliderFlag.AlwaysClamp)
            for (flag in flags) {
                val clampOnInput = flag == SliderFlag.AlwaysClamp
                vars.flags = flag.i

                val sliderMinMax = arrayOf(floatArrayOf(0f, 1f), floatArrayOf(0f, 0f))
                for (j in sliderMinMax.indices) {

                    ctx.logInfo("## Slider $j with Flags = 0x%08X", vars.flags)

                    vars.sliderValue = 0f
                    vars.sliderMin = sliderMinMax[j][0]
                    vars.sliderMax = sliderMinMax[j][1]

                    ctx.itemInput("Slider")
                    ctx.keyCharsReplaceEnter("2")
                    vars.sliderValue shouldBe if (clampOnInput) vars.sliderMax else 2f

                    // Check higher bound
                    ctx.mouseMove("Slider", TestOpFlag.MoveToEdgeR.i)
                    ctx.mouseDown() // Click will update clamping
                    vars.sliderValue shouldBe vars.sliderMax
                    ctx.mouseMoveToPos(g.io.mousePos + Vec2(100, 0))
                    ctx.mouseUp()
                    vars.sliderValue shouldBe vars.sliderMax

                    ctx.itemInput("Slider")
                    ctx.keyCharsReplaceEnter("-2")
                    vars.sliderValue shouldBe if (clampOnInput) vars.sliderMin else -2f

                    // Check lower bound
                    ctx.mouseMove("Slider", TestOpFlag.MoveToEdgeL.i)
                    ctx.mouseDown() // Click will update clamping
                    vars.sliderValue shouldBe vars.sliderMin
                    ctx.mouseMoveToPos(g.io.mousePos - Vec2(100, 0))
                    ctx.mouseUp()
                    vars.sliderValue shouldBe vars.sliderMin
                }

                val dragMinMax = arrayOf(floatArrayOf(0f, 1f), floatArrayOf(0f, 0f), floatArrayOf(-Float.MAX_VALUE, Float.MAX_VALUE))
                for (j in dragMinMax.indices) {

                    ctx.logDebug("Drag $j with flags = 0x%08X", j, vars.flags)

                    vars.dragValue = 0f
                    vars.dragMin = dragMinMax[j][0]
                    vars.dragMax = dragMinMax[j][1]

                    // [0,0] is equivalent to [-FLT_MAX, FLT_MAX] range
                    val unbound = (vars.dragMin == 0f && vars.dragMax == 0f) || (vars.dragMin == -Float.MAX_VALUE && vars.dragMax == Float.MAX_VALUE)

                    ctx.itemInput("Drag")
                    ctx.keyCharsReplaceEnter("-3")
                    vars.dragValue shouldBe if (clampOnInput && !unbound) vars.dragMin else -3f

                    ctx.itemInput("Drag")
                    ctx.keyCharsReplaceEnter("2")
                    vars.dragValue shouldBe if (clampOnInput && !unbound) vars.dragMax else 2f

                    // Check higher bound
                    ctx.mouseMove("Drag")
                    var valueBeforeClick = vars.dragValue
                    ctx.mouseDown() // Click will not update clamping value
                    vars.dragValue shouldBe valueBeforeClick
                    ctx.mouseMoveToPos(g.io.mousePos + Vec2(100, 0))
                    ctx.mouseUp()
                    if (unbound)
                        vars.dragValue shouldBeGreaterThan valueBeforeClick
                    else
                        vars.dragValue shouldBe valueBeforeClick

                    // Check higher to lower bound
                    valueBeforeClick = vars.dragValue
                    ctx.mouseMove("Drag")
                    ctx.mouseDragWithDelta(Vec2(-100, 0))
                    if (unbound)
                        vars.dragValue shouldBeLessThan valueBeforeClick
                    else
                        vars.dragValue shouldBe vars.dragMin

                    // Check low to high bound
                    valueBeforeClick = vars.dragValue
                    ctx.mouseMove("Drag")
                    ctx.mouseDragWithDelta(Vec2(100, 0))
                    if (unbound)
                        vars.dragValue shouldBeGreaterThan valueBeforeClick
                    else
                        vars.dragValue shouldBe vars.dragMax
                }
            }
        }
    }


    // ## Test ColorEdit4() and IsItemDeactivatedXXX() functions
    // ## Test that IsItemActivated() doesn't trigger when clicking the color button to open picker
    e.registerTest("widgets", "widgets_status_coloredit").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            dsl.window("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                val ret = ImGui.colorEdit4("Field", vars.vec4, ColorEditFlag.None.i)
                vars.status.queryInc(ret)
            }
        }
        t.testFunc = { ctx: TestContext ->
            // Accumulate return values over several frames/action into each bool
            val vars = ctx.genericVars
            val status = vars.status

            // Testing activation flag being set
            ctx.setRef("Test Window")
            ctx.itemClick("Field/##ColorButton")
            status.apply {
                assert(ret == 0 && activated == 1 && deactivated == 1 && deactivatedAfterEdit == 0 && edited == 0)
                clear()

                ctx.keyPressMap(Key.Escape)
                assert(ret == 0 && activated == 0 && deactivated == 0 && deactivatedAfterEdit == 0 && edited == 0)
                clear()
            }
        }
    }

    // ## Test InputText() and IsItemDeactivatedXXX() functions (mentioned in #2215)
    e.registerTest("widgets", "widgets_status_inputtext").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            dsl.window("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                val ret = ImGui.inputText("Field", vars.str1)
                vars.status.queryInc(ret)
                ImGui.inputText("Sibling", vars.str2)
            }
        }
        t.testFunc = { ctx: TestContext ->
            // Accumulate return values over several frames/action into each bool
            val vars = ctx.genericVars
            val status = vars.status

            // Testing activation flag being set
            ctx.setRef("Test Window")
            ctx.itemClick("Field")
            status.apply {
                assert(ret == 0 && activated == 1 && deactivated == 0 && deactivatedAfterEdit == 0 && edited == 0)
                clear()

                // Testing deactivated flag being set when canceling with Escape
                ctx.keyPressMap(Key.Escape)
                assert(ret == 0 && activated == 0 && deactivated == 1 && deactivatedAfterEdit == 0 && edited == 0)
                clear()

                // Testing validation with Return after editing
                ctx.itemClick("Field")
                assert(ret == 0 && activated != 0 && deactivated == 0 && deactivatedAfterEdit == 0 && edited == 0)
                clear()
                ctx.keyCharsAppend("Hello")
                assert(ret != 0 && activated == 0 && deactivated == 0 && deactivatedAfterEdit == 0 && edited >= 1)
                clear()
                ctx.keyPressMap(Key.Enter)
                assert(ret == 0 && activated == 0 && deactivated != 0 && deactivatedAfterEdit != 0 && edited == 0)
                clear()

                // Testing validation with Tab after editing
                ctx.itemClick("Field")
                ctx.keyCharsAppend(" World")
                assert(ret != 0 && activated != 0 && deactivated == 0 && deactivatedAfterEdit == 0 && edited >= 1)
                clear()
                ctx.keyPressMap(Key.Tab)
                assert(ret == 0 && activated == 0 && deactivated != 0 && deactivatedAfterEdit != 0 && edited == 0)
                clear()
            }
        }
    }

    // ## Test the IsItemDeactivatedXXX() functions (e.g. #2550, #1875)
    e.registerTest("widgets", "widgets_status_multicomponent").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            dsl.window("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                val ret = ImGui.inputFloat4("Field", vars.floatArray)
                vars.status.queryInc(ret)
            }
        }
        t.testFunc = { ctx: TestContext ->
            // Accumulate return values over several frames/action into each bool
            val vars = ctx.genericVars
            val status = vars.status

            // FIXME-TESTS: Better helper to build ids out of various type of data
            ctx.setRef("Test Window")
            var n: Int
            n = 0
            val field0: ID = hash(n, ctx.getID("Field"))
            n = 1
            val field1: ID = hash(n, ctx.getID("Field"))
            //n = 2; ImGuiID field_2 = ImHashData(&n, sizeof(n), ctx->GetID("Field"));

            status.apply {
                // Testing activation/deactivation flags
                ctx.itemClick(field0)
                assert(ret == 0 && activated == 1 && deactivated == 0 && deactivatedAfterEdit == 0)
                clear()
                ctx.keyPressMap(Key.Enter)
                assert(ret == 0 && activated == 0 && deactivated == 1 && deactivatedAfterEdit == 0)
                clear()

                // Testing validation with Return after editing
                ctx.itemClick(field0)
                clear()
                ctx.keyCharsAppend("123")
                assert(ret >= 1 && activated == 0 && deactivated == 0)
                clear()
                ctx.keyPressMap(Key.Enter)
                assert(ret == 0 && activated == 0 && deactivated == 1)
                clear()

                // Testing validation with Tab after editing
                ctx.itemClick(field0)
                ctx.keyCharsAppend("456")
                clear()
                ctx.keyPressMap(Key.Tab)
                assert(ret == 0 && activated == 1 && deactivated == 1 && deactivatedAfterEdit == 1)

                // Testing Edited flag on all components
                ctx.itemClick(field1) // FIXME-TESTS: Should not be necessary!
                ctx.itemClick(field0)
                ctx.keyCharsAppend("111")
                assert(edited >= 1)
                ctx.keyPressMap(Key.Tab)
                clear()
                ctx.keyCharsAppend("222")
                assert(edited >= 1)
                ctx.keyPressMap(Key.Tab)
                clear()
                ctx.keyCharsAppend("333")
                assert(edited >= 1)
            }
        }
    }

    // ## Test the IsItemEdited() function when input vs output format are not matching
    e.registerTest("widgets", "widgets_status_inputfloat_format_mismatch").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            dsl.window("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                val ret = ImGui.inputFloat("Field", vars::float1)
                vars.status.queryInc(ret)
            }
        }
        t.testFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            val status = vars.status

            // Input "1" which will be formatted as "1.000", make sure we don't report IsItemEdited() multiple times!
            ctx.setRef("Test Window")
            ctx.itemClick("Field")
            ctx.keyCharsAppend("1")
            status.apply {
                assert(ret == 1 && edited == 1 && activated == 1 && deactivated == 0 && deactivatedAfterEdit == 0)
                ctx.yield()
                ctx.yield()
                assert(edited == 1)
            }
        }
    }

    // ## Test that disabled Selectable has an ID but doesn't interfere with navigation
    e.registerTest("widgets", "widgets_selectable_disabled").let { t ->
        t.guiFunc = { ctx: TestContext ->
            dsl.window("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                ImGui.selectable("Selectable A")
                if (ctx.frameCount == 0)
                    ImGui.itemID shouldBe ImGui.getID("Selectable A")
                ImGui.selectable("Selectable B", false, SelectableFlag.Disabled.i)
                if (ctx.frameCount == 0)
                    ImGui.itemID shouldBe ImGui.getID("Selectable B") // Make sure B has an ID
                ImGui.selectable("Selectable C")
            }
        }
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Test Window")
            ctx.itemClick("Selectable A")
            ctx.uiContext!!.navId shouldBe ctx.getID("Selectable A")
            ctx.keyPressMap(Key.DownArrow)
            ctx.uiContext!!.navId shouldBe ctx.getID("Selectable C") // Make sure we have skipped B
        }
    }

    // ## Test that tight tab bar does not create extra drawcalls
    e.registerTest("widgets", "widgets_tabbar_drawcalls").let { t ->
        t.guiFunc = {
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                dsl.tabBar("Tab Drawcalls") {
                    for (i in 0..19)
                        dsl.tabItem("Tab $i") {}
                }
            }
        }
        t.testFunc = { ctx: TestContext ->
            val window = ImGui.findWindowByName("Test Window")!!
            ctx.windowResize("Test Window", Vec2(300))
            val drawCalls = window.drawList.cmdBuffer.size
            ctx.windowResize("Test Window", Vec2(1))
            drawCalls shouldBe window.drawList.cmdBuffer.size
        }
    }

    // ## Test order of tabs in a tab bar
    e.registerTest("widgets", "widgets_tabbar_order").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            ImGui.begin("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize)
            for (n in 0..3)
                ImGui.checkbox("Open Tab $n", vars.boolArray, n)
            if (ImGui.beginTabBar("TabBar", TabBarFlag.Reorderable.i)) {
                for (n in 0..3)
                    if (vars.boolArray[n] && ImGui.beginTabItem("Tab $n", vars.boolArray, n))
                        ImGui.endTabItem()
                ImGui.endTabBar()
            }
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->

            val g = ctx.uiContext!!
            val vars = ctx.genericVars
            ctx.setRef("Test Window")
            val tabBar = g.tabBars.getOrAddByKey(ctx.getID("TabBar")) // FIXME-TESTS: Helper function?
            tabBar shouldNotBe null
            tabBar.tabs.size shouldBe 0

            vars.boolArray.fill(true, 0, 2)
            ctx.yield()
            ctx.yield() // Important: so tab layout are correct for TabClose()
            tabBar.tabs.size shouldBe 3
            tabBar.getTabName(tabBar.tabs[0]) shouldBe "Tab 0"
            tabBar.getTabName(tabBar.tabs[1]) shouldBe "Tab 1"
            tabBar.getTabName(tabBar.tabs[2]) shouldBe "Tab 2"

            ctx.tabClose("TabBar/Tab 1")
            ctx.yield()
            ctx.yield()
            vars.boolArray[1] shouldBe false
            tabBar.tabs.size shouldBe 2
            tabBar.getTabName(tabBar.tabs[0]) shouldBe "Tab 0"
            tabBar.getTabName(tabBar.tabs[1]) shouldBe "Tab 2"

            vars.boolArray[1] = true
            ctx.yield()
            tabBar.tabs.size shouldBe 3
            tabBar.getTabName(tabBar.tabs[0]) shouldBe "Tab 0"
            tabBar.getTabName(tabBar.tabs[1]) shouldBe "Tab 2"
            tabBar.getTabName(tabBar.tabs[2]) shouldBe "Tab 1"
        }
    }

    // ## (Attempt to) Test that tab bar declares its unclipped size.
    e.registerTest("widgets", "widgets_tabbar_size").let { t ->
        class TabBarVars(var hasCloseButton: Boolean = false, var expectedWidth: Float = 0f)
        t.userData = TabBarVars()
        t.guiFunc = { ctx: TestContext ->

            val g = ctx.uiContext!!
            val vars = ctx.getUserData<TabBarVars>()

            // FIXME-TESTS: Ideally we would test variation of with/without ImGuiTabBarFlags_TabListPopupButton, but we'd need to know its width...
            ImGui.begin("Test Window", null, Wf.NoSavedSettings.i)
            ImGui.checkbox("HasCloseButton", vars::hasCloseButton)
            if (ImGui.beginTabBar("TabBar")) {
                vars.expectedWidth = 0f
                for (i in 0..2) {
                    val label = "Tab $i"
                    _b = true
                    if (ImGui.beginTabItem(label, if (vars.hasCloseButton) ::_b else null))
                        ImGui.endTabItem()
                    if (i > 0)
                        vars.expectedWidth += g.style.itemInnerSpacing.x
                    vars.expectedWidth += ImGui.tabItemCalcSize(label, vars.hasCloseButton).x
                }
                ImGui.endTabBar()
            }
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->

            val window = ImGui.findWindowByName("Test Window")!!
            val vars = ctx.getUserData<TabBarVars>()

            vars.hasCloseButton = false
            ctx.yield()
            (window.dc.cursorStartPos.x + vars.expectedWidth) shouldBe window.dc.cursorMaxPos.x

            vars.hasCloseButton = true
            ctx.yield() // BeginTabBar() will submit old size --> TabBarLayout update sizes
            ctx.yield() // BeginTabBar() will submit new size
            (window.dc.cursorStartPos.x + vars.expectedWidth) shouldBe window.dc.cursorMaxPos.x
        }
    }

    // ## Test TabItemButton behavior
    e.registerTest("widgets", "widgets_tabbar_tabitem_button").let { t ->
        class TabBarButtonVars(var lastClickedButton: Int = -1)
        t.userData = TabBarButtonVars()
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.getUserData<TabBarButtonVars>()
            ImGui.begin("Test Window", null, Wf.AlwaysAutoResize or Wf.NoSavedSettings)
            if (ImGui.beginTabBar("TabBar")) {
                if (ImGui.tabItemButton("1", TabItemFlag.None.i)) vars.lastClickedButton = 1
                if (ImGui.tabItemButton("0", TabItemFlag.None.i)) vars.lastClickedButton = 0
                if (ImGui.beginTabItem("Tab", null, TabItemFlag.None.i)) ImGui.endTabItem()
                ImGui.endTabBar()
            }
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            val vars = ctx.getUserData<TabBarButtonVars>()
            ctx.setRef("Test Window/TabBar")

            vars.lastClickedButton shouldBe -1
            ctx.itemClick("1")
            vars.lastClickedButton shouldBe 1
            ctx.itemClick("Tab")
            vars.lastClickedButton shouldBe 1
            ctx.mouseMove("0")
            ctx.mouseDown()
            vars.lastClickedButton shouldBe 1
            ctx.mouseUp()
            vars.lastClickedButton shouldBe 0
        }
    }

    // ## Test that tab items respects their Leading/Trailing position
    e.registerTest("widgets", "widgets_tabbar_tabitem_leading_trailing").let { t ->
        class TabBarLeadingTrailingVars(var windowAutoResize: Boolean = true,
                                        var tabBarFlags: TabBarFlags = TabBarFlag.None.i,
                                        var tabBar: TabBar? = null)
        t.userData = TabBarLeadingTrailingVars()
        t.guiFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!
            val vars = ctx.getUserData<TabBarLeadingTrailingVars>()
            ImGui.begin("Test Window", null, (if (vars.windowAutoResize) Wf.AlwaysAutoResize else Wf.None) or Wf.NoSavedSettings)
            ImGui.checkbox("ImGuiWindowFlags_AlwaysAutoResize", vars::windowAutoResize)
            if (ImGui.beginTabBar("TabBar", vars.tabBarFlags)) {
                vars.tabBar = g.currentTabBar
                if (ImGui.beginTabItem("Trailing", null, TabItemFlag.Trailing.i)) ImGui.endTabItem() // Intentionally submit Trailing tab early and Leading tabs at the end
                if (ImGui.beginTabItem("Tab 0", null, TabItemFlag.None.i)) ImGui.endTabItem()
                if (ImGui.beginTabItem("Tab 1", null, TabItemFlag.None.i)) ImGui.endTabItem()
                if (ImGui.beginTabItem("Tab 2", null, TabItemFlag.None.i)) ImGui.endTabItem()
                if (ImGui.beginTabItem("Leading", null, TabItemFlag.Leading.i)) ImGui.endTabItem()
                ImGui.endTabBar()
            }
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!
            val vars = ctx.getUserData<TabBarLeadingTrailingVars>()
            vars.tabBarFlags = TabBarFlag.Reorderable or TabBarFlag.FittingPolicyResizeDown
            ctx.yield()

            ctx.setRef("Test Window/TabBar")

            // Check that tabs relative order matches what we expect (which is not the same as submission order above)
            var offsetX = -Float.MAX_VALUE
            for (tab in tabs) {
                ctx.mouseMove(tab)
                g.io.mousePos.x shouldBeGreaterThan offsetX
                offsetX = g.io.mousePos.x
            }

            // Test that "Leading" cannot be reordered over "Tab 0" and vice-versa
            ctx.itemDragAndDrop("Leading", "Tab 0")
            vars.tabBar!!.tabs[0].id shouldBe ctx.getID("Leading")
            vars.tabBar!!.tabs[1].id shouldBe ctx.getID("Tab 0")
            ctx.itemDragAndDrop("Tab 0", "Leading")
            vars.tabBar!!.tabs[0].id shouldBe ctx.getID("Leading")
            vars.tabBar!!.tabs[1].id shouldBe ctx.getID("Tab 0")

            // Test that "Trailing" cannot be reordered over "Tab 2" and vice-versa
            ctx.itemDragAndDrop("Trailing", "Tab 2")
            vars.tabBar!!.tabs[4].id shouldBe ctx.getID("Trailing")
            vars.tabBar!!.tabs[3].id shouldBe ctx.getID("Tab 2")
            ctx.itemDragAndDrop("Tab 2", "Trailing")
            vars.tabBar!!.tabs[4].id shouldBe ctx.getID("Trailing")
            vars.tabBar!!.tabs[3].id shouldBe ctx.getID("Tab 2")

            // Resize down
            vars.windowAutoResize = false
            val window = ctx.getWindowByRef("/Test Window")!!
            ctx.windowResize("Test Window", Vec2(window.size.x * 0.3f, window.size.y))
            for (i in 0..1) {
                vars.tabBarFlags = TabBarFlag.Reorderable or if (i == 0) TabBarFlag.FittingPolicyResizeDown else TabBarFlag.FittingPolicyScroll
                ctx.yield()
                ctx.itemInfo("Leading")!!.rectClipped.width shouldBeGreaterThan 1f
                ctx.itemInfo("Tab 0")!!.rectClipped.width shouldBe 0f
                ctx.itemInfo("Tab 1")!!.rectClipped.width shouldBe 0f
                ctx.itemInfo("Tab 2")!!.rectClipped.width shouldBe 0f
                ctx.itemInfo("Trailing")!!.rectClipped.width shouldBeGreaterThan 1f
            }
        }
    }

    // ## Test reordering tabs (and ImGuiTabItemFlags_NoReorder flag)
    e.registerTest("widgets", "widgets_tabbar_reorder").let { t ->
        class TabBarReorderVars(var flags: TabBarFlags = TabBarFlag.Reorderable.i, var tabBar: TabBar? = null)
        t.userData = TabBarReorderVars()
        t.guiFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!
            val vars = ctx.getUserData<TabBarReorderVars>()
            ImGui.begin("Test Window", null, Wf.AlwaysAutoResize or Wf.NoSavedSettings)
            if (ImGui.beginTabBar("TabBar", vars.flags)) {
                vars.tabBar = g.currentTabBar!!
                if (ImGui.beginTabItem("Tab 0", null, TabItemFlag.None.i)) ImGui.endTabItem()
                if (ImGui.beginTabItem("Tab 1", null, TabItemFlag.None.i)) ImGui.endTabItem()
                if (ImGui.beginTabItem("Tab 2", null, TabItemFlag.NoReorder.i)) ImGui.endTabItem()
                if (ImGui.beginTabItem("Tab 3", null, TabItemFlag.None.i)) ImGui.endTabItem()
                ImGui.endTabBar()
            }
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            val vars = ctx.getUserData<TabBarReorderVars>()

            // Reset reorderable flags to ensure tabs are in their submission order
            vars.flags = TabBarFlag.None.i
            ctx.yield()
            vars.flags = TabBarFlag.Reorderable.i
            ctx.yield()

            ctx.setRef("Test Window/TabBar")

            ctx.itemDragAndDrop("Tab 0", "Tab 1")
            vars.tabBar!!.tabs[0].id shouldBe ctx.getID("Tab 1")
            vars.tabBar!!.tabs[1].id shouldBe ctx.getID("Tab 0")

            ctx.itemDragAndDrop("Tab 0", "Tab 1")
            vars.tabBar!!.tabs[0].id shouldBe ctx.getID("Tab 0")
            vars.tabBar!!.tabs[1].id shouldBe ctx.getID("Tab 1")

            ctx.itemDragAndDrop("Tab 0", "Tab 2") // Tab 2 has no reorder flag
            ctx.itemDragAndDrop("Tab 0", "Tab 3") // Tab 2 has no reorder flag
            ctx.itemDragAndDrop("Tab 3", "Tab 2") // Tab 2 has no reorder flag
            vars.tabBar!!.tabs[1].id shouldBe ctx.getID("Tab 0")
            vars.tabBar!!.tabs[2].id shouldBe ctx.getID("Tab 2")
            vars.tabBar!!.tabs[3].id shouldBe ctx.getID("Tab 3")
        }
    }

    // ## Test recursing Tab Bars (Bug #2371)
    e.registerTest("widgets", "widgets_tabbar_recurse").let { t ->
        t.guiFunc = {
            dsl.window("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                dsl.tabBar("TabBar 0") {
                    dsl.tabItem("TabItem") {
                        // If we have many tab bars here, it will invalidate pointers from pooled tab bars
                        for (i in 0..9)
                            dsl.tabBar("Inner TabBar $i") {
                                dsl.tabItem("Inner TabItem") {}
                            }
                    }
                }
            }
        }
    }

    // ## Test BeginTabBar() append
    e.registerTest("widgets", "widgets_tabbar_append").let { t ->
        class TabBarMultipleSubmissionVars(var appendToTabBar: Boolean = false, var submitSecondTabBar: Boolean = true,
                                           val cursorAfterActiveTab: Vec2 = Vec2(), val cursorAfterFirstBeginTabBar: Vec2 = Vec2(),
                                           val cursorAfterFirstWidget: Vec2 = Vec2(), val cursorAfterSecondBeginTabBar: Vec2 = Vec2(),
                                           val cursorAfterSecondWidget: Vec2 = Vec2(), val cursorAfterSecondEndTabBar: Vec2 = Vec2())
        t.userData = TabBarMultipleSubmissionVars()
        t.guiFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!
            val vars = ctx.getUserData<TabBarMultipleSubmissionVars>()

            ImGui.begin("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize)
            ImGui.checkbox("AppendToTabBar", vars::appendToTabBar)
            if (ImGui.beginTabBar("TabBar")) {
                vars.cursorAfterFirstBeginTabBar put g.currentWindow!!.dc.cursorPos
                if (ImGui.beginTabItem("Tab 0")) {
                    ImGui.text("Tab 0")
                    ImGui.endTabItem()
                    vars.cursorAfterActiveTab put g.currentWindow!!.dc.cursorPos
                }
                if (ImGui.beginTabItem("Tab 1")) {
                    for (i in 0..2)
                        ImGui.text("Tab 1 Line $i")
                    ImGui.endTabItem()
                    vars.cursorAfterActiveTab put g.currentWindow!!.dc.cursorPos
                }
                ImGui.endTabBar()
            }
            ImGui.text("After first TabBar submission")

            vars.cursorAfterFirstWidget put g.currentWindow!!.dc.cursorPos

            if (vars.submitSecondTabBar && ImGui.beginTabBar("TabBar")) {
                vars.cursorAfterSecondBeginTabBar put g.currentWindow!!.dc.cursorPos
                if (ImGui.beginTabItem("Tab A")) {
                    ImGui.text("I'm tab A")
                    ImGui.endTabItem()
                    vars.cursorAfterActiveTab put g.currentWindow!!.dc.cursorPos
                }
                ImGui.endTabBar()
                vars.cursorAfterSecondEndTabBar put g.currentWindow!!.dc.cursorPos
            }
            ImGui.text("After second TabBar submission")
            vars.cursorAfterSecondWidget put g.currentWindow!!.dc.cursorPos

            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            val g = ctx.uiContext!!
            val vars = ctx.getUserData<TabBarMultipleSubmissionVars>()

            ctx.setRef("Test Window/TabBar")

            val lineHeight = g.fontSize + g.style.itemSpacing.y
            for (appendToTabBar in booleanArrayOf(false, true)) {
                vars.appendToTabBar = appendToTabBar
                ctx.yield()

                for (tabName in arrayOf("Tab 0", "Tab 1", "Tab A")) {
                    if (!appendToTabBar && tabName == "Tab A")
                        continue

                    ctx.itemClick(tabName)
                    ctx.yield()

                    var activeTabHeight = lineHeight
                    if (tabName == "Tab 1")
                        activeTabHeight *= 3

                    vars.cursorAfterActiveTab.y shouldBe (vars.cursorAfterFirstBeginTabBar.y + activeTabHeight)
                    vars.cursorAfterFirstWidget.y shouldBe (vars.cursorAfterActiveTab.y + lineHeight)
                    if (appendToTabBar) {
                        vars.cursorAfterSecondBeginTabBar.y shouldBe vars.cursorAfterFirstBeginTabBar.y
                        vars.cursorAfterSecondEndTabBar.y shouldBe vars.cursorAfterFirstWidget.y
                    }
                    vars.cursorAfterSecondWidget.y shouldBe (vars.cursorAfterFirstWidget.y + lineHeight)
                }
            }
        }
    }

//    #ifdef IMGUI_HAS_DOCK
//        // ## Test Dockspace within a TabItem
//        t = REGISTER_TEST("widgets", "widgets_tabbar_dockspace");
//    t->GuiFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGui::Begin("Test Window", NULL, ImGuiWindowFlags_NoSavedSettings);
//        if (ImGui::BeginTabBar("TabBar"))
//        {
//            if (ImGui::BeginTabItem("TabItem"))
//            {
//                ImGui::DockSpace(ImGui::GetID("Hello"), ImVec2(0, 0));
//                ImGui::EndTabItem();
//            }
//            ImGui::EndTabBar();
//        }
//        ImGui::End();
//    };
//    #endif

    // ## Test SetSelected on first frame of a TabItem
    e.registerTest("widgets", "widgets_tabbar_tabitem_setselected").let { t ->
        t.guiFunc = { ctx: TestContext ->
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                dsl.tabBar("tab_bar") {
                    dsl.tabItem("TabItem 0") {
                        ImGui.textUnformatted("First tab content")
                    }

                    if (ctx.frameCount >= 0) {
                        val flag = if (ctx.frameCount == 0) TabItemFlag.SetSelected else TabItemFlag.None
                        val tabItemVisible = ImGui.beginTabItem("TabItem 1", null, flag.i)
                        if (tabItemVisible) {
                            ImGui.textUnformatted("Second tab content")
                            ImGui.endTabItem()
                        }
                        if (ctx.frameCount > 0)
                            assert(tabItemVisible)
                    }
                }
            }
        }
        t.testFunc = { ctx: TestContext -> ctx.yield() }
    }

    // TODO resync
//    // ## Test various TreeNode flags
//    t = REGISTER_TEST("widgets", "widgets_treenode_behaviors");
//    struct TreeNodeTestVars { bool Reset = true, IsOpen = false, IsMultiSelect = false; int ToggleCount = 0; ImGuiTreeNodeFlags Flags = 0; };
//    t->SetUserDataType<TreeNodeTestVars>();
//    t->GuiFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGui::SetNextWindowSize(ImVec2(300, 100), ImGuiCond_Always);
//        ImGui::Begin("Test Window", NULL, ImGuiWindowFlags_NoSavedSettings);
//
//        TreeNodeTestVars& vars = ctx->GetUserData<TreeNodeTestVars>();
//        if (vars.Reset)
//        {
//            ImGui::GetStateStorage()->SetInt(ImGui::GetID("AAA"), 0);
//            vars.ToggleCount = 0;
//        }
//        vars.Reset = false;
//        ImGui::Text("Flags: 0x%08X, MultiSelect: %d", vars.Flags, vars.IsMultiSelect);
//
//        #ifdef IMGUI_HAS_MULTI_SELECT
//            if (vars.IsMultiSelect)
//            {
//                ImGui::BeginMultiSelect(ImGuiMultiSelectFlags_None, NULL, false); // Placeholder, won't interact properly
//                ImGui::SetNextItemSelectionData(NULL);
//            }
//        #endif
//
//        vars.IsOpen = ImGui::TreeNodeEx("AAA", vars.Flags);
//        if (ImGui::IsItemToggledOpen())
//            vars.ToggleCount++;
//        if (vars.IsOpen)
//            ImGui::TreePop();
//
//        #ifdef IMGUI_HAS_MULTI_SELECT
//            if (vars.IsMultiSelect)
//                ImGui::EndMultiSelect();
//        #endif
//
//        ImGui::End();
//    };
//    t->TestFunc = [](ImGuiTestContext* ctx)
//    {
//        TreeNodeTestVars& vars = ctx->GetUserData<TreeNodeTestVars>();
//        ctx->WindowRef("Test Window");
//
//        #ifdef IMGUI_HAS_MULTI_SELECT
//            int loop_count = 2;
//        #else
//        int loop_count = 1;
//        #endif
//
//        for (int loop_n = 0; loop_n < loop_count; loop_n++)
//        {
//            vars.IsMultiSelect = (loop_n == 1);
//
//            if (!vars.IsMultiSelect) // _OpenOnArrow is implicit/automatic with MultiSelect
//                {
//                    ctx->LogInfo("## ImGuiTreeNodeFlags_None, IsMultiSelect=%d", vars.IsMultiSelect);
//                    vars.Reset = true;
//                    vars.Flags = ImGuiTreeNodeFlags_None;
//                    ctx->Yield();
//                    IM_CHECK(vars.IsOpen == false && vars.ToggleCount == 0);
//
//                    // Click on arrow
//                    ctx->MouseMove("AAA", ImGuiTestOpFlags_MoveToEdgeL);
//                    ctx->MouseDown(0);
//                    IM_CHECK_EQ(vars.IsOpen, false);
//                    ctx->MouseUp(0); // Toggle on Up with _OpenOnArrow (may change!)
//                    IM_CHECK_EQ(vars.IsOpen, true);
//                    ctx->MouseClick(0);
//                    IM_CHECK_EQ(vars.IsOpen, false);
//                    ctx->MouseDoubleClick(0);
//                    IM_CHECK_EQ(vars.IsOpen, false);
//                    IM_CHECK_EQ(vars.ToggleCount, 4);
//
//                    // Click on main section
//                    vars.ToggleCount = 0;
//                    ctx->MouseMove("AAA");
//                    ctx->MouseClick(0);
//                    IM_CHECK_EQ_NO_RET(vars.IsOpen, true);
//                    ctx->MouseClick(0);
//                    IM_CHECK_EQ_NO_RET(vars.IsOpen, false);
//                    ctx->MouseDoubleClick(0);
//                    IM_CHECK_EQ_NO_RET(vars.IsOpen, false);
//                    IM_CHECK_EQ_NO_RET(vars.ToggleCount, 4);
//                }
//
//            if (!vars.IsMultiSelect) // _OpenOnArrow is implicit/automatic with MultiSelect
//                {
//                    ctx->LogInfo("## ImGuiTreeNodeFlags_OpenOnDoubleClick, IsMultiSelect=%d", vars.IsMultiSelect);
//                    vars.Reset = true;
//                    vars.Flags = ImGuiTreeNodeFlags_OpenOnDoubleClick;
//                    ctx->Yield();
//                    IM_CHECK(vars.IsOpen == false && vars.ToggleCount == 0);
//
//                    // Click on arrow
//                    ctx->MouseMove("AAA", ImGuiTestOpFlags_MoveToEdgeL);
//                    ctx->MouseDown(0);
//                    IM_CHECK_EQ(vars.IsOpen, false);
//                    ctx->MouseUp(0);
//                    IM_CHECK_EQ(vars.IsOpen, false);
//                    ctx->MouseClick(0);
//                    IM_CHECK_EQ(vars.IsOpen, false);
//                    IM_CHECK_EQ(vars.ToggleCount, 0);
//                    ctx->MouseDoubleClick(0);
//                    IM_CHECK_EQ(vars.IsOpen, true);
//                    ctx->MouseDoubleClick(0);
//                    IM_CHECK_EQ(vars.IsOpen, false);
//                    IM_CHECK_EQ(vars.ToggleCount, 2);
//
//                    // Click on main section
//                    vars.ToggleCount = 0;
//                    ctx->MouseMove("AAA");
//                    ctx->MouseClick(0);
//                    IM_CHECK_EQ(vars.IsOpen, false);
//                    ctx->MouseClick(0);
//                    IM_CHECK_EQ(vars.IsOpen, false);
//                    IM_CHECK_EQ(vars.ToggleCount, 0);
//                    ctx->MouseDoubleClick(0);
//                    IM_CHECK_EQ(vars.IsOpen, true);
//                    ctx->MouseDoubleClick(0);
//                    IM_CHECK_EQ(vars.IsOpen, false);
//                    IM_CHECK_EQ(vars.ToggleCount, 2);
//                }
//
//            {
//                ctx->LogInfo("## ImGuiTreeNodeFlags_OpenOnArrow, IsMultiSelect=%d", vars.IsMultiSelect);
//                vars.Reset = true;
//                vars.Flags = ImGuiTreeNodeFlags_OpenOnArrow;
//                ctx->Yield();
//                IM_CHECK(vars.IsOpen == false && vars.ToggleCount == 0);
//
//                // Click on arrow
//                ctx->MouseMove("AAA", ImGuiTestOpFlags_MoveToEdgeL);
//                ctx->MouseDown(0);
//                IM_CHECK_EQ(vars.IsOpen, true);
//                ctx->MouseUp(0);
//                IM_CHECK_EQ(vars.IsOpen, true);
//                ctx->MouseClick(0);
//                IM_CHECK_EQ(vars.IsOpen, false);
//                IM_CHECK_EQ(vars.ToggleCount, 2);
//                ctx->MouseDoubleClick(0);
//                IM_CHECK_EQ(vars.IsOpen, false);
//                IM_CHECK_EQ(vars.ToggleCount, 4);
//
//                // Click on main section
//                vars.ToggleCount = 0;
//                ctx->MouseMove("AAA");
//                ctx->MouseClick(0);
//                IM_CHECK_EQ(vars.IsOpen, false);
//                ctx->MouseClick(0);
//                IM_CHECK_EQ(vars.IsOpen, false);
//                IM_CHECK_EQ(vars.ToggleCount, 0);
//                ctx->MouseDoubleClick(0);
//                IM_CHECK_EQ(vars.IsOpen, false);
//                IM_CHECK_EQ(vars.ToggleCount, 0);
//            }
//
//            {
//                ctx->LogInfo("## ImGuiTreeNodeFlags_OpenOnArrow|ImGuiTreeNodeFlags_OpenOnDoubleClick, IsMultiSelect=%d", vars.IsMultiSelect);
//                vars.Reset = true;
//                vars.Flags = ImGuiTreeNodeFlags_OpenOnArrow | ImGuiTreeNodeFlags_OpenOnDoubleClick;
//                ctx->Yield();
//                IM_CHECK(vars.IsOpen == false && vars.ToggleCount == 0);
//
//                // Click on arrow
//                ctx->MouseMove("AAA", ImGuiTestOpFlags_MoveToEdgeL);
//                ctx->MouseDown(0);
//                IM_CHECK_EQ(vars.IsOpen, true);
//                ctx->MouseUp(0);
//                ctx->MouseClick(0);
//                IM_CHECK_EQ(vars.IsOpen, false);
//                IM_CHECK_EQ(vars.ToggleCount, 2);
//                ctx->MouseDoubleClick(0);
//                IM_CHECK_EQ(vars.IsOpen, false);
//                IM_CHECK_EQ(vars.ToggleCount, 4);
//
//                // Click on main section
//                vars.ToggleCount = 0;
//                ctx->MouseMove("AAA");
//                ctx->MouseClick(0);
//                IM_CHECK_EQ(vars.IsOpen, false);
//                ctx->MouseClick(0);
//                IM_CHECK_EQ(vars.IsOpen, false);
//                IM_CHECK_EQ(vars.ToggleCount, 0);
//                ctx->MouseDoubleClick(0);
//                IM_CHECK_EQ(vars.IsOpen, true);
//                ctx->MouseDoubleClick(0);
//                IM_CHECK_EQ(vars.IsOpen, false);
//                IM_CHECK_EQ(vars.ToggleCount, 2);
//            }
//        }
//    };

    // ## Test ImGuiTreeNodeFlags_SpanAvailWidth and ImGuiTreeNodeFlags_SpanFullWidth flags
    e.registerTest("widgets", "widgets_treenode_span_width").let { t ->
        t.guiFunc = {
            ImGui.setNextWindowSize(Vec2(300, 100), Cond.Always)
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                val window = ImGui.currentWindow

                ImGui.setNextItemOpen(true)
                if (ImGui.treeNodeEx("Parent")) {
                    // Interaction rect does not span entire width of work area.
                    window.dc.lastItemRect.max.x shouldBeLessThan window.workRect.max.x
                    // But it starts at very beginning of WorkRect for first tree level.
                    window.dc.lastItemRect.min.x shouldBe window.workRect.min.x
                    ImGui.setNextItemOpen(true)
                    if (ImGui.treeNodeEx("Regular")) {
                        // Interaction rect does not span entire width of work area.
                        window.dc.lastItemRect.max.x shouldBeLessThan window.workRect.max.x
                        window.dc.lastItemRect.min.x shouldBeGreaterThan window.workRect.min.x
                        ImGui.treePop()
                    }
                    ImGui.setNextItemOpen(true)
                    if (ImGui.treeNodeEx("SpanAvailWidth", TreeNodeFlag.SpanAvailWidth.i)) {
                        // Interaction rect matches visible frame rect
                        assert(window.dc.lastItemStatusFlags has ItemStatusFlag.HasDisplayRect)
                        window.dc.lastItemDisplayRect.min shouldBe window.dc.lastItemRect.min
                        window.dc.lastItemDisplayRect.max shouldBe window.dc.lastItemRect.max
                        // Interaction rect extends to the end of the available area.
                        window.dc.lastItemRect.max.x shouldBe window.workRect.max.x
                        ImGui.treePop()
                    }
                    ImGui.setNextItemOpen(true)
                    if (ImGui.treeNodeEx("SpanFullWidth", TreeNodeFlag.SpanFullWidth.i)) {
                        // Interaction rect matches visible frame rect
                        assert(window.dc.lastItemStatusFlags has ItemStatusFlag.HasDisplayRect)
                        window.dc.lastItemDisplayRect.min shouldBe window.dc.lastItemRect.min
                        window.dc.lastItemDisplayRect.max shouldBe window.dc.lastItemRect.max
                        // Interaction rect extends to the end of the available area.
                        window.dc.lastItemRect.max.x shouldBe window.workRect.max.x
                        // ImGuiTreeNodeFlags_SpanFullWidth also extends interaction rect to the left.
                        window.dc.lastItemRect.min.x shouldBe window.workRect.min.x
                        ImGui.treePop()
                    }
                    ImGui.treePop()
                }
            }
        }
    }

    // ## Test PlotLines() with a single value (#2387).
    e.registerTest("widgets", "widgets_plot_lines_unexpected_input").let { t ->
        t.testFunc = {
            val values = floatArrayOf(0f)
            ImGui.plotLines("PlotLines 1", floatArrayOf())
            ImGui.plotLines("PlotLines 2", values)
            ImGui.plotLines("PlotLines 3", values)
            // FIXME-TESTS: If test did not crash - it passed. A better way to check this would be useful.
        }
    }

    // ## Test ColorEdit basic Drag and Drop
    e.registerTest("widgets", "widgets_coloredit").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            ImGui.setNextWindowSize(Vec2(300, 200))
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                ImGui.colorEdit4("ColorEdit1", vars.vec4Array[0], ColorEditFlag.None.i)
                ImGui.colorEdit4("ColorEdit2", vars.vec4Array[1], ColorEditFlag.None.i)
            }
        }
        t.testFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            vars.vec4Array[0] = Vec4(1, 0, 0, 1)
            vars.vec4Array[1] = Vec4(0, 1, 0, 1)

            ctx.setRef("Test Window")

            vars.vec4Array[0] shouldNotBe vars.vec4Array[1]
            ctx.itemDragAndDrop("ColorEdit1/##ColorButton", "ColorEdit2/##X") // FIXME-TESTS: Inner items
            vars.vec4Array[0] shouldBe vars.vec4Array[1]
        }
    }

    // ## Test BeginDragDropSource() with NULL id.
    e.registerTest("widgets", "widgets_drag_source_null_id").let { t ->
        t.userData = WidgetDragSourceNullIdData()
        t.guiFunc = { ctx: TestContext ->

            val userData = ctx.userData as WidgetDragSourceNullIdData

            dsl.window("Null ID Test", null, Wf.NoSavedSettings.i) {
                ImGui.textUnformatted("Null ID")
                userData.source = Rect(ImGui.itemRectMin, ImGui.itemRectMax).center

                dsl.dragDropSource(DragDropFlag.SourceAllowNullID.i) {
                    val magic = 0xF00
                    ImGui.setDragDropPayload("MAGIC", magic)
                }
                ImGui.textUnformatted("Drop Here")
                userData.destination = Rect(ImGui.itemRectMin, ImGui.itemRectMax).center

                dsl.dragDropTarget {
                    ImGui.acceptDragDropPayload("MAGIC")?.let { payload ->
                        userData.dropped = true
                        (payload.data as Int) shouldBe 0xF00
                    }
                }
            }
        }
        t.testFunc = { ctx: TestContext ->

            val userData = ctx.userData as WidgetDragSourceNullIdData

            // ImGui::TextUnformatted() does not have an ID therefore we can not use ctx->ItemDragAndDrop() as that refers
            // to items by their ID.
            ctx.mouseMoveToPos(userData.source)
            ctx.sleepShort()
            ctx.mouseDown(0)

            ctx.mouseMoveToPos(userData.destination)
            ctx.sleepShort()
            ctx.mouseUp(0)

            userData.dropped shouldBe true
        }
    }

    // ## Test overlapping drag and drop targets. The drag and drop system always prioritize the smaller target.
    e.registerTest("widgets", "widgets_drag_overlapping_targets").let { t ->
        t.guiFunc = { ctx: TestContext ->

            ImGui.begin("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize)

            ImGui.button("Drag")
            if (ImGui.beginDragDropSource()) {
                val value = 0xF00D
                ImGui.setDragDropPayload("_TEST_VALUE", value)
                ImGui.endDragDropSource()
            }

            val renderButton = { ctx: TestContext, name: String, pos: Vec2, size: Vec2 ->
                ImGui.cursorScreenPos = pos
                ImGui.button(name, size)
                if (ImGui.beginDragDropTarget()) {
                    ImGui.acceptDragDropPayload("_TEST_VALUE")?.let {
                        ctx.genericVars.id = ImGui.itemID
                    }
                    ImGui.endDragDropTarget()
                }
            }

            // Render small button over big one
            val pos = ImGui.cursorScreenPos
            renderButton(ctx, "Big1", pos, Vec2(100))
            renderButton(ctx, "Small1", pos + 25, Vec2(50))

            // Render small button over small one
            renderButton(ctx, "Small2", pos + Vec2(0, 110) + 25, Vec2(50))
            renderButton(ctx, "Big2", pos + Vec2(0, 110), Vec2(100))

            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->

            ctx.setRef("Test Window")

            ctx.genericVars.id = 0
            ctx.itemDragAndDrop("Drag", "Small1")
            CHECK(ctx.genericVars.id == ctx.getID("Small1"))

            ctx.genericVars.id = 0
            ctx.itemDragAndDrop("Drag", "Small2")
            CHECK(ctx.genericVars.id == ctx.getID("Small2"))
        }
    }

    // ## Test drag sources with _SourceNoPreviewTooltip flag not producing a tooltip.
    e.registerTest("widgets", "widgets_drag_no_preview_tooltip").let { t ->
        t.guiFunc = { ctx: TestContext ->

            ImGui.begin("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize)

            val createDragDropSource = { flags: DragDropFlags ->
                if (ImGui.beginDragDropSource(flags)) {
                    val value = 0xF00D
                    ImGui.setDragDropPayload("_TEST_VALUE", value)
                    ImGui.endDragDropSource()
                }
            }

            ImGui.button("Drag")
            createDragDropSource(DragDropFlag.SourceNoPreviewTooltip.i)

            ImGui.button("Drag Extern")
            if (ImGui.isItemClicked())
                createDragDropSource(DragDropFlag.SourceNoPreviewTooltip or DragDropFlag.SourceExtern)

            ImGui.button("Drop")
            if (ImGui.beginDragDropTarget()) {
                ImGui.acceptDragDropPayload("_TEST_VALUE")
                ImGui.endDragDropTarget()
            }

            val g = ctx.uiContext!!
            val tooltip = ctx.getWindowByRef("##Tooltip_%02d".format(g.tooltipOverrideCount))
            ctx.genericVars.bool1 = ctx.genericVars.bool1 || g.tooltipOverrideCount != 0
            ctx.genericVars.bool1 = ctx.genericVars.bool1 || (tooltip != null && (tooltip.active || tooltip.wasActive))

            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Test Window")
            ctx.itemDragAndDrop("Drag", "Drop")
            ctx.genericVars.bool1 shouldBe false
            ctx.itemDragAndDrop("Drag Extern", "Drop")
            ctx.genericVars.bool1 shouldBe false
        }
    }

    // ## Test long text rendering by TextUnformatted().
    e.registerTest("widgets", "widgets_text_unformatted_long").let { t ->
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Dear ImGui Demo")
            ctx.menuCheck("Examples/Long text display")
            ctx.setRef("Example: Long text display")
            ctx.itemClick("Add 1000 lines")
            ctx.sleepShort()

            val title = "/Example: Long text display\\/Log_%08X".format(ctx.getID("Log"))
            val logPanel = ctx.getWindowByRef(title)!!
//            assert(logPanel != null)
            logPanel setScrollY logPanel.scrollMax.y
            ctx.sleepShort()
            ctx.itemClick("Clear")
            // FIXME-TESTS: A bit of extra testing that will be possible once tomato problem is solved.
            // ctx->ComboClick("Test type/Single call to TextUnformatted()");
            // ctx->ComboClick("Test type/Multiple calls to Text(), clipped");
            // ctx->ComboClick("Test type/Multiple calls to Text(), not clipped (slow)");
            ctx.windowClose("")
        }
    }

    // ## Test menu appending.
    e.registerTest("widgets", "widgets_menu_append").let { t ->
        t.guiFunc = { ctx: TestContext ->

            ImGui.begin("Append Menus", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize or Wf.MenuBar)
            ImGui.beginMenuBar()

            // Menu that we will append to.
            if (ImGui.beginMenu("First Menu")) {
                ImGui.menuItem("1 First")
                if (ImGui.beginMenu("Second Menu")) {
                    ImGui.menuItem("2 First")
                    ImGui.endMenu()
                }
                ImGui.endMenu()
            }

            // Append to first menu.
            if (ImGui.beginMenu("First Menu")) {
                if (ImGui.menuItem("1 Second"))
                    ctx.genericVars.bool1 = true
                if (ImGui.beginMenu("Second Menu")) {
                    ImGui.menuItem("2 Second")
                    ImGui.endMenu()
                }
                ImGui.endMenu()
            }

            ImGui.endMenuBar()
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Append Menus")
            ctx.menuClick("First Menu")
            ctx.menuClick("First Menu/1 First")
            ctx.genericVars.bool1 shouldBe false
            ctx.menuClick("First Menu/1 Second")
            ctx.genericVars.bool1 shouldBe true
            ctx.menuClick("First Menu/Second Menu/2 First")
            ctx.menuClick("First Menu/Second Menu/2 Second")
        }
    }

    // ## Test main menubar appending.
    e.registerTest("widgets", "widgets_main_menubar_append").let { t ->
        t.guiFunc = { ctx: TestContext ->
            // Menu that we will append to.
            if (ImGui.beginMainMenuBar()) {
                if (ImGui.beginMenu("First Menu"))
                    ImGui.endMenu()
                ImGui.endMenuBar()
            }

            // Append to first menu.
            if (ImGui.beginMainMenuBar()) {
                if (ImGui.beginMenu("Second Menu")) {
                    if (ImGui.menuItem("Second"))
                        ctx.genericVars.bool1 = true
                    ImGui.endMenu()
                }
                ImGui.endMainMenuBar()
            }

            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("##MainMenuBar")
            ctx.menuClick("Second Menu/Second")
            ctx.genericVars.bool1 shouldBe true
        }
    }

    // TODO resync
//    #ifdef IMGUI_HAS_MULTI_SELECT
//            // ## Test MultiSelect API
//            struct ExampleSelection
//            {
//                ImGuiStorage                        Storage;
//                int                                 SelectionSize;  // Number of selected items (== number of 1 in the Storage, maintained by this class)
//                int                                 RangeRef;       // Reference/pivot item (generally last clicked item)
//
//                ExampleSelection()                  { RangeRef = 0; Clear(); }
//                void Clear()                        { Storage.Clear(); SelectionSize = 0; }
//                bool GetSelected(int n) const       { return Storage.GetInt((ImGuiID)n, 0) != 0; }
//                void SetSelected(int n, bool v)     { int* p_int = Storage.GetIntRef((ImGuiID)n, 0); if (*p_int == (int)v) return; if (v) SelectionSize++; else SelectionSize--; *p_int = (bool)v; }
//                int  GetSelectionSize() const       { return SelectionSize; }
//
//                // When using SelectAll() / SetRange() we assume that our objects ID are indices.
//                // In this demo we always store selection using indices and never in another manner (e.g. object ID or pointers).
//                // If your selection system is storing selection using object ID and you want to support Shift+Click range-selection,
//                // you will need a way to iterate from one object to another given the ID you use.
//                // You are likely to need some kind of data structure to convert 'view index' <> 'object ID'.
//                // FIXME-MULTISELECT: Would be worth providing a demo of doing this.
//                // FIXME-MULTISELECT: SetRange() is currently very inefficient since it doesn't take advantage of the fact that ImGuiStorage stores sorted key.
//
//                // FIXME-MULTISELECT: This itself is a good condition we could improve either our API or our demos
//                ImGuiMultiSelectData* BeginMultiSelect(ImGuiMultiSelectFlags flags, int items_count)
//                {
//                    ImGuiMultiSelectData* data = ImGui::BeginMultiSelect(flags, (void*)(intptr_t)RangeRef, GetSelected(RangeRef));
//                    if (data->RequestClear)     { Clear(); }
//                    if (data->RequestSelectAll) { SelectAll(items_count); }
//                    return data;
//                }
//                void EndMultiSelect(int items_count)
//                {
//                    ImGuiMultiSelectData* data = ImGui::EndMultiSelect();
//                    RangeRef = (int)(intptr_t)data->RangeSrc;
//                    if (data->RequestClear)     { Clear(); }
//                    if (data->RequestSelectAll) { SelectAll(items_count); }
//                    if (data->RequestSetRange)  { SetRange((int)(intptr_t)data->RangeSrc, (int)(intptr_t)data->RangeDst, data->RangeValue ? 1 : 0); }
//                }
//                void SetRange(int n1, int n2, bool v)   { if (n2 < n1) { int tmp = n2; n2 = n1; n1 = tmp; } for (int n = n1; n <= n2; n++) SetSelected(n, v); }
//                void SelectAll(int count)               { Storage.Data.resize(count); for (int idx = 0; idx < count; idx++) Storage.Data[idx] = ImGuiStorage::ImGuiStoragePair((ImGuiID)idx, 1); SelectionSize = count; } // This could be using SetRange(), but it this way is faster.
//            };
//    struct MultiSelectTestVars
//            {
//                ExampleSelection    Selection;
//            };
//    auto multiselect_guifunc = [](ImGuiTestContext* ctx)
//    {
//        MultiSelectTestVars& vars = ctx->GetUserData<MultiSelectTestVars>();
//        ExampleSelection& selection = vars.Selection;
//
//        const int ITEMS_COUNT = 100;
//        ImGui::Begin("Test Window", NULL, ImGuiWindowFlags_NoSavedSettings | ImGuiWindowFlags_AlwaysAutoResize);
//        ImGui::Text("(Size = %d items)", selection.SelectionSize);
//        ImGui::Text("(RangeRef = %04d)", selection.RangeRef);
//        ImGui::Separator();
//
//        ImGuiMultiSelectData* multi_select_data = ImGui::BeginMultiSelect(0, (void*)(intptr_t)selection.RangeRef, selection.GetSelected(selection.RangeRef));
//        if (multi_select_data->RequestClear)     { selection.Clear(); }
//        if (multi_select_data->RequestSelectAll) { selection.SelectAll(ITEMS_COUNT); }
//
//        if (ctx->Test->ArgVariant == 1)
//        ImGui::PushStyleVar(ImGuiStyleVar_ItemSpacing, ImVec2(ImGui::GetStyle().ItemSpacing.x, 0.0f));
//
//        ImGuiListClipper clipper(ITEMS_COUNT);
//        while (clipper.Step())
//        {
//            if (clipper.DisplayStart > selection.RangeRef)
//                multi_select_data->RangeSrcPassedBy = true;
//            for (int n = clipper.DisplayStart; n < clipper.DisplayEnd; n++)
//            {
//                Str64f label("Object %04d", n);
//                bool item_is_selected = selection.GetSelected(n);
//
//                ImGui::SetNextItemSelectionData((void*)(intptr_t)n);
//                if (ctx->Test->ArgVariant == 0)
//                {
//                    ImGui::Selectable(label.c_str(), item_is_selected);
//                    bool toggled = ImGui::IsItemToggledSelection();
//                    if (toggled)
//                        selection.SetSelected(n, !item_is_selected);
//                }
//                else if (ctx->Test->ArgVariant == 1)
//                {
//                    ImGuiTreeNodeFlags flags = ImGuiTreeNodeFlags_SpanAvailWidth;
//                    flags |= ImGuiTreeNodeFlags_OpenOnDoubleClick | ImGuiTreeNodeFlags_OpenOnArrow;
//                    if (item_is_selected)
//                        flags |= ImGuiTreeNodeFlags_Selected;
//                    if (ImGui::TreeNodeEx(label.c_str(), flags))
//                        ImGui::TreePop();
//                    if (ImGui::IsItemToggledSelection())
//                        selection.SetSelected(n, !item_is_selected);
//                }
//            }
//        }
//
//        if (ctx->Test->ArgVariant == 1)
//        ImGui::PopStyleVar();
//
//        // Apply multi-select requests
//        multi_select_data = ImGui::EndMultiSelect();
//        selection.RangeRef = (int)(intptr_t)multi_select_data->RangeSrc;
//        if (multi_select_data->RequestClear)     { selection.Clear(); }
//        if (multi_select_data->RequestSelectAll) { selection.SelectAll(ITEMS_COUNT); }
//        if (multi_select_data->RequestSetRange)  { selection.SetRange((int)(intptr_t)multi_select_data->RangeSrc, (int)(intptr_t)multi_select_data->RangeDst, multi_select_data->RangeValue ? 1 : 0); }
//        ImGui::End();
//    };
//    auto multiselect_testfunc = [](ImGuiTestContext* ctx)
//    {
//        // We are using lots of MouseMove+MouseDown+MouseUp (instead of ItemClick) because we need to test precise MouseUp vs MouseDown reactions.
//        ImGuiContext& g = *ctx->UiContext;
//        MultiSelectTestVars& vars = ctx->GetUserData<MultiSelectTestVars>();
//        ExampleSelection& selection = vars.Selection;
//
//        ctx->WindowRef("Test Window");
//
//        selection.Clear();
//        ctx->Yield();
//        IM_CHECK_EQ(selection.SelectionSize, 0);
//
//        // Single click
//        ctx->ItemClick("Object 0000");
//        IM_CHECK_EQ(selection.SelectionSize, 1);
//        IM_CHECK_EQ(selection.GetSelected(0), true);
//
//        // Verify that click on another item alter selection on MouseDown
//        ctx->MouseMove("Object 0001");
//        ctx->MouseDown(0);
//        IM_CHECK_EQ(selection.SelectionSize, 1);
//        IM_CHECK_EQ(selection.GetSelected(1), true);
//        ctx->MouseUp(0);
//
//        // CTRL-A
//        ctx->KeyPressMap(ImGuiKey_A, ImGuiKeyModFlags_Ctrl);
//        IM_CHECK_EQ(selection.SelectionSize, 100);
//
//        // Verify that click on selected item clear other items from selection on MouseUp
//        ctx->MouseMove("Object 0001");
//        ctx->MouseDown(0);
//        IM_CHECK_EQ(selection.SelectionSize, 100);
//        ctx->MouseUp(0);
//        IM_CHECK_EQ(selection.SelectionSize, 1);
//        IM_CHECK_EQ(selection.GetSelected(1), true);
//
//        // Test SHIFT+Click
//        ctx->ItemClick("Object 0001");
//        ctx->KeyDownMap(ImGuiKey_COUNT, ImGuiKeyModFlags_Shift);
//        ctx->MouseMove("Object 0006");
//        ctx->MouseDown(0);
//        IM_CHECK_EQ(selection.SelectionSize, 6);
//        ctx->MouseUp(0);
//        ctx->KeyUpMap(ImGuiKey_COUNT, ImGuiKeyModFlags_Shift);
//
//        // Test that CTRL+A preserve RangeSrc (which was 0001)
//        ctx->KeyPressMap(ImGuiKey_A, ImGuiKeyModFlags_Ctrl);
//        IM_CHECK_EQ(selection.SelectionSize, 100);
//        ctx->KeyDownMap(ImGuiKey_COUNT, ImGuiKeyModFlags_Shift);
//        ctx->ItemClick("Object 0008");
//        ctx->KeyUpMap(ImGuiKey_COUNT, ImGuiKeyModFlags_Shift);
//        IM_CHECK_EQ(selection.SelectionSize, 8);
//
//        // Test reverse clipped SHIFT+Click
//        // FIXME-TESTS: ItemInfo query could disable clipper?
//        // FIXME-TESTS: We would need to disable clipper because it conveniently rely on cliprect which is affected by actual viewport, so ScrollToBottom() is not enough...
//        //ctx->ScrollToBottom();
//        ctx->ItemClick("Object 0030");
//        ctx->ScrollToTop();
//        ctx->KeyDownMap(ImGuiKey_COUNT, ImGuiKeyModFlags_Shift);
//        ctx->ItemClick("Object 0002");
//        ctx->KeyUpMap(ImGuiKey_COUNT, ImGuiKeyModFlags_Shift);
//        IM_CHECK_EQ(selection.SelectionSize, 29);
//
//        // Test ESC to clear selection
//        // FIXME-TESTS
//        #if 0
//        ctx->KeyPressMap(ImGuiKey_Escape);
//        ctx->Yield();
//        IM_CHECK_EQ(selection.SelectionSize, 0);
//        #endif
//
//        // Test SHIFT+Arrow
//        ctx->ItemClick("Object 0002");
//        IM_CHECK_EQ(g.NavId, ctx->GetID("Object 0002"));
//        IM_CHECK_EQ(selection.SelectionSize, 1);
//        ctx->KeyPressMap(ImGuiKey_DownArrow, ImGuiKeyModFlags_Shift);
//        ctx->KeyPressMap(ImGuiKey_DownArrow, ImGuiKeyModFlags_Shift);
//        IM_CHECK_EQ(g.NavId, ctx->GetID("Object 0004"));
//        IM_CHECK_EQ(selection.SelectionSize, 3);
//
//        // Test CTRL+Arrow
//        ctx->KeyPressMap(ImGuiKey_DownArrow, ImGuiKeyModFlags_Ctrl);
//        ctx->KeyPressMap(ImGuiKey_DownArrow, ImGuiKeyModFlags_Ctrl);
//        IM_CHECK_EQ(g.NavId, ctx->GetID("Object 0006"));
//        IM_CHECK_EQ(selection.SelectionSize, 3);
//
//        // Test SHIFT+Arrow after a gap
//        ctx->KeyPressMap(ImGuiKey_DownArrow, ImGuiKeyModFlags_Shift);
//        IM_CHECK_EQ(g.NavId, ctx->GetID("Object 0007"));
//        IM_CHECK_EQ(selection.SelectionSize, 6);
//
//        // Test SHIFT+Arrow reducing selection
//        ctx->KeyPressMap(ImGuiKey_UpArrow, ImGuiKeyModFlags_Shift);
//        IM_CHECK_EQ(g.NavId, ctx->GetID("Object 0006"));
//        IM_CHECK_EQ(selection.SelectionSize, 5);
//
//        // Test CTRL+Shift+Arrow moving or appending without reducing selection
//        ctx->KeyPressMap(ImGuiKey_UpArrow, ImGuiKeyModFlags_Ctrl | ImGuiKeyModFlags_Shift, 4);
//        IM_CHECK_EQ(g.NavId, ctx->GetID("Object 0002"));
//        IM_CHECK_EQ(selection.SelectionSize, 5);
//
//        // Test SHIFT+Arrow replacing selection
//        ctx->KeyPressMap(ImGuiKey_UpArrow, ImGuiKeyModFlags_Shift);
//        IM_CHECK_EQ(g.NavId, ctx->GetID("Object 0001"));
//        IM_CHECK_EQ(selection.SelectionSize, 2);
//
//        // Test Arrow replacing selection
//        ctx->KeyPressMap(ImGuiKey_DownArrow);
//        IM_CHECK_EQ(g.NavId, ctx->GetID("Object 0002"));
//        IM_CHECK_EQ(selection.SelectionSize, 1);
//        IM_CHECK_EQ(selection.GetSelected(2), true);
//
//        // Test Home/End
//        ctx->KeyPressMap(ImGuiKey_Home);
//        IM_CHECK_EQ(g.NavId, ctx->GetID("Object 0000"));
//        IM_CHECK_EQ(selection.SelectionSize, 1);
//        IM_CHECK_EQ(selection.GetSelected(0), true);
//        ctx->KeyPressMap(ImGuiKey_End);
//        IM_CHECK_EQ(g.NavId, ctx->GetID("Object 0099"));
//        IM_CHECK_EQ(selection.SelectionSize, 1);
//        IM_CHECK_EQ(selection.GetSelected(99), true); // Would break if clipped by viewport
//        ctx->KeyPressMap(ImGuiKey_Home, ImGuiKeyModFlags_Ctrl);
//        IM_CHECK_EQ(g.NavId, ctx->GetID("Object 0000"));
//        IM_CHECK_EQ(selection.SelectionSize, 1);
//        IM_CHECK_EQ(selection.GetSelected(99), true);
//        ctx->KeyPressMap(ImGuiKey_Home);
//        IM_CHECK_EQ(selection.SelectionSize, 1);
//        IM_CHECK_EQ(selection.GetSelected(99), true); // FIXME: A Home/End/PageUp/PageDown leading to same target doesn't trigger JustMovedTo, may be reasonable.
//        ctx->KeyPressMap(ImGuiKey_Space);
//        IM_CHECK_EQ(selection.SelectionSize, 1);
//        IM_CHECK_EQ(selection.GetSelected(0), true);
//        ctx->KeyPressMap(ImGuiKey_End, ImGuiKeyModFlags_Shift);
//        IM_CHECK_EQ(g.NavId, ctx->GetID("Object 0099"));
//        IM_CHECK_EQ(selection.SelectionSize, 100);
//    };
//
//    t = REGISTER_TEST("widgets", "widgets_multiselect_1_selectables");
//    t->SetUserDataType<MultiSelectTestVars>();
//    t->GuiFunc = multiselect_guifunc;
//    t->TestFunc = multiselect_testfunc;
//    t->ArgVariant = 0;
//
//    t = REGISTER_TEST("widgets", "widgets_multiselect_2_treenode");
//    t->SetUserDataType<MultiSelectTestVars>();
//    t->GuiFunc = multiselect_guifunc;
//    t->TestFunc = multiselect_testfunc;
//    t->ArgVariant = 1;
//    #endif

    val widgetsOverlappingDropTargetsGui = { ctx: TestContext ->

        ImGui.begin("Overlapping Drop Targets", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize)
        ImGui.button("Drag")
        if (ImGui.beginDragDropSource()) {
            val value = 0xF00D
            ImGui.setDragDropPayload("value", value)
            ImGui.endDragDropSource()
        }

        val renderBigButton = { ctx: TestContext ->
            ImGui.button("Big", Vec2(100))
            if (ImGui.beginDragDropTarget()) {
                ImGui.acceptDragDropPayload("value")?.let { _ ->
                    ctx.genericVars.int1 = 0xBAD
                }
                ImGui.endDragDropTarget()
            }
        }

        val renderSmallButton = { ctx: TestContext ->
            ImGui.button("Small", Vec2(50))
            if (ImGui.beginDragDropTarget()) {
                ImGui.acceptDragDropPayload("value")?.let { payload ->
                    ctx.genericVars.int1 = payload.data as Int
                }
                ImGui.endDragDropTarget()
            }
        }

        if (ctx.test!!.argVariant == 0) {
            // Render small button over big one.
            renderBigButton(ctx)
            ImGui.cursorPos = ImGui.cursorPos + Vec2(25, -75)
            renderSmallButton(ctx)
        } else {
            // Render small button over small one.
            val pos = ImGui.cursorPos
            ImGui.cursorPos = pos + 25f
            renderSmallButton(ctx)
            ImGui.cursorPos = pos
            renderBigButton(ctx)
        }
        ImGui.end()
    }
    val widgetsOverlappingDropTargetsTest = { ctx: TestContext ->
        ctx.setRef("Overlapping Drop Targets")
        ctx.mouseMove("Drag")
        ctx.itemDragAndDrop("Drag", "Small")
        CHECK(ctx.genericVars.int1 == 0xF00D)
    }

    // ## Test overlapping drag and drop targets. Small area is on the top.
    e.registerTest("widgets", "widgets_overlapping_drop_targets_1").let { t ->
        t.guiFunc = widgetsOverlappingDropTargetsGui
        t.testFunc = widgetsOverlappingDropTargetsTest
        t.argVariant = 0
    }
    // ## Test overlapping drag and drop targets. Small area is on the bottom.
    e.registerTest("widgets", "widgets_overlapping_drop_targets_2").let { t ->
        t.guiFunc = widgetsOverlappingDropTargetsGui
        t.testFunc = widgetsOverlappingDropTargetsTest
        t.argVariant = 1
    }

    // ## Test SetKeyboardFocusHere()
    e.registerTest("widgets", "widgets_set_keyboard_focus_here").let { t ->
        t.guiFunc = { ctx: TestContext ->

            ImGui.setNextWindowSize(Vec2(300, 200), Cond.Appearing)
            ImGui.begin("Test Window", null, Wf.NoSavedSettings.i)

            val vars = ctx.genericVars
            when (vars.step) {
                1 -> {
                    ImGui.setKeyboardFocusHere()
                    ImGui.inputText("Text1", vars.str1)
                    vars.status.querySet()
                }
                2 -> {
                    ImGui.inputText("Text2", vars.str1)
                    vars.status.querySet()
                    ImGui.setKeyboardFocusHere(-1)
                }
            }
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->

            val g = ctx.uiContext!!
            ctx.setRef("Test Window")

            // Test focusing next item with SetKeyboardFocusHere(0)
            val vars = ctx.genericVars
            vars.step = 1
            ctx.yield()
            g.activeId shouldBe 0
            vars.status.activated shouldBe 0
            ctx.yield()
            g.activeId shouldBe ctx.getID("Text1")
            vars.status.activated shouldBe 1

            // Test that ActiveID gets cleared when not alive
            vars.step = 0
            ctx.yield()
            ctx.yield()
            g.activeId shouldBe 0

            // Test focusing previous item with SetKeyboardFocusHere(-1)
            vars.step = 2
            ctx.yield()
            g.activeId shouldBe 0
            vars.status.activated shouldBe 0
            ctx.yield()
            g.activeId shouldBe ctx.getID("Text2")
            vars.status.activated shouldBe 1
        }
    }

    // ## Test sliders with inverted ranges. TODO resync
//    e.registerTest("widgets", "widgets_slider_ranges").let { t ->
//        t.guiFunc = { ctx: TestContext ->
//
//            val sliderScalar = { label: String, ctx: TestContext, dataType: DataType, invert: Boolean, valP: KMutableProperty0<Number> ->
//                char min_max[16];
//                void* min_p = min_max;
//                void* max_p = min_max + 8;
//                int& range_flags = ctx->GenericVars.IntArray[data_type];
//                GetDataTypeRanges(data_type, invert, min_p, max_p);
//                ImGui::SliderScalar(label, data_type, val_p, min_p, max_p);
//                if (invert)
//                    ImSwap(*(uint64_t*)min_p, *(uint64_t*)max_p); // Swap back
//                if (ImGui::DataTypeCompare(data_type, val_p, min_p) < 0 || ImGui::DataTypeCompare(data_type, val_p, max_p) > 0)
//                    range_flags |= 1; // Out of range
//                if (ImGui::DataTypeCompare(data_type, val_p, min_p) > 0 && ImGui::DataTypeCompare(data_type, val_p, max_p) < 0)
//                    range_flags |= 2; // Middle of range
//            }
//
//            ImGui.setNextWindowSize(Vec2(300, 200), Cond.Appearing)
//            ImGui.begin("Test Window", null, Wf.NoSavedSettings.i)
////            ImGui.sliderScalar("Slider", dataType, valP, minP, maxP) TODO
//            ImGui.end()
//        }
//        t.testFunc = { ctx: TestContext ->
//
//            val valP = ctx.genericVars::number0
//            val minP = ctx.genericVars::number1
//            val maxP = ctx.genericVars::number2
//            ctx.windowRef("Test Window")
//
//            for (invertRange in 0..1) {
//                for (data_type in 0 until DataType.Count.ordinal) {
//                    val dataType = DataType.values()[data_type]
//                    ctx.genericVars.dataType = dataType
//                    ctx.genericVars.str1.fill(0.b, 0, 24)
//                    when (dataType) {
//                        DataType.Byte -> {
//                            minP.set(Byte.MIN_VALUE)
//                            maxP.set(Byte.MAX_VALUE)
//                        }
//                        DataType.Ubyte -> {
//                            minP.set(Ubyte.MIN)
//                            maxP.set(Ubyte.MAX)
//                        }
//                        DataType.Short -> {
//                            minP.set(Short.MIN_VALUE)
//                            maxP.set(Short.MAX_VALUE)
//                        }
//                        DataType.Ushort -> {
//                            minP.set(Ushort.MIN)
//                            maxP.set(Ushort.MAX)
//                        }
//                        DataType.Int -> {
//                            minP.set(Int.MIN_VALUE / 2)
//                            maxP.set(Int.MAX_VALUE / 2)
//                        }
//                        DataType.Uint -> {
//                            minP.set(Uint.MIN)
//                            maxP.set(Uint.MAX / 2)
//                        }
//                        DataType.Long -> {
//                            minP.set(Long.MIN_VALUE / 2)
//                            maxP.set(Long.MAX_VALUE / 2)
//                        }
//                        DataType.Ulong -> {
//                            minP.set(Ulong.MIN)
//                            maxP.set(Ulong.MAX / 2)
//                        }
//                        DataType.Float -> {
//                            minP.set(-999999999.0f)  // Floating point types do not use their min/max supported values because widget
//                            maxP.set(+999999999.0f)  // to display them due to lossy RoundScalarWithFormatT().
//                        }
//                        DataType.Double -> {
//                            minP.set(-999999999.0)
//                            maxP.set(+999999999.0)
//                        }
//                    }
//
//                    if (invertRange.bool) { // Binary swap
//                        val tmp = minP()
//                        minP.set(maxP())
//                        maxP.set(tmp)
//                    }
//                    ctx.yield()
//
//                    ctx.mouseMove("Slider")
//                    ctx.mouseDown()
//                    ctx.mouseMove("Slider", TestOpFlag.MoveToEdgeL.i)
//                    ctx.mouseUp()
//
//                    ctx.logInfo("## DataType: ${dataType.name_}, Inverted: $invertRange, min = ${minP()}, max = ${maxP()}, val = ${valP()}")
////                    IM_CHECK(memcmp(val_p, min_p, data_type_info->Size) == 0);
//
//                    ctx.mouseMove("Slider")
//                    ctx.mouseDown()
//                    ctx.mouseMove("Slider", TestOpFlag.MoveToEdgeR.i)
//                    ctx.mouseUp()
//
//                    ctx.logInfo("## DataType: ${dataType.name_}, Inverted: $invertRange, min = ${minP()}, max = ${maxP()}, val = ${valP()}")
////                    IM_CHECK(memcmp(val_p, max_p, data_type_info->Size) == 0);
//                }
//            }
//        }
//    }

    // ## Test tooltip positioning in various conditions.
    e.registerTest("widgets", "widgets_tooltip_positioning").let { t ->
        class TooltipPosVars(val size: Vec2 = Vec2(50))
        t.userData = TooltipPosVars()
        t.guiFunc = { ctx: TestContext ->

            val vars = ctx.getUserData<TooltipPosVars>()

            ImGui.begin("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize or Wf.NoNav)
            if (ctx.runFlags has TestRunFlag.GuiFuncOnly)
                ImGui.dragVec2("Tooltip Size", vars.size)
            ImGui.button("HoverMe", Vec2(100, 0))
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip()
                ImGui.invisibleButton("Space", vars.size)

                // Debug Controls
                if (ctx.runFlags has TestRunFlag.GuiFuncOnly) {
                    val step = ctx.uiContext!!.io.deltaTime * 500f
                    if (ImGui.isKeyDown(ImGui.getKeyIndex(Key.UpArrow.i))) vars.size.y -= step
                    if (ImGui.isKeyDown(ImGui.getKeyIndex(Key.DownArrow.i))) vars.size.y += step
                    if (ImGui.isKeyDown(ImGui.getKeyIndex(Key.LeftArrow.i))) vars.size.x -= step
                    if (ImGui.isKeyDown(ImGui.getKeyIndex(Key.RightArrow.i))) vars.size.x += step
                }
                ImGui.endTooltip()
            }
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->

            val g = ctx.uiContext!!
            val vars = ctx.getUserData<TooltipPosVars>()

            ctx.setRef("Test Window")
            ctx.mouseMove("HoverMe")       // Force tooltip creation so we can grab the pointer
            val tooltip = ctx.getWindowByRef("##Tooltip_00")!!

            val viewportPos = ctx.mainViewportPos
            val viewportSize = ctx.mainViewportSize

            class TestData(
                    val pos: Vec2,             // Window position
                    val pivot: Vec2,           // Window position pivot
                    val dirSmall: Dir,      // Expected default tooltip location
                    val dirBigH: Dir,       // Expected location when tooltip is as wide as viewport
                    val dirBigV: Dir)       // Expected location when tooltip is as high as viewport

            // Test tooltip positioning around viewport corners // TODO static
            val testCases = arrayOf(
                    // [0] Top-left corner
                    TestData(viewportPos, Vec2(), Dir.Right, Dir.Down, Dir.Right),
                    // [1] Top edge
                    TestData(viewportPos + Vec2(viewportSize.x * 0.5f, 0f), Vec2(0.5f, 0f), Dir.Right, Dir.Down, Dir.Right),
                    // [2] Top-right corner
                    TestData(viewportPos + Vec2(viewportSize.x, 0f), Vec2(1f, 0f), Dir.Down, Dir.Down, Dir.Left),
                    // [3] Right edge
                    TestData(viewportPos + Vec2(viewportSize.x, viewportSize.y * 0.5f), Vec2(1f, 0.5f), Dir.Down, Dir.Down, Dir.Left),
                    // [4] Bottom-right corner
                    TestData(viewportPos + viewportSize, Vec2(1f), Dir.Up, Dir.Up, Dir.Left),
                    // [5] Bottom edge
                    TestData(viewportPos + Vec2(viewportSize.x * 0.5f, viewportSize.y), Vec2(0.5f, 1f), Dir.Right, Dir.Up, Dir.Right),
                    // [6] Bottom-left corner
                    TestData(viewportPos + Vec2(0f, viewportSize.y), Vec2(0f, 1f), Dir.Right, Dir.Up, Dir.Right),
                    // [7] Left edge
                    TestData(viewportPos + Vec2(0f, viewportSize.y * 0.5f), Vec2(0f, 0.5f), Dir.Right, Dir.Down, Dir.Right))

            for (testCase in testCases) {
                ctx.logInfo("## Test case ${testCases.indexOf(testCase)}")
                vars.size put 50
                ctx.windowMove(ctx.refID, testCase.pos, testCase.pivot)
                ctx.mouseMove("HoverMe")

                // Check default tooltip location
                g.hoveredIdPreviousFrame shouldBe ctx.getID("HoverMe")
                tooltip.autoPosLastDirection shouldBe testCase.dirSmall

                // Check tooltip location when it is real wide and verify that location does not change once it becomes too wide
                // First iteration: tooltip is just wide enough to fit within viewport
                // First iteration: tooltip is wider than viewport
                for (j in 0..1) {
                    vars.size.put(j * 0.25f * viewportSize.x + (viewportSize.x - (g.style.windowPadding.x + g.style.displaySafeAreaPadding.x) * 2), 50)
                    ctx.sleepNoSkip(0.1f, 1f / 60f)
                    tooltip.autoPosLastDirection shouldBe testCase.dirBigH
                }

                // Check tooltip location when it is real tall and verify that location does not change once it becomes too tall
                // First iteration: tooltip is just tall enough to fit within viewport
                // First iteration: tooltip is taller than viewport
                for (j in 0..1) {
                    vars.size.put(50, j * 0.25f * viewportSize.x + (viewportSize.y - (g.style.windowPadding.y + g.style.displaySafeAreaPadding.y) * 2))
                    ctx.sleepNoSkip(0.1f, 1f / 60f)
                    tooltip.autoPosLastDirection shouldBe testCase.dirBigV
                }

                g.hoveredIdPreviousFrame shouldBe ctx.getID("HoverMe")
            }
        }
    }
}


class WidgetDragSourceNullIdData(var dropped: Boolean = false) {
    lateinit var source: Vec2
    lateinit var destination: Vec2
}

// TODO resync
fun getDataTypeRanges(dataType: DataType, invert: Boolean, minP: KMutableProperty0<Number>, maxP: KMutableProperty0<Number>) {
    when (dataType) {
        DataType.Byte -> {
            minP.set(Byte.MIN_VALUE)
            maxP.set(Byte.MAX_VALUE)
        }
        DataType.Ubyte -> {
            minP.set(Ubyte.MIN)
            maxP.set(Ubyte.MAX)
        }
        DataType.Short -> {
            minP.set(Short.MIN_VALUE)
            maxP.set(Short.MAX_VALUE)
        }
        DataType.Ushort -> {
            minP.set(Ushort.MIN)
            maxP.set(Ushort.MAX)
        }
        DataType.Int -> {
            minP.set(Int.MIN_VALUE / 2)
            maxP.set(Int.MAX_VALUE / 2)
        }
        DataType.Uint -> {
            minP.set(Uint.MIN)
            maxP.set(Uint.MAX / 2)
        }
        DataType.Long -> {
            minP.set(Long.MIN_VALUE / 2)
            maxP.set(Long.MAX_VALUE / 2)
        }
        DataType.Ulong -> {
            minP.set(Ulong.MIN)
            maxP.set(Ulong.MAX / 2)
        }
        DataType.Float -> {
            minP.set(-1000000000.0f)  // Floating point types do not use their min/max supported values because widgets
            maxP.set(+1000000000.0f)  // to display them due to lossy RoundScalarWithFormatT().
        }
        DataType.Double -> {
            minP.set(-1000000000.0)
            maxP.set(+1000000000.0)
        }
        else -> error("invalid")
    }
    if (invert) {
        val tmp = minP()
        minP.set(maxP())
        maxP.set(tmp)
    }
}

private val tabs = arrayOf("Leading", "Tab 0", "Tab 1", "Tab 2", "Trailing")