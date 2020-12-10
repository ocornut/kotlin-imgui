package app

import app.tests.registerTests
import engine.TestEngine
import engine.engine.*
import glm_.parseInt
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import glm_.vec4.Vec4i
import imgui.ConfigFlag
import imgui.ImGui
import imgui.api.g
import imgui.api.gImGui
import imgui.classes.Context
import imgui.or
import shared.ImGuiApp
import shared.ImGuiApp_ImplNull
import shared.gitBranchName
import shared.osIsDebuggerPresent
import java.nio.ByteBuffer
import kotlin.system.exitProcess

/*
 dear imgui - Standalone GUI/command-line app for Test Engine
 If you are new to dear imgui, see examples/README.txt and documentation at the top of imgui.cpp.

 Interactive mode, e.g.
   main.exe [tests]
   main.exe -gui -fileopener ..\..\tools\win32_open_with_sublime.cmd -slow
   main.exe -gui -fileopener ..\..\tools\win32_open_with_sublime.cmd -nothrottle

 Command-line mode, e.g.
   main.exe -nogui -v -nopause
   main.exe -nogui -nopause perf_
*/


//-------------------------------------------------------------------------
// Test Application
//-------------------------------------------------------------------------

object gApp {

    var quit = false
    var appWindow: ImGuiApp? = null
    var testEngine: TestEngine? = null
    var lastTime = 0L
    val clearColor = Vec4(0.45f, 0.55f, 0.6f, 1f)

    // Command-line options
    var optGUI = false
    var optFast = true
    var optVerboseLevelBasic = TestVerboseLevel.COUNT // Default is set in main.cpp depending on -gui/-nogui
    var optVerboseLevelError = TestVerboseLevel.COUNT // "
    var optNoThrottle = false
    var optPauseOnExit = true
    var optStressAmount = 5

    //    char*                   OptFileOpener = NULL
    val testsToRun = ArrayList<String>()
}

val IMGUI_APP_WIN32_DX11 = false
val IMGUI_APP_SDL_GL3 = false
var IMGUI_APP_GLFW_GL3 = false

