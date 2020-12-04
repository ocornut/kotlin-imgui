package engine

import engine.core.*
import glm_.max
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import imgui.*
import imgui.classes.InputTextCallbackData
import imgui.classes.TextFilter
import imgui.internal.classes.Rect
import imgui.internal.sections.Axis
import kotlin.reflect.KMutableProperty0


//-------------------------------------------------------------------------
// TEST ENGINE: USER INTERFACE
//-------------------------------------------------------------------------
// - DrawTestLog() [internal]
// - GetVerboseLevelName() [internal]
// - ImGuiTestEngine_ShowTestGroup() [Internal]
// - ImGuiTestEngine_ShowTestWindow()
//-------------------------------------------------------------------------

fun TestEngine.drawTestLog(test: Test, isInteractive: Boolean) {
    val errorCol = COL32(255, 150, 150, 255)
    val warningCol = COL32(240, 240, 150, 255)
    val unimportantCol = COL32(190, 190, 190, 255)

    // FIXME-OPT: Split TestLog by lines so we can clip it easily.
    val log = test.testLog
//    val text = test.testLog.buffer.begin()
//    const char * text_end = test->TestLog.Buffer.end()
//    ImGui::PushStyleVar(ImGuiStyleVar_ItemSpacing, ImVec2(6.0f, 2.0f) * e->IO.DpiScale)
//    ImVector<ImGuiTestLogLineInfo>& line_info_vector = test->Status == ImGuiTestStatus_Error ? log->LineInfoError : log->LineInfo
//    ImGuiListClipper clipper
//            clipper.Begin(line_info_vector.Size)
//    while (clipper.Step()) {
//        for (int line_no = clipper.DisplayStart; line_no < clipper.DisplayEnd; line_no++)
//        {
//            ImGuiTestLogLineInfo& line_info = line_info_vector[line_no]
//            const char * line_start = text +line_info.LineOffset
//            const char * line_end = strchr (line_start, '\n')
//            if (line_end == NULL)
//                line_end = text_end
//
//            switch(line_info.Level)
//            {
//                case ImGuiTestVerboseLevel_Error :
//                ImGui::PushStyleColor(ImGuiCol_Text, error_col)
//                break
//                case ImGuiTestVerboseLevel_Warning :
//                ImGui::PushStyleColor(ImGuiCol_Text, warning_col)
//                break
//                case ImGuiTestVerboseLevel_Debug :
//                case ImGuiTestVerboseLevel_Trace :
//                ImGui::PushStyleColor(ImGuiCol_Text, unimportant_col)
//                break
//                default:
//                ImGui::PushStyleColor(ImGuiCol_Text, IM_COL32_WHITE)
//                break
//            }
//            ImGui::TextUnformatted(line_start, line_end)
//            ImGui::PopStyleColor()
//
//            ImGui::PushID(line_no)
//            if (ImGui::BeginPopupContextItem("Context", 1)) {
//                if (!ParseLineAndDrawFileOpenItemForSourceFile(e, test, line_start, line_end))
//                    ImGui::MenuItem("No options", NULL, false, false)
//                ImGui::EndPopup()
//            }
//            ImGui::PopID()
//        }
//    }
//    ImGui::PopStyleVar()
}

fun helpTooltip(desc: String) {
    if (ImGui.isItemHovered())
        ImGui.setTooltip(desc)
}

