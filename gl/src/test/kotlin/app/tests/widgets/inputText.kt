package app.tests.widgets

import engine.TestEngine
import engine.context.*
import engine.engine.registerTest
import engine.inputText_
import glm_.i
import glm_.vec2.Vec2
import imgui.*
import imgui.api.gImGui
import imgui.classes.InputTextCallbackData
import imgui.stb.te
import io.kotest.matchers.shouldBe
import uno.kotlin.NUL
import imgui.WindowFlag as Wf

fun registerTests_Widgets_inputText(e: TestEngine) {

    // ## Test InputText widget
    e.registerTest("widgets", "widgets_inputtext_1").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            ImGui.setNextWindowSize(Vec2(200))
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                ImGui.inputText("InputText", vars.str1)
            }
        }
        t.testFunc = { ctx: TestContext ->

            val buf = ctx.genericVars.str1

            ctx.setRef("Test Window")

            // Insert
            "Hello".toByteArray(buf)
            ctx.itemClick("InputText")
            ctx.keyCharsAppendEnter("World123")
            buf.cStr shouldBe "HelloWorld123"

            // Delete
            ctx.itemClick("InputText")
            ctx.keyPressMap(Key.End)
            ctx.keyPressMap(Key.Backspace, KeyMod.None.i, 3)
            ctx.keyPressMap(Key.Enter)
            buf.cStr shouldBe "HelloWorld"

            // Insert, Cancel
            ctx.itemClick("InputText")
            ctx.keyPressMap(Key.End)
            ctx.keyChars("XXXXX")
            ctx.keyPressMap(Key.Escape)
            buf.cStr shouldBe "HelloWorld"

            // Delete, Cancel
            ctx.itemClick("InputText")
            ctx.keyPressMap(Key.End)
            ctx.keyPressMap(Key.Backspace, KeyMod.None.i, 5)
            ctx.keyPressMap(Key.Escape)
            buf.cStr shouldBe "HelloWorld"
        }
    }

    // ## Test InputText undo/redo ops, in particular related to issue we had with stb_textedit undo/redo buffers
    e.registerTest("widgets", "widgets_inputtext_2").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            if (vars.strLarge.isEmpty())
                vars.strLarge = ByteArray(10000)
            ImGui.setNextWindowSize(Vec2(ImGui.fontSize * 50, 0f))
            dsl.window("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                ImGui.text("strlen() = ${vars.strLarge.strlen()}")
                ImGui.inputText("Other", vars.str1, InputTextFlag.None.i)
                ImGui.inputTextMultiline("InputText", vars.strLarge, Vec2(-1f, ImGui.fontSize * 20), InputTextFlag.None.i)
            }
            //ImDebugShowInputTextState();
        }
        t.testFunc = { ctx: TestContext ->

            // https://github.com/nothings/stb/issues/321
            val vars = ctx.genericVars

            // Start with a 350 characters buffer.
            // For this test we don't inject the characters via pasting or key-by-key in order to precisely control the undo/redo state.
            val buf = vars.strLarge
            buf.strlen() shouldBe 0
            for (n in 0..9) {
                val bytes = "xxxxxxx abcdefghijklmnopqrstuvwxyz\n".toByteArray()
                val size = bytes.strlen()
                bytes.copyInto(buf, size * n)
            }
            buf.strlen() shouldBe 350

            ctx.setRef("Test Window")
            ctx.itemClick("Other") // This is to ensure stb_textedit_clear_state() gets called (clear the undo buffer, etc.)
            ctx.itemClick("InputText")

            val inputTextState = gImGui!!.inputTextState
            val undoState = inputTextState.stb.undoState
            inputTextState.id shouldBe gImGui!!.activeId
            undoState.undoPoint shouldBe 0
            undoState.undoCharPoint shouldBe 0
            undoState.redoPoint = te.UNDOSTATECOUNT
            undoState.redoCharPoint shouldBe te.UNDOCHARCOUNT
            te.UNDOCHARCOUNT shouldBe 999 // Test designed for this value

            // Insert 350 characters via 10 paste operations
            // We use paste operations instead of key-by-key insertion so we know our undo buffer will contains 10 undo points.
            //const char line_buf[26+8+1+1] = "xxxxxxx abcdefghijklmnopqrstuvwxyz\n"; // 8+26+1 = 35
            //ImGui::SetClipboardText(line_buf);
            //IM_CHECK(strlen(line_buf) == 35);
            //ctx->KeyPressMap(ImGuiKey_V, ImGuiKeyModFlags_Shortcut, 10);

            // Select all, copy, paste 3 times
            ctx.keyPressMap(Key.A, KeyMod.Shortcut.i)    // Select all
            ctx.keyPressMap(Key.C, KeyMod.Shortcut.i)    // Copy
            ctx.keyPressMap(Key.End, KeyMod.Shortcut.i)  // Go to end, clear selection
            ctx.sleepShort()
            for (n in 0..2) {
                ctx.keyPressMap(Key.V, KeyMod.Shortcut.i)// Paste append three times
                ctx.sleepShort()
            }
            var len = vars.strLarge.strlen()
            len shouldBe (350 * 4)
            undoState.undoPoint shouldBe 3
            undoState.undoCharPoint shouldBe 0

            // Undo x2
            undoState.redoPoint shouldBe te.UNDOSTATECOUNT
            ctx.keyPressMap(Key.Z, KeyMod.Shortcut.i)
            ctx.keyPressMap(Key.Z, KeyMod.Shortcut.i)
            len = vars.strLarge.strlen()
            len shouldBe (350 * 2)
            undoState.undoPoint shouldBe 1
            undoState.redoPoint shouldBe (te.UNDOSTATECOUNT - 2)
            undoState.redoCharPoint shouldBe (te.UNDOCHARCOUNT - 350 * 2)

            // Undo x1 should call stb_textedit_discard_redo()
            ctx.keyPressMap(Key.Z, KeyMod.Shortcut.i)
            len = vars.strLarge.strlen()
            len shouldBe (350 * 1)
        }
    }

    // ## Test InputText vs user ownership of data
    e.registerTest("widgets", "widgets_inputtext_3_text_ownership").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            dsl.window("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                ImGui.logToBuffer()
                ImGui.inputText("##InputText", vars.str1) // Remove label to simplify the capture/comparison
                ctx.uiContext!!.logBuffer.toString().toByteArray(vars.str2)
                ImGui.logFinish()
                ImGui.text("Captured: \"${vars.str2.cStr}\"")
            }
        }
        t.testFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            val bufUser = vars.str1
            val bufVisible = vars.str2
            ctx.setRef("Test Window")

            bufVisible.cStr shouldBe ""
            "Hello".toByteArray(bufUser)
            ctx.yield()
            bufVisible.cStr shouldBe "Hello"
            ctx.itemClick("##InputText")
            ctx.keyCharsAppend("1")
            ctx.yield()
            bufUser.cStr shouldBe "Hello1"
            bufVisible.cStr shouldBe "Hello1"

            // Because the item is active, it owns the source data, so:
            "Overwritten".toByteArray(bufUser)
            ctx.yield()
            bufUser.cStr shouldBe "Hello1"
            bufVisible.cStr shouldBe "Hello1"

            // Lose focus, at this point the InputTextState->ID should be holding on the last active state,
            // so we verify that InputText() is picking up external changes.
            ctx.keyPressMap(Key.Escape)
            ctx.uiContext!!.activeId shouldBe 0
            "Hello2".toByteArray(bufUser)
            ctx.yield()
            bufUser.cStr shouldBe "Hello2"
            bufVisible.cStr shouldBe "Hello2"
        }
    }

    // ## Test that InputText doesn't go havoc when activated via another item
    e.registerTest("widgets", "widgets_inputtext_4_id_conflict").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            ImGui.setNextWindowSize(Vec2(ImGui.fontSize * 50, 0f))
            dsl.window("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize) {
                if (ctx.frameCount < 50)
                    ImGui.button("Hello")
                else
                    ImGui.inputText("Hello", vars.str1)
            }
        }
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Test Window")
            ctx.itemHoldForFrames("Hello", 100)
        }
    }

    // ## Test that InputText doesn't append two tab characters if the backend supplies both tab key and character
    e.registerTest("widgets", "widgets_inputtext_5_tab_double_insertion").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                ImGui.inputText("Field", vars.str1, InputTextFlag.AllowTabInput.i)
            }
        }
        t.testFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            ctx.setRef("Test Window")
            ctx.itemClick("Field")
            ctx.uiContext!!.io.addInputCharacter('\t')
            ctx.keyPressMap(Key.Tab)
            vars.str1.cStr shouldBe "\t"
        }
    }

    // ## Test input clearing action (ESC key) being undoable (#3008).
    e.registerTest("widgets", "widgets_inputtext_6_esc_undo").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                ImGui.inputText("Field", vars.str1)
            }

        }
        t.testFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            // FIXME-TESTS: Facilitate usage of variants
            val testCount = if (ctx.hasDock) 2 else 1
            for (testN in 0 until testCount) {
                ctx.logDebug("TEST CASE $testN")
                val initialValue = if (testN == 0) "" else "initial"
                initialValue.toByteArray(vars.str1)
                ctx.setRef("Test Window")
                ctx.itemInput("Field")
                ctx.keyCharsReplace("text")
                vars.str1.cStr shouldBe "text"
                ctx.keyPressMap(Key.Escape)                      // Reset input to initial value.
                vars.str1.cStr shouldBe initialValue
                ctx.itemInput("Field")
                ctx.keyPressMap(Key.Z, KeyMod.Shortcut.i)    // Undo
                vars.str1.cStr shouldBe "text"
                ctx.keyPressMap(Key.Enter)                       // Unfocus otherwise test_n==1 strcpy will fail
            }
        }
    }

    // ## Test resize callback (#3009, #2006, #1443, #1008)
    e.registerTest("widgets", "widgets_inputtext_7_resizecallback").let { t ->
        t.userData = ByteArray(0)
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.userData as ByteArray
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                if (ImGui.inputText_("Field1", vars, InputTextFlag.EnterReturnsTrue.i)) {
                    vars.strlen() shouldBe (4 + 5)
                    vars.cStr shouldBe "abcdhello"
                }
                // [JVM] increase buffer size in order to avoid reassignment inside ImGui::inputText when
                // calling the callback which would create a new (bigger) ByteArray, breaking our
                // ::strLocalUnsaved reference.
                // This is a jvm limit, cpp works because it passes pointers around
                val strLocalUnsaved = "abcd".toByteArray(32)
                if (ImGui.inputText_("Field2", strLocalUnsaved, InputTextFlag.EnterReturnsTrue.i)) {
                    strLocalUnsaved.strlen() shouldBe (4 + 5)
                    strLocalUnsaved.cStr shouldBe "abcdhello"
                }
            }
        }
        t.testFunc = { ctx: TestContext ->
            // size 32 for the same reason as right above
            ctx.userData = "abcd".toByteArray(32).also {
                it.strlen() shouldBe 4
            }
            ctx.setRef("Test Window")
            ctx.itemInput("Field1")
            ctx.keyCharsAppendEnter("hello")
            ctx.itemInput("Field2")
            ctx.keyCharsAppendEnter("hello")
        }
    }


    // ## Test input text multiline cursor movement: left, up, right, down, origin, end, ctrl+origin, ctrl+end, page up, page down
    e.registerTest("widgets", "widgets_inputtext_8_cursor").let { t ->
        class InputTextCursorVars {
            lateinit var str: String
            var cursor = 0
            var lineCount: Int = 10
        }
        t.userData = InputTextCursorVars()
        t.guiFunc = { ctx: TestContext ->

            val vars = ctx.getUserData<InputTextCursorVars>()

            val height = vars.lineCount * 0.5f * gImGui!!.fontSize
            ImGui.begin("Test Window", null, Wf.NoSavedSettings or Wf.AlwaysAutoResize)
            ImGui.inputTextMultiline("Field", vars.str, Vec2(300, height), InputTextFlag.EnterReturnsTrue.i) // [JVM] Check str reference, need byteBuffer?
            ImGui.getInputTextState(ctx.getID("/Test Window/Field"))?.let { state ->
                ImGui.text("Stb Cursor: ${state.stb.cursor}")
            }
            ImGui.end()
        }

        val charCountPerLine = 10

        var pageSize: Int

        var cursorPosBeginOfFirstLine: Int
        var cursorPosEndOfFirstLine: Int
        var cursorPosMiddleOfFirstLine: Int
        var cursorPosEndOfLastLine: Int
        var cursorPosBeginOfLastLine: Int
//        var cursorPosMiddleOfLastLine = 0
        var cursorPosMiddle: Int

        t.testFunc = { ctx: TestContext ->

            val vars = ctx.getUserData<InputTextCursorVars>()
            ctx.setRef("Test Window")

            val str = StringBuilder()
            for (n in 0 until vars.lineCount) {
                for (c in 0 until (charCountPerLine - 1)) // \n is part of our char_count_per_line
                    str += "$n"
                if (n < vars.lineCount - 1)
                    vars.str += "\n"
            }
            vars.str = str.toString()
            ctx.itemInput("Field")

            val state = ImGui.getInputTextState(ctx.getID("Field"))!!
            val stb = state.stb
            vars.cursor = stb.cursor

            pageSize = (vars.lineCount / 2) - 1

            cursorPosBeginOfFirstLine = 0
            cursorPosEndOfFirstLine = charCountPerLine - 1
            cursorPosMiddleOfFirstLine = charCountPerLine / 2
            cursorPosEndOfLastLine = vars.str.length
            cursorPosBeginOfLastLine = cursorPosEndOfLastLine - charCountPerLine + 1
//            cursorPosMiddleOfLastLine = cursorPosEndOfLastLine - charCountPerLine / 2
            cursorPosMiddle = vars.str.length / 2

            val setCursorPosition = { cursor: Int -> stb.cursor = cursor; stb.hasPreferredX = false; }

            // Do all the test twice: with no trailing \n, and with.
            for (i in 0..1) {
                val hasTrailingLineFeed = i == 1
                if (hasTrailingLineFeed) {
                    setCursorPosition(cursorPosEndOfLastLine)
                    ctx.keyCharsAppend("\n")
                }
                val eof = vars.str.length

                // Begin of File
                setCursorPosition(0); ctx.keyPressMap(Key.UpArrow)
                stb.cursor shouldBe 0
                setCursorPosition(0); ctx.keyPressMap(Key.LeftArrow)
                stb.cursor shouldBe 0
                setCursorPosition(0); ctx.keyPressMap(Key.DownArrow)
                stb.cursor shouldBe charCountPerLine
                setCursorPosition(0); ctx.keyPressMap(Key.RightArrow)
                stb.cursor shouldBe 1

                // End of first line
                setCursorPosition(cursorPosEndOfFirstLine); ctx.keyPressMap(Key.UpArrow)
                stb.cursor shouldBe cursorPosEndOfFirstLine
                setCursorPosition(cursorPosEndOfFirstLine); ctx.keyPressMap(Key.LeftArrow)
                stb.cursor shouldBe (cursorPosEndOfFirstLine - 1)
                setCursorPosition(cursorPosEndOfFirstLine); ctx.keyPressMap(Key.DownArrow)
                stb.cursor shouldBe (cursorPosEndOfFirstLine + charCountPerLine)
                setCursorPosition(cursorPosEndOfFirstLine); ctx.keyPressMap(Key.RightArrow)
                stb.cursor shouldBe (cursorPosEndOfFirstLine + 1)

                // Begin of last line
                setCursorPosition(cursorPosBeginOfLastLine); ctx.keyPressMap(Key.UpArrow)
                stb.cursor shouldBe (cursorPosBeginOfLastLine - charCountPerLine)
                setCursorPosition(cursorPosBeginOfLastLine); ctx.keyPressMap(Key.LeftArrow)
                stb.cursor shouldBe (cursorPosBeginOfLastLine - 1)
                setCursorPosition(cursorPosBeginOfLastLine); ctx.keyPressMap(Key.DownArrow)
                stb.cursor shouldBe if (hasTrailingLineFeed) eof else cursorPosBeginOfLastLine
                setCursorPosition(cursorPosBeginOfLastLine); ctx.keyPressMap(Key.RightArrow)
                stb.cursor shouldBe (cursorPosBeginOfLastLine + 1)

                // End of last line
                setCursorPosition(cursorPosEndOfLastLine); ctx.keyPressMap(Key.UpArrow)
                //IM_CHECK_EQ(stb.cursor, cursor_pos_end_of_last_line - char_count_per_line); // FIXME: This one is broken even on master
                setCursorPosition(cursorPosEndOfLastLine); ctx.keyPressMap(Key.LeftArrow)
                stb.cursor shouldBe (cursorPosEndOfLastLine - 1)
                setCursorPosition(cursorPosEndOfLastLine); ctx.keyPressMap(Key.DownArrow)
                stb.cursor shouldBe if (hasTrailingLineFeed) eof else cursorPosEndOfLastLine
                setCursorPosition(cursorPosEndOfLastLine); ctx.keyPressMap(Key.RightArrow)
                stb.cursor shouldBe (cursorPosEndOfLastLine + hasTrailingLineFeed.i)

                // In the middle of the content
                setCursorPosition(cursorPosMiddle); ctx.keyPressMap(Key.UpArrow)
                stb.cursor shouldBe (cursorPosMiddle - charCountPerLine)
                setCursorPosition(cursorPosMiddle); ctx.keyPressMap(Key.LeftArrow)
                stb.cursor shouldBe (cursorPosMiddle - 1)
                setCursorPosition(cursorPosMiddle); ctx.keyPressMap(Key.DownArrow)
                stb.cursor shouldBe (cursorPosMiddle + charCountPerLine)
                setCursorPosition(cursorPosMiddle); ctx.keyPressMap(Key.RightArrow)
                stb.cursor shouldBe (cursorPosMiddle + 1)

                // Home/End to go to beginning/end of the line
                setCursorPosition(cursorPosMiddle); ctx.keyPressMap(Key.Home)
                stb.cursor shouldBe ((vars.lineCount / 2 - 1) * charCountPerLine)
                setCursorPosition(cursorPosMiddle); ctx.keyPressMap(Key.End)
                stb.cursor shouldBe ((vars.lineCount / 2) * charCountPerLine - 1)

                // Ctrl+Home/End to go to beginning/end of the text
                setCursorPosition(cursorPosMiddle); ctx.keyPressMap(Key.Home, KeyMod.Ctrl.i)
                stb.cursor shouldBe 0
                setCursorPosition(cursorPosMiddle); ctx.keyPressMap(Key.End, KeyMod.Ctrl.i)
                stb.cursor shouldBe (cursorPosEndOfLastLine + hasTrailingLineFeed.i)

                // PageUp/PageDown
                setCursorPosition(cursorPosBeginOfFirstLine); ctx.keyPressMap(Key.PageDown)
                stb.cursor shouldBe (cursorPosBeginOfFirstLine + charCountPerLine * pageSize)
                ctx.keyPressMap(Key.PageUp)
                stb.cursor shouldBe cursorPosBeginOfFirstLine

                setCursorPosition(cursorPosMiddleOfFirstLine)
                ctx.keyPressMap(Key.PageDown)
                stb.cursor shouldBe (cursorPosMiddleOfFirstLine + charCountPerLine * pageSize)
                ctx.keyPressMap(Key.PageDown)
                stb.cursor shouldBe (cursorPosMiddleOfFirstLine + charCountPerLine * pageSize * 2)
                ctx.keyPressMap(Key.PageDown)
                stb.cursor shouldBe if (hasTrailingLineFeed) eof else eof - charCountPerLine / 2 + 1

                // We started PageDown from the middle of a line, so even if we're at the end (with X = 0),
                // PageUp should bring us one page up to the middle of the line
                val cursorPosBeginCurrentLine = (stb.cursor / charCountPerLine) * charCountPerLine // Round up cursor position to decimal only
                ctx.keyPressMap(Key.PageUp)
                stb.cursor shouldBe (cursorPosBeginCurrentLine - pageSize * charCountPerLine + charCountPerLine / 2)
                //eof - (char_count_per_line * page_size) + (char_count_per_line / 2) + (has_trailing_line_feed ? 0 : 1));
            }
        }
    }
    // ## Test input text multiline cursor with selection: left, up, right, down, origin, end, ctrl+origin, ctrl+end, page up, page down
    // ## Test input text multiline scroll movement only: ctrl + (left, up, right, down)
    // ## Test input text multiline page up/page down history ?

    // ## Test character replacement in callback (inspired by https://github.com/ocornut/imgui/pull/3587)
    e.registerTest("widgets", "widgets_inputtext_callback_replace").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            ImGui.begin("Test Window", null, Wf.NoSavedSettings.i)
            val callback: InputTextCallback = { data: InputTextCallbackData ->
                if (data.cursorPos >= 3 && String(data.buf, data.cursorPos - 3, 3) == "abc") {
                    data.deleteChars(data.cursorPos - 3, 3)
                    TODO()
//                data.insertChars(data.cursorPos, "\xE5\xA5\xBD") // HAO
//                data.selectionStart = data->CursorPos - 3
//                data->SelectionEnd = data->CursorPos
//                return 1
                } else
                    false
            }
            ImGui.inputText("Hello", vars.str1, InputTextFlag.CallbackAlways.i, callback)
            ImGui.end()
        }
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Test Window")
            ctx.itemInput("Hello")
            val state = ctx.uiContext!!.inputTextState
            state.id shouldBe ctx.getID("Hello")
            ctx.keyCharsAppend("ab")
            state.curLenA shouldBe 2
            state.curLenW shouldBe 2
            String(state.textA) shouldBe  "ab"
            state.stb.cursor shouldBe 2
            ctx.keyCharsAppend("c")
            state.curLenA shouldBe 3
            state.curLenW shouldBe 1