fun main(args: Array<String>) {

//    Configuration.DEBUG.set(true)
//    Configuration.DEBUG_MEMORY_ALLOCATOR.set(true)

    // Parse command-line arguments
    if (IMGUI_APP_WIN32_DX11 || IMGUI_APP_SDL_GL3 || IMGUI_APP_GLFW_GL3)
        gApp.optGUI = true

//    #ifdef CMDLINE_ARGS
//        if (argc == 1)
//        {
//            printf("# [exe] %s\n", CMDLINE_ARGS);
//            ImParseSplitCommandLine(&argc, (const char***)&argv, CMDLINE_ARGS);
//            if (!ParseCommandLineOptions(argc, argv))
//                return ImGuiTestAppErrorCode_CommandLineError;
//            free(argv);
//        }
//        else
//    #endif
//    {
    if (!parseCommandLineOptions(args))
        exitProcess(TestAppErrorCode.CommandLineError.ordinal)
//    }
//    argv = NULL;

    // Default verbose level differs whether we are in in GUI or Command-Line mode
    // Default verbose levels differs whether we are in in GUI or Command-Line mode
    if (gApp.optGUI) {
        // Default -v4 -ve4
        if (gApp.optVerboseLevelBasic == TestVerboseLevel.COUNT)
            gApp.optVerboseLevelBasic = TestVerboseLevel.Debug
        if (gApp.optVerboseLevelError == TestVerboseLevel.COUNT)
            gApp.optVerboseLevelError = TestVerboseLevel.Debug
    } else {
        // Default -v2 -ve4
        if (gApp.optVerboseLevelBasic == TestVerboseLevel.COUNT)
            gApp.optVerboseLevelBasic = TestVerboseLevel.Warning
        if (gApp.optVerboseLevelError == TestVerboseLevel.COUNT)
            gApp.optVerboseLevelError = TestVerboseLevel.Debug
    }

    // Setup Dear ImGui context
    val ctx = Context()
    ImGui.styleColorsDark()
    val io = ImGui.io.apply {
        iniFilename = "imgui.ini"
        configFlags = configFlags or ConfigFlag.NavEnableKeyboard  // Enable Keyboard Controls
    }

    g.testEngineHookItems = true

    //ImGuiStyle& style = ImGui::GetStyle();
    //style.Colors[ImGuiCol_Border] = style.Colors[ImGuiCol_BorderShadow] = ImVec4(1.0f, 0, 0, 1.0f);
    //style.FrameBorderSize = 1.0f;
    //style.FrameRounding = 5.0f;
//    #ifdef IMGUI_HAS_VIEWPORT
//    //io.ConfigFlags |= ImGuiConfigFlags_ViewportsEnable;
//    #endif
//    #ifdef IMGUI_HAS_DOCK
//            io.ConfigFlags | = ImGuiConfigFlags_DockingEnable
//    //io.ConfigDockingTabBarOnSingleWindows = true;
//    #endif

    // Creates window
    if (gApp.optGUI) {
//        #ifdef IMGUI_APP_WIN32_DX11
//                g_App.AppWindow = ImGuiApp_ImplWin32DX11_Create();
//        #elif IMGUI_APP_SDL_GL3
//                g_App.AppWindow = ImGuiApp_ImplSdlGL3_Create();
//        #elif IMGUI_APP_GLFW_GL3
//                g_App.AppWindow = ImGuiApp_ImplGlfwGL3_Create();
//        #endif
    }
    if (gApp.appWindow == null)
        gApp.appWindow = ImGuiApp_ImplNull()
    gApp.appWindow!!.dpiAware = false

    // Create TestEngine context
    assert(gApp.testEngine == null)
    val engine = TestEngine(gImGui!!)
    gApp.testEngine = engine

    // Apply options
    val testIo = engine.io.apply {
        configRunWithGui = gApp.optGUI
        configRunFast = gApp.optFast
        configVerboseLevel = gApp.optVerboseLevelBasic
        configVerboseLevelOnError = gApp.optVerboseLevelError
        configNoThrottle = gApp.optNoThrottle
        perfStressAmount = gApp.optStressAmount
        if (!gApp.optGUI)
            configLogToTTY = true
        if (!gApp.optGUI && osIsDebuggerPresent()) {
            configLogToDebugger = true
            configBreakOnError = true
        }

        // Set up functions
//        srcFileOpenFunc = srcFileOpenerFunc TODO
        srcFileOpenUserData = null
        screenCaptureFunc = { extend: Vec4i, pixels: ByteBuffer, userData: Any? ->
            val app = userData as ImGuiApp
            app.captureFramebuffer(extend, pixels, userData)
        }
        screenCaptureUserData = gApp.appWindow
//        test_io.CoroutineFuncs = Coroutine_ImplStdThread_GetInterface();
    }

    // Register and queue our tests
    engine.registerTests()
//    engine.calcSourceLineEnds()

    queueTests(engine)

    // Branch name stored in annotation field by default
    testIo.gitBranchName = gitBranchName
    println("Git branch: \"${testIo.gitBranchName}\"")

    // Start engine
    engine.start()

    // Create window
    val appWindow = gApp.appWindow!!
    appWindow.initCreateWindow("Dear ImGui: Test Engine", Vec2(1440, 900))
    appWindow.initBackends()

    // Load fonts, Set DPI scale
    loadFonts(appWindow.dpiScale)
    ImGui.style.scaleAllSizes(appWindow.dpiScale)
    testIo.dpiScale = appWindow.dpiScale

    // Main loop
    var aborted = false
    while (true) {
        if (!appWindow.newFrame())
            aborted = true
        if (aborted) {
            engine.abort()
            engine.coroutineStopRequest()
            if (!engine.isRunningTests)
                break
        }

        ImGui.newFrame()
        showUI()
        ImGui.render()

        if (!gApp.optGUI && !testIo.runningTests)
            break

        appWindow.vSync = !testIo.renderWantMaxSpeed
        appWindow.clearColor put gApp.clearColor
        appWindow.render()

        engine.postRender()
    }

    engine.stop()

    // Print results (command-line mode)
    var errorCode = TestAppErrorCode.Success
    if (!gApp.quit) {
        val (countTested, countSuccess) = engine.result
        engine.printResultSummary()
        if (countTested != countSuccess)
            errorCode = TestAppErrorCode.TestFailed
    }

    // Shutdown window
    appWindow.shutdownBackends()
    appWindow.shutdownCloseWindow()

    // Shutdown
    // We shutdown the Dear ImGui context _before_ the test engine context, so .ini data may be saved.
    ctx.destroy()
    engine.shutdownContext()
    appWindow.destroy()

//    if (app.optFileOpener)
//        free(g_App.OptFileOpener)

    if (gApp.optPauseOnExit && !gApp.optGUI) {
        println("Press Enter to exit.")
        System.`in`.read()
    }

    exitProcess(errorCode.ordinal)
}

fun queueTests(engine: TestEngine) {
    // Non-interactive mode queue all tests by default
    if (!gApp.optGUI && gApp.testsToRun.isEmpty())
        gApp.testsToRun += "tests"

    // Queue requested tests
    // FIXME: Maybe need some cleanup to not hard-coded groups.
    for (testSpec_ in gApp.testsToRun)
        when (testSpec_) {
            "tests" -> gApp.testEngine!!.queueTests(TestGroup.Tests, runFlags = TestRunFlag.CommandLine.i)
            "perf" -> gApp.testEngine!!.queueTests(TestGroup.Perfs, runFlags = TestRunFlag.CommandLine.i)
            else -> {
                val testSpec = testSpec_.takeIf { testSpec_ != "all" }
                for (group in 0 until TestGroup.COUNT.i)
                    gApp.testEngine!!.queueTests(TestGroup(group), testSpec, runFlags = TestRunFlag.CommandLine.i)
            }
        }
    gApp.testsToRun.clear()
}

