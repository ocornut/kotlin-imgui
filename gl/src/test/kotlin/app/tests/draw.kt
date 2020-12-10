package app.tests

import engine.TestEngine
import engine.context.TestContext
import engine.context.logDebug
import engine.context.logInfo
import engine.context.yield
import engine.engine.registerTest
import glm_.vec2.Vec2
import imgui.*
import imgui.classes.DrawList
import imgui.internal.DrawCallback
import imgui.internal.DrawCmd
import imgui.internal.DrawIdx
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kool.BYTES

//-------------------------------------------------------------------------
// Tests: Draw, ImDrawList
//-------------------------------------------------------------------------

class HelpersTextureId {
    // Fake texture ID
    val fakeTex0: TextureID = 100
    val fakeTex1: TextureID = 200

    // Replace fake texture IDs with a known good ID in order to prevent graphics API crashing application.
    fun removeFakeTexFromDrawList(drawList: DrawList, replacementTexId: TextureID) {
        for (cmd in drawList.cmdBuffer)
            if (cmd.textureId == fakeTex0 || cmd.textureId == fakeTex1)
                cmd.textureId = replacementTexId
    }
}

fun canTestVtxOffset(ctx: TestContext): Boolean = when {
    ctx.uiContext!!.io.backendFlags hasnt BackendFlag.RendererHasVtxOffset -> {
        ctx.logInfo("Skipping: back-end does not support RendererHasVtxOffset!")
        false
    }
    DrawIdx.BYTES != 2 -> {
        ctx.logInfo("sizeof(ImDrawIdx) != 2")
        false
    }
    else -> true
}

