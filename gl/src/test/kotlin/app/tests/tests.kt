// dear imgui
// (tests)

package app.tests

import engine.TestEngine
import java.io.File


//-------------------------------------------------------------------------
// NOTES (also see TODO in imgui_te_core.cpp)
//-------------------------------------------------------------------------
// - Tests can't reliably once ImGuiCond_Once or ImGuiCond_FirstUseEver
// - GuiFunc can't run code that yields. There is an assert for that.
//-------------------------------------------------------------------------

fun registerTests(e: TestEngine) {

    // Tests
    registerTests_Window(e)
//    registerTests_Layout(e)
//    registerTests_Widgets(e)
//    registerTests_Nav(e)
//    registerTests_Columns(e)
////    RegisterTests_Table(e)
////    RegisterTests_Docking(e)
//    registerTests_drawList(e)
//    registerTests_Misc(e)
//
//    // Captures
//    registerTests_Capture(e)
//
//    // Performance Benchmarks
////    registerTests_Perf(e)
}