fun parseCommandLineOptions(args: Array<String>): Boolean {
    var n = 0
    while (n < args.size) {
        val arg = args[n++]
        if (arg[0] == '-')
            when (arg) {
                // Command-line option
                "-v" -> {
                    gApp.optVerboseLevelBasic = TestVerboseLevel.Info
                    gApp.optVerboseLevelError = TestVerboseLevel.Debug
                }
                "-v0" -> gApp.optVerboseLevelBasic = TestVerboseLevel.Silent
                "-v1" -> gApp.optVerboseLevelBasic = TestVerboseLevel.Error
                "-v2" -> gApp.optVerboseLevelBasic = TestVerboseLevel.Warning
                "-v3" -> gApp.optVerboseLevelBasic = TestVerboseLevel.Info
                "-v4" -> gApp.optVerboseLevelBasic = TestVerboseLevel.Debug
                "-ve0" -> gApp.optVerboseLevelError = TestVerboseLevel.Silent
                "-ve1" -> gApp.optVerboseLevelError = TestVerboseLevel.Error
                "-ve2" -> gApp.optVerboseLevelError = TestVerboseLevel.Warning
                "-ve3" -> gApp.optVerboseLevelError = TestVerboseLevel.Info
                "-ve4" -> gApp.optVerboseLevelError = TestVerboseLevel.Debug
                "-gui" -> gApp.optGUI = true
                "-nogui" -> gApp.optGUI = false
                "-fast" -> {
                    gApp.optFast = true
                    gApp.optNoThrottle = true
                }
                "-slow" -> {
                    gApp.optFast = false
                    gApp.optNoThrottle = false
                }
                "-nothrottle" -> gApp.optNoThrottle = true
                "-nopause" -> gApp.optPauseOnExit = false
                "-stressamount" -> if (n < args.size) gApp.optStressAmount = args[n++].parseInt()
//            "-fileopener") == 0 && n + 1 < argc) {
//                g_App.OptFileOpener = strdup(argv[n + 1])
//                ImPathFixSeparatorsForCurrentOS(g_App.OptFileOpener)
//                n++
//            }
                else -> {
                    println("""
                            Syntax: .. <options> [tests]
                            Options:
                                -h                       : show command-line help.
                                -v                       : verbose mode (same as -v3 -ve4)
                                -v0/-v1/-v2/-v3/-v4      : verbose level [v0: silent, v1: errors, v2: headers & warnings, v3: info, v4: debug]
                                -ve0/-ve1/-ve2/-ve3/-ve4 : verbose level for errored tests [same as above]
                                -gui/-nogui              : enable interactive mode.
                                -slow                    : run automation at feeble human speed.
                                -nothrottle              : run GUI app without throttling/vsync by default.
                                -nopause                 : don't pause application on exit.
                                -stressamount <int>      : set performance test duration multiplier (default: 5)
                                -fileopener <file>       : provide a bat/cmd/shell script to open source file.
                            Tests:
                                all/tests/perf           : queue by groups: all, only tests, only performance benchmarks.
                                [pattern]                : queue all tests containing the word [pattern].
                                """)
                    return false
                }
            }
        else // Add tests
            gApp.testsToRun += arg
    }
    return true
}

fun loadFonts(dpiScale: Float) {
    val io = ImGui.io
    io.fonts.addFontDefault()
    //ImFontConfig cfg;
    //cfg.RasterizerMultiply = 1.1f;

    // Find font directory
    io.fonts.addFontFromFileTTF("fonts/NotoSans-Regular.ttf", 16f * dpiScale)
    io.fonts.addFontFromFileTTF("fonts/Roboto-Medium.ttf", 16f * dpiScale)
    //io.Fonts->AddFontFromFileTTF(Str64f("%s/%s", base_font_dir.c_str(), "RobotoMono-Regular.ttf").c_str(), 16.0f * dpiScale, &cfg);
    //io.Fonts->AddFontFromFileTTF(Str64f("%s/%s", base_font_dir.c_str(), "Cousine-Regular.ttf").c_str(), 15.0f * dpiScale);
    //io.Fonts->AddFontFromFileTTF(Str64f("%s/%s", base_font_dir.c_str(), "DroidSans.ttf").c_str(), 16.0f * dpiScale);
    //io.Fonts->AddFontFromFileTTF(Str64f("%s/%s", base_font_dir.c_str(), "ProggyTiny.ttf").c_str(), 10.0f * dpiScale);
    //IM_ASSERT(font != NULL);

    io.fonts.build()
}


// Source file opener
//static void SrcFileOpenerFunc(const char* filename, int line, void*)
//{
//    if (!g_App.OptFileOpener) {
//        fprintf(stderr, "Executable needs to be called with a -fileopener argument!\n")
//        return
//    }
//
//    ImGuiTextBuffer cmd_line
//            cmd_line.appendf("%s %s %d", g_App.OptFileOpener, filename, line)
//    printf("Calling: '%s'\n", cmd_line.c_str())
//    bool ret = ImOsCreateProcess (cmd_line.c_str())
//    if (!ret)
//        fprintf(stderr, "Error creating process!\n")
//}

// Return value for main()
enum class TestAppErrorCode { Success, CommandLineError, TestFailed }