//            String(state.textA), "\xE5\xA5\xBD") == 0) TODO
            state.textW[0] shouldBe '\u597D'
            state.textW[1] shouldBe NUL
            state.stb.cursor shouldBe 1
            state.stb.selectStart == 0 && state.stb.selectEnd == 1
        }
    }

    // ## Test for Nav interference
    e.registerTest("widgets", "widgets_inputtext_nav").let { t ->
        t.guiFunc = { ctx: TestContext ->
            val vars = ctx.genericVars
            dsl.window("Test Window", null, Wf.NoSavedSettings.i) {
                val sz = Vec2(50, 0)
                ImGui.button("UL", sz); ImGui.sameLine()
                ImGui.button("U", sz); ImGui.sameLine()
                ImGui.button("UR", sz)
                ImGui.button("L", sz); ImGui.sameLine()
                ImGui.setNextItemWidth(sz.x)
                ImGui.inputText("##Field", vars.str1, InputTextFlag.AllowTabInput.i)
                ImGui.sameLine()
                ImGui.button("R", sz)
                ImGui.button("DL", sz); ImGui.sameLine()
                ImGui.button("D", sz); ImGui.sameLine()
                ImGui.button("DR", sz)
            }
        }
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Test Window")
            ctx.itemClick("##Field")
            ctx.keyPressMap(Key.LeftArrow)
            ctx.uiContext!!.navId shouldBe ctx.getID("##Field")
            ctx.keyPressMap(Key.RightArrow)
            ctx.uiContext!!.navId shouldBe ctx.getID("##Field")
            ctx.keyPressMap(Key.UpArrow)
            ctx.uiContext!!.navId shouldBe ctx.getID("U")
            ctx.keyPressMap(Key.DownArrow)
            ctx.keyPressMap(Key.DownArrow)
            ctx.uiContext!!.navId shouldBe ctx.getID("D")
        }
    }
}