fun showTestGroup(e: TestEngine, group: TestGroup, filter: TextFilter) {

    val style = ImGui.style

    //ImGui::Text("TESTS (%d)", engine->TestsAll.Size);
    if (ImGui.button("Run All"))
        e.queueTests(group, filter.inputBuf.cStr) // FIXME: Filter func differs

    ImGui.sameLine()
    filter.draw("##filter", -1f)
    ImGui.separator()

    if (ImGui.beginChild("Tests", Vec2())) {
        ImGui.pushStyleVar(StyleVar.ItemSpacing, Vec2(6, 3) * e.io.dpiScale)
        ImGui.pushStyleVar(StyleVar.FramePadding, Vec2(4, 1) * e.io.dpiScale)
        for (n in e.testsAll.indices) {
            val test = e.testsAll[n]
            if (test.group != group)
                continue
            if (!filter.passFilter(test.name!!) && !filter.passFilter(test.category!!))
                continue

            val testContext = e.testContext!!.takeIf { it.test === test }

            ImGui.pushID(n)

            val statusColor = when (test.status) {
                TestStatus.Error -> Vec4(0.9f, 0.1f, 0.1f, 1f)
                TestStatus.Success -> Vec4(0.1f, 0.9f, 0.1f, 1f)
                TestStatus.Queued, TestStatus.Running -> when {
                    testContext?.runFlags?.has(TestRunFlag.NoTestFunc) == true -> Vec4(0.8f, 0f, 0.8f, 1f)
                    else -> Vec4(0.8f, 0.4f, 0.1f, 1f)
                }
                else -> Vec4(0.4f, 0.4f, 0.4f, 1f)
            }

            val p = Vec2(ImGui.cursorScreenPos)
            ImGui.colorButton("status", statusColor, ColorEditFlag.NoTooltip.i)
            ImGui.sameLine()
            if (test.status == TestStatus.Running)
                ImGui.renderText(p + style.framePadding + Vec2(), "|\\0/\\0-\\0\\".substring((((ImGui.frameCount) / 5) and 3) shl 1))

            var queueTest = false
            var queueGuiFunc = false
            var selectTest = false

            if (ImGui.button("Run")) {
                queueTest = true
                selectTest = true
            }
            ImGui.sameLine()

            val buf = "${test.category ?: ""}${" ".repeat(10 - (test.category?.length ?: 0))} - ${test.name}"
            if (ImGui.selectable(buf, test == e.uiSelectedTest))
                selectTest = true

            // Double-click to run test, CTRL+Double-click to run GUI function
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(MouseButton.Left))
                if (ImGui.io.keyCtrl)
                    queueGuiFunc = true
                else
                    queueTest = true

            /*if (ImGui::IsItemHovered() && test->TestLog.size() > 0)
            {
            ImGui::BeginTooltip();
            DrawTestLog(engine, test, false);
            ImGui::EndTooltip();
            }*/

            if (e.uiSelectAndScrollToTest == test)
                ImGui.setScrollHereY()

            var viewSource = false
            if (ImGui.beginPopupContextItem()) {
                selectTest = true

                if (ImGui.menuItem("Run test"))
                    queueTest = true

                val isRunningGuiFunc = testContext?.runFlags?.has(TestRunFlag.NoTestFunc) == true
                if (ImGui.menuItem("Run GUI func", selected = isRunningGuiFunc))
                    if (isRunningGuiFunc)
                        e.abort()
                    else queueGuiFunc = true
                ImGui.separator()

                val openSourceAvailable = test.sourceFile != null && e.io.srcFileOpenFunc != null
                if (openSourceAvailable) {
                    TODO()
//                    buf.setf("Open source (%s:%d)", test->SourceFileShort, test->SourceLine)
//                    if (ImGui::MenuItem(buf.c_str()))
//                        { engine ->
//                            IO.SrcFileOpenFunc(test->SourceFile, test->SourceLine, engine->IO.UserData)
//                        }
//                    if (ImGui::MenuItem("View source..."))
//                        viewSource = true
                } else {
                    ImGui.menuItem("Open source", selected = false, enabled = false)
                    ImGui.menuItem("View source", selected = false, enabled = false)
                }

                ImGui.separator()
                if (ImGui.menuItem("Copy name", selected = false))
                    ImGui.clipboardText = test.name!!

                if (ImGui.menuItem("Copy log", selected = false, enabled = test.testLog.buffer.isNotEmpty()))
                    ImGui.clipboardText = test.testLog.buffer.toString()

                if (ImGui.menuItem("Clear log", selected = false, enabled = test.testLog.buffer.isNotEmpty()))
                    test.testLog.clear()

                ImGui.endPopup()
            }

            // Process source popup
//            static ImGuiTextBuffer source_blurb
//            static int goto_line = -1
            if (viewSource) {
                TODO()
//                source_blurb.clear()
//                size_t file_size = 0
//                char * file_data = (char *) ImFileLoadToMemory (test->SourceFile, "rb", &file_size)
//                if (file_data)
//                    source_blurb.append(file_data, file_data + file_size)
//                else
//                    source_blurb.append("<Error loading sources>")
//                goto_line = (test->SourceLine+test->SourceLineEnd) / 2
//                ImGui::OpenPopup("Source")
            }
            if (ImGui.beginPopup("Source")) {
                TODO()
//                // FIXME: Local vs screen pos too messy :(
//                const ImVec2 start_pos = ImGui::GetCursorStartPos()
//                const float line_height = ImGui::GetTextLineHeight()
//                if (goto_line != -1)
//                    ImGui::SetScrollFromPosY(start_pos.y + (goto_line - 1) * line_height, 0.5f)
//                goto_line = -1
//
//                ImRect r (0.0f, test->SourceLine * line_height, ImGui::GetWindowWidth(), (test->SourceLine+1) * line_height) // SourceLineEnd is too flaky
//                ImGui::GetWindowDrawList()->AddRectFilled(ImGui::GetWindowPos()+start_pos+r.Min, ImGui::GetWindowPos()+start_pos+r.Max, IM_COL32(80, 80, 150, 150))
//
//                ImGui::TextUnformatted(source_blurb.c_str(), source_blurb.end())
//                ImGui::EndPopup()
            }

            // Process selection
            if (selectTest)
                e.uiSelectedTest = test

            // Process queuing
            if (!e.io.runningTests)
                if (queueTest)
                    e.queueTest(test, TestRunFlag.ManualRun.i)
                else if (queueGuiFunc)
                    e.queueTest(test, TestRunFlag.ManualRun or TestRunFlag.NoTestFunc)

            ImGui.popID()
        }
        ImGui.spacing()
        ImGui.popStyleVar()
        ImGui.popStyleVar()
    }
    ImGui.endChild()
}

