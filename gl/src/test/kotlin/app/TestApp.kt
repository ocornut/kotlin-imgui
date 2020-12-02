package app

import engine.core.TestEngine
import engine.core.TestVerboseLevel
import glm_.vec4.Vec4
import helpers.ImGuiApp

object TestApp {

    var quit = false
    var appWindow: ImGuiApp? = null
    var testEngine: TestEngine? = null
    var lastTime = 0L
    val clearColor = Vec4(0.45f, 0.55f, 0.6f, 1f)

    // Command-line options
    var optGUI = false
    var optFast = true
    var optVerboseLevel = TestVerboseLevel.COUNT // Set in main.cpp
    var optVerboseLevelOnError = TestVerboseLevel.COUNT // Set in main.cpp
    var optNoThrottle = false
    var optPauseOnExit = true
    var optStressAmount = 5
//    char*                   OptFileOpener = NULL
    val testsToRun = ArrayList<String>()
}