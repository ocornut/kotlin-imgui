package engine.context

import glm_.vec2.Vec2
import imgui.ImGui

// Misc

val TestContext.mainViewportPos
    get() =
//    #ifdef IMGUI_HAS_VIEWPORT
//    return ImGui::GetMainViewport()->Pos;
//    #else
        Vec2()
//    #endif

val TestContext.mainViewportSize: Vec2
    get() =
//    #ifdef IMGUI_HAS_VIEWPORT
//        ImGui::GetMainViewport()->Size;
//    #else
        Vec2(ImGui.io.displaySize)
//    #endif
