package engine.context

import glm_.vec2.Vec2

// Misc

val TestContext.mainViewportPos
    get() =
//    #ifdef IMGUI_HAS_VIEWPORT
//    return ImGui::GetMainViewport()->Pos;
//    #else
        Vec2()
//    #endif