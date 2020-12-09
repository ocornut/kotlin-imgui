package app.tests.widgets

import engine.TestEngine
import engine.context.*
import engine.engine.TestOpFlag
import engine.engine.registerTest
import glm_.vec2.Vec2
import imgui.*
import io.kotest.matchers.shouldBe
import imgui.WindowFlag as Wf
import imgui.internal.sections.ButtonFlag as Bf

fun registerTests_Widgets_button(e: TestEngine) {

    // ## Test basic button presses
    e.registerTest("widgets", "widgets_button_press").let { t ->
        t.userData = IntArray(6)
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.userData as IntArray

            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                if (ImGui.button("Button0"))
                    vars[0]++
                if (ImGui.buttonEx("Button1", Vec2(), Bf.PressedOnDoubleClick.i))
                    vars[1]++
                if (ImGui.buttonEx("Button2", Vec2(), Bf.PressedOnClickRelease or Bf.PressedOnDoubleClick))
                    vars[2]++
                if (ImGui.buttonEx("Button3", Vec2(), Bf.PressedOnClickReleaseAnywhere.i))
                    vars[3]++
                if (ImGui.buttonEx("Button4", Vec2(), Bf.Repeat.i))
                    vars[4]++
            }
        }
        t.testFunc = { ctx: TestContext ->

            val vars = ctx.userData as IntArray

            ctx.setRef("Test Window")
            ctx.itemClick("Button0")
            vars[0] shouldBe 1
            ctx.itemDoubleClick("Button1")
            vars[1] shouldBe 1
            ctx.itemDoubleClick("Button2")
            vars[2] shouldBe 2

            // Test ImGuiButtonFlags_PressedOnClickRelease vs ImGuiButtonFlags_PressedOnClickReleaseAnywhere
            vars[2] = 0
            ctx.mouseMove("Button2")
            ctx.mouseDown(0)
            ctx.mouseMove("Button0", TestOpFlag.NoCheckHoveredId.i)
            ctx.mouseUp(0)
            vars[2] shouldBe 0
            ctx.mouseMove("Button3")
            ctx.mouseDown(0)
            ctx.mouseMove("Button0", TestOpFlag.NoCheckHoveredId.i)
            ctx.mouseUp(0)
            vars[3] shouldBe 1

            // Test ImGuiButtonFlags_Repeat
            ctx.itemClick("Button4")
            vars[4] shouldBe 1
            ctx.mouseDown(0)
            vars[4] shouldBe 1
            ctx.uiContext!!.io.apply {
                val step = min(keyRepeatDelay, keyRepeatRate) * 0.5f
                ctx.sleepNoSkip(keyRepeatDelay, step)
                ctx.sleepNoSkip(keyRepeatRate, step)
                ctx.sleepNoSkip(keyRepeatRate, step)
                ctx.sleepNoSkip(keyRepeatRate, step)
            }
            vars[4] shouldBe (1 + 1 + 3 * 2) // FIXME: MouseRepeatRate is double KeyRepeatRate, that's not documented / or that's a bug
            ctx.mouseUp(0)
        }
    }

    // ## Test basic button presses
    e.registerTest("widgets", "widgets_button_mouse_buttons").let { t ->
        t.userData = IntArray(6)
        t.guiFunc = { ctx: TestContext ->

            val vars = ctx.userData as IntArray

            dsl.window("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                if (ImGui.buttonEx("ButtonL", Vec2(), Bf.MouseButtonLeft.i))
                    vars[0]++
                if (ImGui.buttonEx("ButtonR", Vec2(), Bf.MouseButtonRight.i))
                    vars[1]++
                if (ImGui.buttonEx("ButtonM", Vec2(), Bf.MouseButtonMiddle.i))
                    vars[2]++
                if (ImGui.buttonEx("ButtonLR", Vec2(), Bf.MouseButtonLeft or Bf.MouseButtonRight))
                    vars[3]++

                if (ImGui.buttonEx("ButtonL-release", Vec2(), Bf.MouseButtonLeft or Bf.PressedOnRelease))
                    vars[4]++
                if (ImGui.buttonEx("ButtonR-release", Vec2(), Bf.MouseButtonRight or Bf.PressedOnRelease)) {
                    ctx.logDebug("Pressed!")
                    vars[5]++
                }
                for (n in vars.indices)
                    ImGui.text("$n: ${vars[n]}")
            }
        }
        t.testFunc = { ctx: TestContext ->

            val vars = ctx.userData as IntArray

            ctx.setRef("Test Window")
            ctx.itemClick("ButtonL", 0)
            vars[0] shouldBe 1
            ctx.itemClick("ButtonR", 1)
            vars[1] shouldBe 1
            ctx.itemClick("ButtonM", 2)
            vars[2] shouldBe 1
            ctx.itemClick("ButtonLR", 0)
            ctx.itemClick("ButtonLR", 1)
            vars[3] shouldBe 2

            vars[3] = 0
            ctx.mouseMove("ButtonLR")
            ctx.mouseDown(0)
            ctx.mouseDown(1)
            ctx.mouseUp(0)
            ctx.mouseUp(1)
            vars[3] shouldBe 1

            vars[3] = 0
            ctx.mouseMove("ButtonLR")
            ctx.mouseDown(0)
            ctx.mouseMove("ButtonR", TestOpFlag.NoCheckHoveredId.i)
            ctx.mouseDown(1)
            ctx.mouseUp(0)
            ctx.mouseMove("ButtonLR")
            ctx.mouseUp(1)
            vars[3] shouldBe 0
        }
    }

    e.registerTest("widgets", "widgets_button_status").let { t ->
        t.userData = ButtonStateTestVars()
        t.guiFunc = { ctx: TestContext ->

            val vars = ctx.userData as ButtonStateTestVars
            val status = vars.status

            ImGui.begin("Test Window", null, Wf.NoSavedSettings.i)

            val pressed = ImGui.button("Test")
            status.querySet()
            when (vars.nextStep) {
                ButtonStateMachineTestStep.Init -> {
                    pressed shouldBe false
                    status.hovered shouldBe 0
                    status.active shouldBe 0
                    status.activated shouldBe 0
                    status.deactivated shouldBe 0
                }
                ButtonStateMachineTestStep.MovedOver -> {
                    pressed shouldBe false
                    status.hovered shouldBe 1
                    status.active shouldBe 0
                    status.activated shouldBe 0
                    status.deactivated shouldBe 0
                }
                ButtonStateMachineTestStep.MouseDown -> {
                    pressed shouldBe false
                    status.hovered shouldBe 1
                    status.active shouldBe 1
                    status.activated shouldBe 1
                    status.deactivated shouldBe 0
                }
                ButtonStateMachineTestStep.MovedAway -> {
                    pressed shouldBe false
                    status.hovered shouldBe 0
                    status.active shouldBe 1
                    status.activated shouldBe 0
                    status.deactivated shouldBe 0
                }
                ButtonStateMachineTestStep.MovedOverAgain -> {
                    pressed shouldBe false
                    status.hovered shouldBe 1
                    status.active shouldBe 1
                    status.activated shouldBe 0
                    status.deactivated shouldBe 0
                }
                ButtonStateMachineTestStep.MouseUp -> {
                    pressed shouldBe true
                    status.hovered shouldBe 1
                    status.active shouldBe 0
                    status.activated shouldBe 0
                    status.deactivated shouldBe 1
                }
                ButtonStateMachineTestStep.Done -> {
                    pressed shouldBe false
                    status.hovered shouldBe 0
                    status.active shouldBe 0
                    status.activated shouldBe 0
                    status.deactivated shouldBe 0
                }
                else -> Unit
            }
            vars.nextStep = ButtonStateMachineTestStep.None

            // The "Unused" button allows to move the mouse away from the "Test" button
            ImGui.button("Unused")

            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->

            val vars = ctx.userData as ButtonStateTestVars
            vars.nextStep = ButtonStateMachineTestStep.None

            ctx.setRef("Test Window")

            // Move mouse away from "Test" button
            ctx.mouseMove("Unused")
            vars.nextStep = ButtonStateMachineTestStep.Init
            ctx.yield()

            ctx.mouseMove("Test")
            vars.nextStep = ButtonStateMachineTestStep.MovedOver
            ctx.yield()

            vars.nextStep = ButtonStateMachineTestStep.MouseDown
            ctx.mouseDown()

            ctx.mouseMove("Unused", TestOpFlag.NoCheckHoveredId.i)
            vars.nextStep = ButtonStateMachineTestStep.MovedAway
            ctx.yield()

            ctx.mouseMove("Test")
            vars.nextStep = ButtonStateMachineTestStep.MovedOverAgain
            ctx.yield()

            vars.nextStep = ButtonStateMachineTestStep.MouseUp
            ctx.mouseUp()

            ctx.mouseMove("Unused")
            vars.nextStep = ButtonStateMachineTestStep.Done
            ctx.yield()
        }
    }
}

class ButtonStateTestVars {
    var nextStep = ButtonStateMachineTestStep.None
    var status = TestGenericStatus()
}

// ## Test ButtonBehavior frame by frame behaviors (see comments at the top of the ButtonBehavior() function)
enum class ButtonStateMachineTestStep { None, Init, MovedOver, MouseDown, MovedAway, MovedOverAgain, MouseUp, Done }
