package app.tests

import engine.TestEngine
import engine.context.TestContext
import engine.context.yield
import engine.core.registerTest
import glm_.vec2.Vec2
import imgui.ImGui
import imgui.WindowFlag
import imgui.classes.DrawList
import imgui.internal.DrawCallback
import imgui.internal.DrawCmd
import io.kotest.matchers.shouldBe

//-------------------------------------------------------------------------
// Tests: Draw, ImDrawList
//-------------------------------------------------------------------------

fun registerTests_draw(e: TestEngine) {

    // ## Test AddCallback()
    e.registerTest("drawlist", "drawlist_callbacks").let { t ->
        t.guiFunc = { ctx: TestContext ->
            ImGui.begin("Test Window", null, WindowFlag.NoSavedSettings.i)
            val drawList = ImGui.windowDrawList
            drawList.cmdBuffer.size shouldBe 2
            drawList.cmdBuffer.last().elemCount shouldBe 0
            ImGui.button("Hello")

            val cb: DrawCallback = { parentList: DrawList, cmd: DrawCmd ->
                val ctx = cmd.userCallbackData as TestContext
                ctx.genericVars.int1++
            }
            drawList.addCallback(cb, ctx)
            drawList.cmdBuffer.size shouldBe 4

            drawList.addCallback(cb, ctx)
            drawList.cmdBuffer.size shouldBe 5

            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            ctx.genericVars.int1 = 0
            ctx.yield()
            ctx.genericVars.int1 shouldBe 2
        }
    }

    // ## Test whether splitting/merging draw lists properly retains a texture id.
    e.registerTest("drawlist", "drawlist_splitter_texture_id").let { t ->
        t.guiFunc = { ctx: TestContext ->
            ImGui.begin("Test Window", null, WindowFlag.NoSavedSettings.i)
            val drawList = ImGui.windowDrawList
            val prevTextureId = drawList._textureIdStack.last()
            val drawCount = drawList.cmdBuffer.size
            drawList.cmdBuffer.last().elemCount shouldBe 0

            val p = ImGui.cursorScreenPos
            ImGui.dummy(Vec2(100 + 10 + 100, 100))

            drawList.channelsSplit(2)
            drawList.channelsSetCurrent(0)
            // Image wont be clipped when added directly into the draw list.
            drawList.addImage(100, p, p + 100f)
            drawList.channelsSetCurrent(1)
            drawList.addImage(200, p + Vec2(110, 0), p + Vec2(210, 100))
            drawList.channelsMerge()

            drawList.cmdBuffer.size shouldBe (drawCount + 2)
            drawList.cmdBuffer.last().elemCount shouldBe 0
            prevTextureId shouldBe drawList.cmdBuffer.last().textureId

            // Replace fake texture IDs with a known good ID in order to prevent graphics API crashing application.
            for (cmd in drawList.cmdBuffer)
                if (cmd.textureId == 100 || cmd.textureId == 200)
                    cmd.textureId = prevTextureId

            ImGui.end()
        }
    }
}