fun TestEngine.showTestWindow(pOpen: KMutableProperty0<Boolean>? = null) {

    if (uiFocus) {
        ImGui.setNextWindowFocus()
        uiFocus = false
    }
    ImGui.begin("Dear ImGui Test Engine", pOpen)// , ImGuiWindowFlags_MenuBar);

//    #if 0
//    if (0 && ImGui::BeginMenuBar())
//    {
//        if (ImGui::BeginMenu("Options"))
//        {
//            const bool busy = ImGuiTestEngine_IsRunningTests(engine)
//            ImGui::MenuItem("Run fast", NULL, &engine->IO.ConfigRunFast, !busy)
//            ImGui::MenuItem("Run in blind context", NULL, &engine->IO.ConfigRunBlind)
//            ImGui::MenuItem("Debug break on error", NULL, &engine->IO.ConfigBreakOnError)
//            ImGui::EndMenu()
//        }
//        ImGui::EndMenuBar()
//    }
//    #endif



    // Options
    ImGui.pushStyleVar(StyleVar.FramePadding, Vec2())
    ImGui.checkbox("Fast", io::configRunFast); helpTooltip("Run tests as fast as possible (no vsync, no delay, teleport mouse, etc.).")
    ImGui.sameLine()
    ImGui.checkbox("Blind", io::configRunBlind); helpTooltip("<UNSUPPORTED>\nRun tests in a blind ui context.")
    ImGui.sameLine()
    ImGui.checkbox("Stop", io::configStopOnError); helpTooltip("Stop running tests when hitting an error.")
    ImGui.sameLine()
    ImGui.checkbox("DbgBrk", io::configBreakOnError); helpTooltip("Break in debugger when hitting an error.")
    ImGui.sameLine()
    ImGui.checkbox("KeepGUI", io::configKeepGuiFunc); helpTooltip("Keep GUI function running after test function is finished.")
    ImGui.sameLine()
    ImGui.checkbox("Refocus", io::configTakeFocusBackAfterTests); helpTooltip("Set focus back to Test window after running tests.")
    ImGui.sameLine()
    ImGui.setNextItemWidth(60f * io.dpiScale)
    _i = io.configVerboseLevel.i
    if (ImGui.dragInt("Verbose", ::_i, 0.1f, 0, TestVerboseLevel.COUNT.i - 1, io.configVerboseLevel.name))
        io.configVerboseLevelOnError = io.configVerboseLevel
    io.configVerboseLevel = TestVerboseLevel(_i)
    ImGui.sameLine()
    ImGui.setNextItemWidth(30f)
    ImGui.dragInt("Perf Stress Amount", io::perfStressAmount, 0.1f, 1, 20); helpTooltip("Increase workload of performance tests (higher means longer run).")
    ImGui.popStyleVar()
    ImGui.separator()

    // FIXME-DOGFOODING: It would be about time that we made it easier to create splitters, current low-level splitter behavior is not easy to use properly.
    // FIXME-SCROLL: When resizing either we'd like to keep scroll focus on something (e.g. last clicked item for list, bottom for log)
    // See https://github.com/ocornut/imgui/issues/319
    val availY = ImGui.contentRegionAvail.y - ImGui.style.itemSpacing.y
    val minSize0 = ImGui.frameHeight * 1.2f
    val minSize1 = ImGui.frameHeight * 1
    var logHeight = uiLogHeight
    var listHeight = (availY - uiLogHeight) max minSize0
    run {
        val window = ImGui.currentWindow
        val y = ImGui.cursorScreenPos.y + listHeight
        val splitterBb = Rect(window.workRect.min.x, y - 1, window.workRect.max.x, y + 1)
        _f = listHeight
        _f1 = logHeight
        ImGui.splitterBehavior(splitterBb, ImGui.getID("splitter"), Axis.Y, ::_f, ::_f1, minSize0, minSize1, 3f)
        uiLogHeight = logHeight
        listHeight = _f
        logHeight = _f1
//        ImGui.debugDrawItemRect()
    }

    // TESTS
    ImGui.beginChild("List", Vec2(0, listHeight), false, WindowFlag.NoScrollbar.i)
    dsl.tabBar("##Tests", TabBarFlag.NoTooltip.i) {
        dsl.tabItem("TESTS") { showTestGroup(this, TestGroup.Tests, uiFilterTests) }
        dsl.tabItem("PERFS") { showTestGroup(this, TestGroup.Perfs, uiFilterPerfs) }
    }
    ImGui.endChild()
    uiSelectAndScrollToTest = null

    // LOG
    ImGui.beginChild("Log", Vec2(0f, logHeight))
    dsl.tabBar("##tools") {
        dsl.tabItem("LOG") {
            ImGui.text(uiSelectedTest?.run { "Log for $category: $name" } ?: "N/A")
            if (ImGui.smallButton("Clear"))
                uiSelectedTest?.testLog?.clear()
            ImGui.sameLine()
            if (ImGui.smallButton("Copy to clipboard"))
                uiSelectedTest?.let { ImGui.clipboardText = it.testLog.buffer.toString() }
            ImGui.separator()

            // Quick status
            val uiContext = uiContextActive ?: uiContextVisible
            val itemHoveredId = uiContext!!.hoveredIdPreviousFrame
            val itemActiveId = uiContext.activeId
            val itemHoveredInfo = if (itemHoveredId != 0) itemLocate(itemHoveredId, "") else null
            val itemActiveInfo = if (itemActiveId != 0) itemLocate(itemActiveId, "") else null
            ImGui.text("Hovered: 0x%08X (\"${itemHoveredInfo?.debugLabel ?: ""}\") @ (%.1f,%.1f)", itemHoveredId, uiContext.io.mousePos.x, uiContext.io.mousePos.y)
            ImGui.text("Active:  0x%08X (\"${itemActiveInfo?.debugLabel ?: ""}\")", itemActiveId)

            ImGui.separator()
            ImGui.beginChild("Log")
            uiSelectedTest?.let {
                drawTestLog(it, true)
                if (ImGui.scrollY >= ImGui.scrollMaxY)
                    ImGui.setScrollHereY()
            }
            ImGui.endChild()
        }

        // Tools
        dsl.tabItem("MISC TOOLS") {
            val io = ImGui.io
            ImGui.text("%.3f ms/frame (%.1f FPS)", 1000f / io.framerate, io.framerate)
            ImGui.separator()

            ImGui.text("Tools:")
            ImGui.dragFloat("DpiScale", this.io::dpiScale, 0.005f, 0f, 0f, "%.2f")
            ImGui.checkbox("Capture Tool", captureTool::visible)
            ImGui.checkbox("Slow down whole app", ::toolSlowDown)
            ImGui.sameLine()
            ImGui.setNextItemWidth(70f * this.io.dpiScale)
            ImGui.sliderInt("##ms", ::toolSlowDownMs, 0, 400, "%d ms")

            ImGui.separator()
            ImGui.text("Configuration:")
            val filterCallback: InputTextCallback = { data: InputTextCallbackData -> data.eventChar == ',' || data.eventChar == ';' }
            ImGui.inputText("Branch/Annotation", this.io.gitBranchName, InputTextFlag.CallbackCharFilter.i, filterCallback)
            ImGui.checkboxFlags("io.ConfigFlags: NavEnableKeyboard", io::configFlags, ConfigFlag.NavEnableKeyboard.i)
            ImGui.checkboxFlags("io.ConfigFlags: NavEnableGamepad", io::configFlags, ConfigFlag.NavEnableGamepad.i)
//            if (IMGUI_HAS_DOCK)
//                ImGui.checkbox("io.ConfigDockingAlwaysTabBar", io::configDockingAlwaysTabBar)
        }

        // FIXME-TESTS: Need to be visualizing the samples/spikes.
        dsl.tabItem("PERFS TOOLS") {
            val dt1 = 1.0 / ImGui.io.framerate
            val fpsNow = 1.0 / dt1
            val dt100 = perfDeltaTime100.average
            val dt1000 = perfDeltaTime1000.average
            val dt2000 = perfDeltaTime2000.average

            //if (engine->PerfRefDeltaTime <= 0.0 && engine->PerfRefDeltaTime.IsFull())
            //    engine->PerfRefDeltaTime = dt_2000;

            ImGui.checkbox("Unthrolled", io::configNoThrottle)
            ImGui.sameLine()
            if (ImGui.button("Pick ref dt"))
                perfRefDeltaTime = dt2000

            val dtRef = perfRefDeltaTime
            ImGui.text("[ref dt]    %6.3f ms", perfRefDeltaTime * 1000)
            ImGui.text("[last 0001] %6.3f ms (%.1f FPS) ++ %6.3f ms", dt1 * 1000.0, 1.0 / dt1, (dt1 - dtRef) * 1000)
            ImGui.text("[last 0100] %6.3f ms (%.1f FPS) ++ %6.3f ms ~ converging in %.1f secs", dt100 * 1000.0, 1.0 / dt100, (dt1 - dtRef) * 1000, 100.0 / fpsNow)
            ImGui.text("[last 1000] %6.3f ms (%.1f FPS) ++ %6.3f ms ~ converging in %.1f secs", dt1000 * 1000.0, 1.0 / dt1000, (dt1 - dtRef) * 1000, 1000.0 / fpsNow)
            ImGui.text("[last 2000] %6.3f ms (%.1f FPS) ++ %6.3f ms ~ converging in %.1f secs", dt2000 * 1000.0, 1.0 / dt2000, (dt1 - dtRef) * 1000, 2000.0 / fpsNow)

            //PlotLines("Last 100", &engine->PerfDeltaTime100.Samples.Data, engine->PerfDeltaTime100.Samples.Size, engine->PerfDeltaTime100.Idx, NULL, 0.0f, dt_1000 * 1.10f, ImVec2(0.0f, GetFontSize()));
            val plotSize = Vec2(0f, ImGui.frameHeight * 3)
            val ma = perfDeltaTime500
//            ImGui.plotLines("Last 500", TODO
//                    { n -> val ma = (MovingAverageDouble) data; return (float)(ma->Samples[n] * 1000); },
//                    ma, ma->Samples.Size, 0*ma->Idx, NULL, 0.0f, (float)(ImMax(dt_100, dt_1000) * 1000.0 * 1.2f), plot_size)
        }
    }
    ImGui.endChild()

    ImGui.end()

    // Capture Tool
    captureTool.context.screenCaptureFunc = io.screenCaptureFunc
    if (captureTool.visible)
        captureTool.showCaptureToolWindow(captureTool::visible)

    // Capture a screenshot from main thread while coroutine waits
    // FIXME: Move that out of here!
    currentCaptureArgs?.let {
        captureContext.screenCaptureFunc = io.screenCaptureFunc
        if (!captureContext.captureScreenshot(it)) {
//            ImStrncpy(engine->CaptureTool.LastSaveFileName, engine->CurrentCaptureArgs->OutSavedFileName, IM_ARRAYSIZE(engine->CaptureTool.LastSaveFileName));
            currentCaptureArgs = null
        }
    }
}