fun registerTests_drawList(e: TestEngine) {

    // ## Test AddCallback()
    e.registerTest("drawlist", "drawlist_callbacks").let { t ->
        t.guiFunc = { ctx: TestContext ->
            ImGui.setNextWindowScroll(Vec2())
            ImGui.setNextWindowSize(Vec2(100f))
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

            // Test callbacks in columns
            ImGui.columns(3)
            ImGui.columns(1)

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
            val startCmdbufferSize = drawList.cmdBuffer.size
            drawList.cmdBuffer.last().elemCount shouldBe 0

            val p = ImGui.cursorScreenPos
            ImGui.dummy(Vec2(100 + 10 + 100, 100))

            val fakeTex = HelpersTextureId()

            drawList.channelsSplit(2)
            drawList.channelsSetCurrent(0)
            drawList.addImage(fakeTex.fakeTex0, p, p + 100f)
            drawList.channelsSetCurrent(1)
            drawList.addImage(fakeTex.fakeTex1, p + Vec2(110, 0), p + Vec2(210, 100))
            drawList.channelsMerge()

            drawList.cmdBuffer.size shouldBe (startCmdbufferSize + 2)
            drawList.cmdBuffer.last().elemCount shouldBe 0
            prevTextureId shouldBe drawList.cmdBuffer.last().textureId

            fakeTex.removeFakeTexFromDrawList(drawList, prevTextureId)

            ImGui.end()
        }
    }

    e.registerTest("drawlist", "drawlist_merge_cmd").let { t ->
        t.guiFunc = { ctx: TestContext ->
            ImGui.setNextWindowSize(Vec2(200)) // Make sure our columns aren't clipped
            ImGui.begin("Test Window", null, WindowFlag.NoSavedSettings.i)

            val drawList = ImGui.windowDrawList
            drawList.cmdBuffer.last().elemCount shouldBe 0

            val startCmdbufferSize = drawList.cmdBuffer.size
            //ImGui::Text("Hello");
            drawList.addDrawCmd() // Empty command
            drawList.cmdBuffer.size shouldBe (startCmdbufferSize + 1)
            drawList.cmdBuffer.last().elemCount shouldBe 0

            ImGui.columns(2)
            drawList.cmdBuffer.size shouldBe 1 // In channel 1
            ImGui.text("One")
            drawList.cmdBuffer.size shouldBe 1 // In channel 1

            ImGui.nextColumn()
            drawList.cmdBuffer.size shouldBe 1 // In channel 2
            ImGui.text("Two")
            drawList.cmdBuffer.size shouldBe 1 // In channel 2

            ImGui.pushColumnsBackground() // In channel 0
            // 2020/06/13: we don't request of PushColumnsBackground() to merge the two same commands (one created by loose AddDrawCmd())!
            //IM_CHECK_EQ(draw_list->CmdBuffer.Size, start_cmdbuffer_size);
            ImGui.popColumnsBackground()

            drawList.cmdBuffer.size shouldBe 1 // In channel 2

            ImGui.text("Two Two") // FIXME-TESTS: Make sure this is visible
            ImGui.isItemVisible shouldBe true

            ImGui.columns(1)
            drawList.cmdBuffer.size shouldBe (startCmdbufferSize + 1 + 2) // Channel 1 and 2 each other one Cmd

            ImGui.end()
        }
    }

    // ## Test VtxOffset
    e.registerTest("drawlist", "drawlist_vtxoffset_basic").let { t ->
        t.guiFunc = { ctx: TestContext ->
            if (canTestVtxOffset(ctx)) {
                ImGui.begin("Test Window", null, WindowFlag.NoSavedSettings.i)
                val drawList = ImGui.windowDrawList
                drawList.cmdBuffer.last().elemCount shouldBe 0
                drawList.cmdBuffer.last().vtxOffset shouldBe 0

                val startVtxbufferSize = drawList.vtxBuffer.size
                val startCmdbufferSize = drawList.cmdBuffer.size
                ctx.logDebug("VtxBuffer.Size = $startVtxbufferSize, CmdBuffer.Size = $startCmdbufferSize")

                // fill up vertex buffer with rectangles
                val rectCount = (65536 - startVtxbufferSize - 1) / 4
                val expectedThreshold = rectCount * 4 + startVtxbufferSize
                ctx.logDebug("rect_count = $rectCount")
                ctx.logDebug("expected_threshold = $expectedThreshold")

                val pMin = ImGui.cursorScreenPos
                val pMax = pMin + 50
                ImGui.dummy(pMax - pMin)
                for (n in 0 until rectCount)
                    drawList.addRectFilled(pMin, pMax, COL32(255, 0, 0, 255))

                // we are just before reaching 64k vertex count, so far everything
                // should land single command buffer
                drawList.vtxBuffer.size shouldBe expectedThreshold
                drawList.cmdBuffer.size shouldBe startCmdbufferSize
                drawList.cmdBuffer.last().vtxOffset shouldBe 0
                drawList._cmdHeader.vtxOffset shouldBe 0

                // Test #3232
//                #if 1
                drawList.primReserve(6, 4)
                drawList.primUnreserve(6, 4)
                val clipRect = drawList._clipRectStack.last()
                drawList.pushClipRect(Vec2(clipRect.x, clipRect.y), Vec2(clipRect.z, clipRect.w)) // Use same cliprect so pop will easily
                drawList.popClipRect()
                drawList._cmdHeader.vtxOffset shouldBe drawList.cmdBuffer.last().vtxOffset
//                #endif

                // Next rect should pass 64k threshold and emit new command
                drawList.addRectFilled(pMin, pMax, COL32(0, 255, 0, 255))
                drawList.vtxBuffer.size shouldBeGreaterThanOrEqual 65536
                drawList.cmdBuffer.size shouldBe (startCmdbufferSize + 1)
                drawList.cmdBuffer.last().vtxOffset shouldBe expectedThreshold
                drawList._cmdHeader.vtxOffset shouldBe expectedThreshold

                ImGui.end()
            }
        }
    }

    // ## Test starting Splitter when VtxOffset != 0
    e.registerTest("drawlist", "drawlist_vtxoffset_splitter_2").let { t ->
        t.guiFunc = { ctx: TestContext ->
            if (canTestVtxOffset(ctx)) {

                ImGui.begin("Test Window", null, WindowFlag.NoSavedSettings.i)
                val drawList = ImGui.windowDrawList

                // fill up vertex buffer with rectangles
                val startVtxbufferSize = drawList.vtxBuffer.size
                val rectCount = (65536 - startVtxbufferSize - 1) / 4

                val pMin = ImGui.cursorScreenPos
                val pMax = pMin + 50
                ImGui.dummy(pMax - pMin)
                drawList.cmdBuffer.last().vtxOffset shouldBe 0
                for (n in 0..rectCount)
                    drawList.addRectFilled(pMin, pMax, COL32(255, 0, 0, 255))
                val vtxOffset = drawList.cmdBuffer.last().vtxOffset
                drawList.cmdBuffer.last().vtxOffset shouldBeGreaterThan 0

                ImGui.columns(3)
                ImGui.text("AAA")
                ImGui.nextColumn()
                ImGui.text("BBB")
                ImGui.nextColumn()
                ImGui.text("CCC")
                ImGui.nextColumn()

                ImGui.text("AAA 2")
                ImGui.nextColumn()
                ImGui.text("BBB 2")
                ImGui.nextColumn()
                ImGui.text("CCC 2")
                ImGui.nextColumn()
                ImGui.columns(1)

                drawList.channelsSplit(3)
                for (n in 1..2) {
                    drawList.channelsSetCurrent(n)
                    drawList.cmdBuffer.last().vtxOffset shouldBe vtxOffset
                    drawList.addRectFilled(pMin, pMax, COL32(0, 255, 0, 255))
                }
                drawList.channelsMerge()

                ImGui.end()
            }
        }
    }

    // ## Test VtxOffset with Splitter with worst case scenario
    // Draw calls are interleaved, one with VtxOffset == 0, next with VtxOffset != 0
    e.registerTest("drawlist", "drawlist_vtxoffset_splitter_draw_call_explosion").let { t ->
        t.guiFunc = { ctx: TestContext ->
            if (canTestVtxOffset(ctx)) {
                ImGui.begin("Test Window", null, WindowFlag.NoSavedSettings.i)
                val drawList = ImGui.windowDrawList
                drawList.cmdBuffer.last().elemCount shouldBe 0
                drawList.cmdBuffer.last().vtxOffset shouldBe 0

                val startVtxbufferSize = drawList.vtxBuffer.size
                val startCmdbufferSize = drawList.cmdBuffer.size
                ctx.logDebug("VtxBuffer.Size = $startVtxbufferSize, CmdBuffer.Size = $startCmdbufferSize")

                // Fill up vertex buffer with rectangles
                val rectCount = (65536 - startVtxbufferSize - 1) / 4
                ctx.logDebug("rect_count = $rectCount")

                // Expected number of draw calls after interleaving channels with VtxOffset == 0 and != 0
                val expectedDrawCommandCount = startCmdbufferSize + rectCount * 2 - 1 // minus one, because last channel became active one

                val pMin = ImGui.cursorScreenPos
                val pMax = pMin + 50
                val color = COL32(255, 255, 255, 255)
                ImGui.dummy(pMax - pMin)

                // Make split and draw rect to every even channel
                drawList.channelsSplit(rectCount * 2)

                for (n in 0 until rectCount) {
                    drawList.channelsSetCurrent(n * 2)
                    drawList.addRectFilled(pMin, pMax, color)
                    if (n == 0 || n == rectCount - 1) { // Reduce check/log spam
                        drawList.cmdBuffer.last().vtxOffset shouldBe 0
                        drawList._cmdHeader.vtxOffset shouldBe 0
                    }
                }

                // From this point all new rects will pass 64k vertex count, and draw calls will have VtxOffset != 0
                // Draw rect to every odd channel
                for (n in 0 until rectCount) {
                    drawList.channelsSetCurrent(n * 2 + 1)
                    if (n == 0 || n == rectCount - 1) // Reduce check/log spam
                        drawList.cmdBuffer.size shouldBe 1
                    if (n == 0)
                        drawList.cmdBuffer.last().vtxOffset shouldBe 0
                    drawList.addRectFilled(pMin, pMax, color)
                    if (n == 0 || n == rectCount - 1) { // Reduce check/log spam
                        drawList.cmdBuffer.size shouldBe 1 // Confirm that VtxOffset change didn't grow CmdBuffer (at n==0, empty command gets recycled)
                        drawList.cmdBuffer.last().vtxOffset shouldBeGreaterThan 0
                        drawList._cmdHeader.vtxOffset shouldBeGreaterThan 0
                    }
                }

                drawList.channelsSetCurrent(0)
                drawList.cmdBuffer.last().vtxOffset shouldBeGreaterThan 0

                drawList.channelsMerge()
                drawList.cmdBuffer.size shouldBe expectedDrawCommandCount

                ImGui.end()
            }
        }
    }
}