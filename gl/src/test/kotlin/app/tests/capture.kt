package app.tests

import engine.CaptureArgs
import engine.TestEngine
import engine.context.*
import engine.engine.registerTest
import glm_.vec2.Vec2
import imgui.ImGui
import imgui.Key

//-------------------------------------------------------------------------
// Tests: Capture
//-------------------------------------------------------------------------

fun registerTests_Capture(e: TestEngine) {

    e.registerTest("capture", "capture_demo_documents").let { t ->
        t.testFunc = { ctx: TestContext ->

            ctx.setRef("Dear ImGui Demo")
            ctx.menuCheck("Examples/Documents")

            ctx.setRef("Example: Documents")
            ctx.windowResize(ctx.refID, Vec2(600, 300))    // Ensure no items are clipped, because then they cant be found by item search
            ctx.itemCheck("**/Tomato")
            ctx.itemCheck("**/A Rather Long Title")
            ctx.itemClick("##tabs/Eggplant")
            ctx.setRef(ctx.getID("##tabs/Eggplant"))
            ctx.mouseMove("**/Modify")
            ctx.sleep(1f)
        }
    }

//    #if 1
    // TODO: Better position of windows.
    // TODO: Draw in custom rendering canvas
    // TODO: Select color picker mode
    // TODO: Flags document as "Modified"
    // TODO: InputText selection
    e.registerTest("capture", "capture_readme_misc").let { t ->
        t.testFunc = { ctx: TestContext ->

            val io = ImGui.io
            //ImGuiStyle& style = ImGui::GetStyle();

            ctx.setRef("Dear ImGui Demo")
            ctx.itemCloseAll("")
            ctx.menuCheck("Examples/Simple overlay")
            ctx.setRef("Example: Simple overlay")
            val windowOverlay = ctx.getWindowByRef("")!!
//        IM_CHECK(windowOverlay != NULL)

            // FIXME-TESTS: Find last newly opened window? -> cannot rely on NavWindow as menu item maybe was already checked..

            val fh = ImGui.fontSize
            var pad = fh

            ctx.setRef("Dear ImGui Demo")
            ctx.menuCheck("Examples/Custom rendering")
            ctx.setRef("Example: Custom rendering")
            ctx.windowResize("", Vec2(fh * 30))
            ctx.windowMove("", windowOverlay.rect().bl + Vec2(0f, pad))
            val windowCustomRendering = ctx.getWindowByRef("")!!
//        IM_CHECK(windowCustomRendering != NULL)

            ctx.setRef("Dear ImGui Demo")
            ctx.menuCheck("Examples/Simple layout")
            ctx.setRef("Example: Simple layout")
            ctx.windowResize("", Vec2(fh * 50, fh * 15))
            ctx.windowMove("", Vec2(pad, io.displaySize.y - pad), Vec2(0f, 1f))

            ctx.setRef("Dear ImGui Demo")
            ctx.menuCheck("Examples/Documents")
            ctx.setRef("Example: Documents")
            ctx.windowResize("", Vec2(fh * 20, fh * 27))
            ctx.windowMove("", Vec2(windowCustomRendering.pos.x + windowCustomRendering.size.x + pad, pad))

            ctx.logDebug("Setup Console window...")
            ctx.setRef("Dear ImGui Demo")
            ctx.menuCheck("Examples/Console")
            ctx.setRef("Example: Console")
            ctx.windowResize("", Vec2(fh * 40, fh * (34 - 7)))
            ctx.windowMove("", windowCustomRendering.pos + windowCustomRendering.size * Vec2(0.3f, 0.6f))
            ctx.itemClick("Clear")
            ctx.itemClick("Add Debug Text")
            ctx.itemClick("Add Debug Error")
            ctx.itemClick("Input")
            ctx.keyChars("H")
            ctx.keyPressMap(Key.Tab)
            ctx.keyCharsAppendEnter("ELP")
            ctx.keyCharsAppendEnter("hello, imgui world!")

            ctx.logDebug("Setup Demo window...")
            ctx.setRef("Dear ImGui Demo")
            ctx.windowResize("", Vec2(fh * 35, io.displaySize.y - pad * 2f))
            ctx.windowMove("", Vec2(io.displaySize.x - pad, pad), Vec2(1f, 0f))
            ctx.itemOpen("Widgets")
            ctx.itemOpen("Color\\/Picker Widgets")
            ctx.itemOpen("Layout & Scrolling")
            ctx.itemOpen("Groups")
            ctx.scrollToItemY("Layout & Scrolling", 0.8f)

            ctx.logDebug("Capture screenshot...")
            ctx.setRef("")

            val args = CaptureArgs()
            ctx.captureInitArgs(args)
            args.inPadding = pad
            ctx.captureAddWindow(args, "Dear ImGui Demo")
            ctx.captureAddWindow(args,"Example: Simple overlay")
            ctx.captureAddWindow(args,"Example: Custom rendering")
            ctx.captureAddWindow(args,"Example: Simple layout")
            ctx.captureAddWindow(args,"Example: Documents")
            ctx.captureAddWindow(args,"Example: Console")
            ctx.captureScreenshot(args)

            // Close everything
            ctx.setRef("Dear ImGui Demo")
            ctx.itemCloseAll("")
            ctx.menuUncheckAll("Examples")
            ctx.menuUncheckAll("Tools")
        }
    }
//    #endif

//    // ## Capture a screenshot displaying different supported styles.
//    t = REGISTER_TEST("capture", "capture_readme_styles");
//    t->GuiFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGuiIO& io = ImGui::GetIO();
//        ImGuiStyle& style = ImGui::GetStyle();
//        const ImGuiStyle backup_style = style;
//        const bool backup_cursor_blink = io.ConfigInputTextCursorBlink;
//
//        // Setup style
//        // FIXME-TESTS: Ideally we'd want to be able to manipulate fonts
//        ImFont* font = FindFontByName("Roboto-Medium.ttf, 16px");
//        IM_CHECK_SILENT(font != NULL);
//        ImGui::PushFont(font);
//        style.FrameRounding = style.ChildRounding = 0;
//        style.GrabRounding = 0;
//        style.FrameBorderSize = style.ChildBorderSize = 1;
//        io.ConfigInputTextCursorBlink = false;
//
//        // Show two windows
//        for (int n = 0; n < 2; n++)
//        {
//            bool open = true;
//            ImGui::SetNextWindowSize(ImVec2(300, 160), ImGuiCond_Appearing);
//            if (n == 0)
//            {
//                ImGui::StyleColorsDark(&style);
//                ImGui::Begin("Debug##Dark", &open, ImGuiWindowFlags_NoSavedSettings);
//            }
//            else
//            {
//                ImGui::StyleColorsLight(&style);
//                ImGui::Begin("Debug##Light", &open, ImGuiWindowFlags_NoSavedSettings);
//            }
//            float float_value = 0.6f;
//            ImGui::Text("Hello, world 123");
//            ImGui::Button("Save");
//            ImGui::SetNextItemWidth(194);
//            ImGui::InputText("string", ctx->GenericVars.Str1, IM_ARRAYSIZE(ctx->GenericVars.Str1));
//            ImGui::SetNextItemWidth(194);
//            ImGui::SliderFloat("float", &float_value, 0.0f, 1.0f);
//            ImGui::End();
//        }
//        ImGui::PopFont();
//
//        // Restore style
//        style = backup_style;
//        io.ConfigInputTextCursorBlink = backup_cursor_blink;
//    };
//    t->TestFunc = [](ImGuiTestContext* ctx)
//    {
//        // Capture both windows in separate captures
//        ImGuiContext& g = *ctx->UiContext;
//        for (int n = 0; n < 2; n++)
//        {
//            ImGuiWindow* window = (n == 0) ? ctx->GetWindowByRef("/Debug##Dark") : ctx->GetWindowByRef("/Debug##Light");
//            ctx->WindowRef(window);
//            ctx->ItemClick("string");
//            ctx->KeyCharsReplace("quick brown fox");
//            //ctx->KeyPressMap(ImGuiKey_End);
//            ctx->MouseMove("float");
//            ctx->MouseMoveToPos(g.IO.MousePos + ImVec2(30, -10));
//
//            ImGuiCaptureArgs args;
//            ctx->CaptureInitArgs(&args);
//            ctx->CaptureAddWindow(&args, window->Name);
//            ctx->CaptureScreenshot(&args);
//        }
//    };
//
//    t = REGISTER_TEST("capture", "capture_readme_gif");
//    t->GuiFunc = [](ImGuiTestContext* ctx)
//    {
//        ImGui::SetNextWindowSize(ImVec2(300, 160), ImGuiCond_Appearing);
//        ImGui::Begin("CaptureGif", NULL, ImGuiWindowFlags_NoSavedSettings);
//        static char string_buffer[64] = {};
//        static float float_value = 0.6f;
//        ImGui::Text("Hello, world 123");
//        ImGui::Button("Save");
//        ImGui::SetNextItemWidth(194);
//        ImGui::InputText("string", string_buffer, IM_ARRAYSIZE(string_buffer));
//        ImGui::SetNextItemWidth(194);
//        ImGui::SliderFloat("float", &float_value, 0.0f, 4.0f);
//        ImGui::End();
//    };
//    t->TestFunc = [](ImGuiTestContext* ctx)
//    {
//        ctx->WindowRef("CaptureGif");
//        ImGuiWindow* window = ctx->GetWindowByRef("/CaptureGif");
//        ImGuiCaptureArgs args;
//        ctx->CaptureInitArgs(&args);
//        ctx->CaptureAddWindow(&args, window->Name);
//        ctx->BeginCaptureGif(&args);
//        ctx->ItemInput("string");
//        ctx->KeyCharsReplace("Dear ImGui: Now with gif animations \\o/");
//        ctx->SleepShort();
//        ctx->ItemInput("float");
//        ctx->KeyCharsReplaceEnter("3.14");
//        ctx->SleepShort();
//        ctx->ItemClick("Save");
//        ctx->SleepShort();
//        ctx->EndCaptureGif(&args);
//    };
//
//    // ## Capture
//    t = IM_REGISTER_TEST(e, "capture", "capture_readme_my_first_tool");

//    #ifdef IMGUI_HAS_TABLE
//        // ## Capture all tables demo
//        t = REGISTER_TEST("capture", "capture_table_demo");
//    t->TestFunc = [](ImGuiTestContext* ctx)
//    {
//        ctx->WindowRef("Dear ImGui Demo");
//        ctx->ItemOpen("Tables & Columns");
//        ctx->ItemClick("Tables/Open all");
//        ctx->ItemOpen("Tables/Advanced/Options");
//        ctx->ItemOpen("Tables/Tree view/**/Root");
//
//        ImGuiCaptureArgs args;
//        ctx->CaptureInitArgs(&args, ImGuiCaptureFlags_StitchFullContents | ImGuiCaptureFlags_HideMouseCursor);
//        ctx->CaptureAddWindow(&args, "");
//        ctx->CaptureScreenshot(&args);
//
//    ctx->ItemClick("Tables/Close all");
//    };
//    #endif
}
