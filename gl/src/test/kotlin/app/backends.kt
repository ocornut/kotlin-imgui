package app

import engine.engine.TestEngineScreenCaptureFunc
import engine.engine.showTestWindow
import glm_.vec4.Vec4i
import imgui.ImGui
import kool.set
import org.lwjgl.opengl.GL11C.*
import java.nio.ByteBuffer


var showDemoWindow_ = true
var showAnotherWindow = false

var f = 0f
var counter = 0

fun showUI() {

    gApp.testEngine!!.showTestWindow()

    // 1. Show the big demo window (Most of the sample code is in ImGui::ShowDemoWindow()! You can browse its code to learn more about Dear ImGui!).
    if (showDemoWindow_)
        ImGui.showDemoWindow(::showDemoWindow_)

    // 2. Show a simple window that we create ourselves. We use a Begin/End pair to created a named window.
    ImGui.apply {

        begin("Hello, world!")                          // Create a window called "Hello, world!" and append into it.

        text("This is some useful text.")               // Display some text (you can use a format strings too)
        checkbox("Demo Window", ::showDemoWindow_)      // Edit bools storing our window open/close state
        checkbox("Another Window", ::showAnotherWindow)

        sliderFloat("float", ::f, 0f, 1f)            // Edit 1 float using a slider from 0.0f to 1.0f
        colorEdit3("clear color", gApp.clearColor) // Edit 3 floats representing a color

        if (button("Button"))                            // Buttons return true when clicked (most widgets return true when edited/activated)
            counter++
        sameLine()
        text("counter = $counter")

        text("Application average %.3f ms/frame (%.1f FPS)", 1000f / io.framerate, io.framerate)
        end()
    }

    // 3. Show another simple window.
    if (showAnotherWindow) {
        ImGui.begin("Another Window", ::showAnotherWindow)   // Pass a pointer to our bool variable (the window will have a closing button that will clear the bool when clicked)
        ImGui.text("Hello from another window!")
        if (ImGui.button("Close Me"))
            showAnotherWindow = false
        ImGui.end()
    }

    ImGui.endFrame()
}

val captureFramebufferScreenshot: TestEngineScreenCaptureFunc = { extend: Vec4i, pixels: ByteBuffer, _: Any? ->
    val (x, y, w, h) = extend
    val y2 = ImGui.io.displaySize.y - (y + h)
    glPixelStorei(GL_PACK_ALIGNMENT, 1)
    glReadPixels(x, y2, w, h, GL_RGBA, GL_UNSIGNED_BYTE, pixels)

    // Flip vertically
    val comp = 4
    val stride = w * comp
    val lineTmp = ByteArray(stride)
    var lineA = 0
    var lineB = stride * (h - 1)
    while (lineA < lineB) {
        repeat(stride) { lineTmp[it] = pixels[lineA + it] }
        repeat(stride) { pixels[lineA + it] = pixels[lineB + it] }
        repeat(stride) { pixels[lineB + it] = lineTmp[it] }
        lineA += stride
        lineB -= stride
    }
    true
}
