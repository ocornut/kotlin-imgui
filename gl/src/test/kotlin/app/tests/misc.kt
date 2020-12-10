package app.tests

import engine.TestEngine
import engine.context.*
import engine.engine.TestItemList
import engine.engine.TestOpFlag
import engine.engine.TestRef
import engine.engine.registerTest
import engine.hashDecoratedPath
import glm_.*
import glm_.vec2.Vec2
import imgui.*
import imgui.api.gImGui
import imgui.classes.TextFilter
import imgui.font.FontAtlas
import imgui.font.FontConfig
import imgui.font.FontGlyphRangesBuilder
import imgui.internal.*
import imgui.internal.classes.Pool
import imgui.internal.classes.PoolIdx
import imgui.internal.classes.Rect
import imgui.internal.classes.TabBar
import imgui.internal.sections.ItemStatusFlag
import imgui.internal.sections.hasnt
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import unsigned.Ubyte
import unsigned.Uint
import unsigned.Ulong
import unsigned.Ushort

fun registerTests_Misc(e: TestEngine) {

    // ## Test watchdog
//    #if 0
//    t = REGISTER_TEST("misc", "misc_watchdog");
//    t->TestFunc = [](ImGuiTestContext* ctx)
//    {
//        while (true)
//            ctx->Yield();
//    };
//    #endif

    // ## Test window data garbage collection
    e.registerTest("misc", "misc_gc").let { t ->
        t.guiFunc = { ctx: TestContext ->
            // Pretend window is no longer active once we start testing.
            if (ctx.frameCount < 2)
                for (i in 0..4) {
                    val name = "GC Test $i"
                    dsl.window(name, null, WindowFlag.NoSavedSettings.i) {
                        ImGui.textUnformatted(name)
                    }
                }
        }
        t.testFunc = { ctx: TestContext ->
            ctx.logDebug("Check normal state")
            for (i in 0..2) {
                val window = ctx.getWindowByRef("GC Test $i")!!
                assert(!window.memoryCompacted)
                assert(window.drawList.cmdBuffer.isNotEmpty())
            }

            var backupTimer = 0f
            fun swap() {
                val tmp = backupTimer
                backupTimer = ctx.uiContext!!.io.configMemoryCompactTimer
                ctx.uiContext!!.io.configMemoryCompactTimer = tmp
            }

            swap()

            ctx.yield(3) // Give time to perform GC
            ctx.logDebug("Check GC-ed state")
            for (i in 0..2) {
                val window = ctx.getWindowByRef("GC Test $i")!!
                assert(window.memoryCompacted)
                assert(window.idStack.isEmpty())
                assert(window.drawList.cmdBuffer.isEmpty())
            }
            swap()
        }
    }

    // ## Test hash functions and ##/### operators
    e.registerTest("misc", "misc_hash_001").let { t ->
        t.testFunc = {
            // Test hash function for the property we need
            assert(hash("helloworld") == hash("world", 0, hash("hello", 0)))  // String concatenation
            assert(hash("hello###world") == hash("###world"))                      // ### operator reset back to the seed
            assert(hash("hello###world", 0, 1234) == hash("###world", 0, 1234))    // ### operator reset back to the seed
            assert(hash("helloxxx", 5) == hash("hello"))                           // String size is honored
            assert(hash("", 0, 0) == 0)                                          // Empty string doesn't alter hash
            assert(hash("", 0, 1234) == 1234)                                    // Empty string doesn't alter hash
            assert(hash("hello", 5) == hash("hello", 5))                          // FIXME: Do we need to guarantee this?

            val data = intArrayOf(42, 50)
            assert(hash(data) == hash(data[1], hash(data[0])))
            assert(hash("", 0, 1234) == 1234)                                   // Empty data doesn't alter hash

            // Verify that Test Engine high-level hash wrapper works
            assert(hashDecoratedPath("Hello/world") == hash("Helloworld"))            // Slashes are ignored
            assert(hashDecoratedPath("Hello\\/world") == hash("Hello/world"))         // Slashes can be inhibited
            assert(hashDecoratedPath("/Hello", null, 42) == hashDecoratedPath("Hello"))        // Leading / clears seed
        }
    }

    // ## Test ImVector functions
    e.registerTest("misc", "misc_vector_001").let { t ->
        t.testFunc = {
            val v = ArrayList<Int>()
            assert(v.isEmpty())
            v += 0
            v += 1
            assert(v.size == 2)
            v += 2
            var r = v.remove(1)
            assert(r)
            assert(v.size == 2)
            r = v.remove(1)
            assert(!r)
            assert(0 in v)
            assert(2 in v)
//            v.resize(0);
//            IM_CHECK(v.Data != NULL && v.Capacity >= 3);
            v.clear()
            assert(v.size == 0)
//            val maxSize = v.max_size();
//            IM_CHECK(maxSize == INT_MAX / sizeof(int))
        }
    }

//    // ## Test ImVector functions
//    #ifdef IMGUI_HAS_TABLE
//            t = REGISTER_TEST("misc", "misc_bitarray");
//    t->TestFunc = [](ImGuiTestContext* ctx)
//    {
//        ImBitArray<128> v128;
//        IM_CHECK_EQ(sizeof(v128), 16);
//        v128.ClearBits();
//        v128.SetBitRange(1, 1);
//        IM_CHECK(v128.Storage[0] == 0x00000002 && v128.Storage[1] == 0x00000000 && v128.Storage[2] == 0x00000000);
//        v128.ClearBits();
//        v128.SetBitRange(1, 31);
//        IM_CHECK(v128.Storage[0] == 0xFFFFFFFE && v128.Storage[1] == 0x00000000 && v128.Storage[2] == 0x00000000);
//        v128.ClearBits();
//        v128.SetBitRange(1, 32);
//        IM_CHECK(v128.Storage[0] == 0xFFFFFFFE && v128.Storage[1] == 0x00000001 && v128.Storage[2] == 0x00000000);
//        v128.ClearBits();
//        v128.SetBitRange(0, 64);
//        IM_CHECK(v128.Storage[0] == 0xFFFFFFFF && v128.Storage[1] == 0xFFFFFFFF && v128.Storage[2] == 0x00000001);
//
//        ImBitArray<129> v129;
//        IM_CHECK_EQ(sizeof(v129), 20);
//        v129.SetBit(128);
//        IM_CHECK(v129.TestBit(128) == true);
//    };
//    #endif

    // ## Test ImPool functions
    e.registerTest("misc", "misc_pool_001").let { t ->
        t.testFunc = {
            val pool = Pool { TabBar() }
            pool.getOrAddByKey(0x11)
            pool.getOrAddByKey(0x22) // May invalidate first point
            val t1 = pool[0x11]!!
            val t2 = pool[0x22]!!
//            assert(t1 != null && t2 != null)
            assert(pool.buf[pool.getIndex(t1).i + 1] === t2)
            assert(pool.getIndex(t1) == PoolIdx(0))
            assert(pool.getIndex(t2) == PoolIdx(1))
            assert(t1 in pool && t2 in pool)
            assert(TabBar() !in pool)
            assert(pool[pool.getIndex(t1)] === t1)
            assert(pool[pool.getIndex(t2)] === t2)
            val t3 = pool.getOrAddByKey(0x33)
            assert(pool.getIndex(t3) == PoolIdx(2))
            assert(pool.size == 3)
            pool.remove(0x22, pool[0x22]!!)
            assert(pool[0x22] == null)
            assert(pool.size == 2) // [JVM] different from native, 3
            val t4 = pool.getOrAddByKey(0x40)
            assert(pool.getIndex(t4) == PoolIdx(2)) // [JVM] different from native, 1
            assert(pool.size == 3)
            pool.clear()
            assert(pool.size == 0)
        }
    }

    // ## Test behavior of ImParseFormatTrimDecorations
    e.registerTest("misc", "misc_format_parse").let { t ->
        t.testFunc = {
            // fmt = "blah blah"  -> return fmt
            // fmt = "%.3f"       -> return fmt
            // fmt = "hello %.3f" -> return fmt + 6
            // fmt = "%.3f hello" -> return buf, "%.3f"
            //const char* ImGui::ParseFormatTrimDecorations(const char* fmt, char* buf, int buf_size)

            var fmt = "blah blah"
            var out = ImGui.parseFormatTrimDecorations(fmt)
            assert(out == fmt)

            fmt = "%.3f"
            out = ImGui.parseFormatTrimDecorations(fmt)
            assert(out == fmt)

            fmt = "hello %.3f"
            out = ImGui.parseFormatTrimDecorations(fmt)
            assert(out == fmt.substring(6))
            assert(out == "%.3f")

            fmt = "%%hi%.3f"
            out = ImGui.parseFormatTrimDecorations(fmt)
            assert(out == fmt.substring(4))
            assert(out == "%.3f")

            fmt = "hello %.3f ms"
            out = ImGui.parseFormatTrimDecorations(fmt)
//            assert(out == buf)
            assert(out == "%.3f")

            fmt = "hello %f blah"
            out = ImGui.parseFormatTrimDecorations(fmt)
//            IM_CHECK(out == buf)
            assert(out == "%f")
        }
    }

    // TODO sync
//    t = IM_REGISTER_TEST(e, "misc", "misc_clipper");

    // ## Test ImFontAtlas building with overlapping glyph ranges (#2353, #2233)
    e.registerTest("misc", "misc_atlas_build_glyph_overlap").let { t ->
        t.testFunc = {
            val atlas = FontAtlas()
            val fontConfig = FontConfig()
            fontConfig.glyphRanges = defaultRanges
            atlas.addFontDefault(fontConfig)
            atlas.build()
            atlas.clear()
        }
    }

    e.registerTest("misc", "misc_atlas_ranges_builder").let { t ->
        t.testFunc = {
            val builder = FontGlyphRangesBuilder()
            builder.addChar(31)
            builder.addChar(0x10000 - 1)
            var outRanges = builder.buildRanges()
            builder.clear()
            outRanges.size shouldBe (2*2+1)
//            builder.addText("\xe6\x97\xa5\xe6\x9c\xac\xe8\xaa\x9e"); // "Ni-hon-go"
            outRanges = builder.buildRanges()
            outRanges.size shouldBe (3*2+1)
        }
    }

    e.registerTest("misc", "misc_repeat_typematic").let { t ->
        t.testFunc = { ctx: TestContext ->
            ctx.logDebug("Regular repeat delay/rate")
            assert(ImGui.calcTypematicRepeatAmount(0.00f, 0.00f, 1.0f, 0.2f) == 1) // Trigger @ 0.0f, 1.0f, 1.2f, 1.4f, etc.
            assert(ImGui.calcTypematicRepeatAmount(0.00f, 0.99f, 1.0f, 0.2f) == 0) // "
            assert(ImGui.calcTypematicRepeatAmount(0.99f, 1.00f, 1.0f, 0.2f) == 1) // "
            assert(ImGui.calcTypematicRepeatAmount(0.99f, 1.01f, 1.0f, 0.2f) == 1) // "
            assert(ImGui.calcTypematicRepeatAmount(0.99f, 1.41f, 1.0f, 0.2f) == 3) // "
            assert(ImGui.calcTypematicRepeatAmount(1.01f, 1.41f, 1.0f, 0.2f) == 2) // "

            assert(ImGui.calcTypematicRepeatAmount(0.99f, 1.01f, 1.1f, 0.2f) == 0) // Trigger @ 0.0f, 1.1f, 1.3f, 1.5f, etc.

            assert(ImGui.calcTypematicRepeatAmount(0.99f, 1.01f, 0.1f, 1.0f) == 0) // Trigger @ 0.0f, 0.1f, 1.1f, 2.1f, etc.
            assert(ImGui.calcTypematicRepeatAmount(0.99f, 1.11f, 0.1f, 1.0f) == 1) // "

            ctx.logDebug("No repeat delay")
            assert(ImGui.calcTypematicRepeatAmount(0.00f, 0.00f, 0.0f, 0.2f) == 1) // Trigger @ 0.0f, 0.2f, 0.4f, 0.6f, etc.
            assert(ImGui.calcTypematicRepeatAmount(0.19f, 0.20f, 0.0f, 0.2f) == 1) // Trigger @ 0.0f, 0.2f, 0.4f, 0.6f, etc.
            assert(ImGui.calcTypematicRepeatAmount(0.20f, 0.20f, 0.0f, 0.2f) == 0) // Trigger @ 0.0f, 0.2f, 0.4f, 0.6f, etc.
            assert(ImGui.calcTypematicRepeatAmount(0.19f, 1.01f, 0.0f, 0.2f) == 5) // Trigger @ 0.0f, 0.2f, 0.4f, 0.6f, etc.

            ctx.logDebug("No repeat rate")
            assert(ImGui.calcTypematicRepeatAmount(0.00f, 0.00f, 1.0f, 0.0f) == 1) // Trigger @ 0.0f, 1.0f
            assert(ImGui.calcTypematicRepeatAmount(0.99f, 1.01f, 1.0f, 0.0f) == 1) // "
            assert(ImGui.calcTypematicRepeatAmount(1.01f, 2.00f, 1.0f, 0.0f) == 0) // "

            ctx.logDebug("No repeat delay/rate")
            assert(ImGui.calcTypematicRepeatAmount(0.00f, 0.00f, 0.0f, 0.0f) == 1) // Trigger @ 0.0f
            assert(ImGui.calcTypematicRepeatAmount(0.01f, 1.01f, 0.0f, 0.0f) == 0) // "
        }
    }

    // ## Test ImGui::InputScalar() handling overflow for different data types
    e.registerTest("misc", "misc_input_scalar_overflow").let { t ->
        t.testFunc = {
            run {
                val one: Byte = 1
                var value: Byte = 2
                value = ImGui.dataTypeApplyOp(DataType.Byte, '+', value, one)
                assert(value == 3.b)
                value = Byte.MAX_VALUE
                value = ImGui.dataTypeApplyOp(DataType.Byte, '+', value, one)
                assert(value == Byte.MAX_VALUE)
                value = Byte.MIN_VALUE
                value = ImGui.dataTypeApplyOp(DataType.Byte, '-', value, one)
                assert(value == Byte.MIN_VALUE)
            }
            run {
                val one = Ubyte(1)
                var value = Ubyte(2)
                value = ImGui.dataTypeApplyOp(DataType.Ubyte, '+', value, one)
                assert(value == Ubyte(3))
                value = Ubyte.MAX
                value = ImGui.dataTypeApplyOp(DataType.Ubyte, '+', value, one)
                assert(value == Ubyte.MAX)
                value = Ubyte(0)
                value = ImGui.dataTypeApplyOp(DataType.Ubyte, '-', value, one)
                assert(value == Ubyte(0))
            }
            run {
                val one: Short = 1
                var value: Short = 2
                value = ImGui.dataTypeApplyOp(DataType.Short, '+', value, one)
                assert(value == 3.s)
                value = Short.MAX_VALUE
                value = ImGui.dataTypeApplyOp(DataType.Short, '+', value, one)
                assert(value == Short.MAX_VALUE)
                value = Short.MIN_VALUE
                value = ImGui.dataTypeApplyOp(DataType.Short, '-', value, one)
                assert(value == Short.MIN_VALUE)
            }
            run {
                val one = Ushort(1)
                var value = Ushort(2)
                value = ImGui.dataTypeApplyOp(DataType.Ushort, '+', value, one)
                assert(value == Ushort(3))
                value = Ushort.MAX
                value = ImGui.dataTypeApplyOp(DataType.Ushort, '+', value, one)
                assert(value == Ushort.MAX)
                value = Ushort(0)
                value = ImGui.dataTypeApplyOp(DataType.Ushort, '-', value, one)
                assert(value == Ushort(0))
            }
            run {
                val one = 1
                var value = 2
                value = ImGui.dataTypeApplyOp(DataType.Int, '+', value, one)
                assert(value == 3)
                value = Int.MAX_VALUE
                value = ImGui.dataTypeApplyOp(DataType.Int, '+', value, one)
                assert(value == Int.MAX_VALUE)
                value = Int.MIN_VALUE
                value = ImGui.dataTypeApplyOp(DataType.Int, '-', value, one)
                assert(value == Int.MIN_VALUE)
            }
            run {
                val one = Uint(1)
                var value = Uint(2)
                value = ImGui.dataTypeApplyOp(DataType.Uint, '+', value, one)
                assert(value == Uint(3))
                value = Uint.MAX
                value = ImGui.dataTypeApplyOp(DataType.Uint, '+', value, one)
                assert(value == Uint.MAX)
                value = Uint(0)
                value = ImGui.dataTypeApplyOp(DataType.Uint, '-', value, one)
                assert(value == Uint(0))
            }
            run {
                val one = 1L
                var value = 2L
                value = ImGui.dataTypeApplyOp(DataType.Long, '+', value, one)
                assert(value == 3L)
                value = Long.MAX_VALUE
                value = ImGui.dataTypeApplyOp(DataType.Long, '+', value, one)
                assert(value == Long.MAX_VALUE)
                value = Long.MIN_VALUE
                value = ImGui.dataTypeApplyOp(DataType.Long, '-', value, one)
                assert(value == Long.MIN_VALUE)
            }
            run {
                val one = Ulong(1)
                var value = Ulong(2)
                value = ImGui.dataTypeApplyOp(DataType.Ulong, '+', value, one)
                assert(value == Ulong(3))
                value = Ulong.MAX
                value = ImGui.dataTypeApplyOp(DataType.Ulong, '+', value, one)
                assert(value == Ulong.MAX)
                value = Ulong(0)
                value = ImGui.dataTypeApplyOp(DataType.Ulong, '-', value, one)
                assert(value == Ulong(0))
            }
        }
    }

    // ## Test basic clipboard, test that clipboard is empty on start
    e.registerTest("misc", "misc_clipboard").let { t ->
        t.testFunc = {
            // By specs, the testing system should provide an empty clipboard (we don't want user clipboard leaking into tests!)
            var clipboardText = ImGui.clipboardText
            assert(clipboardText == "")

            // Regular clipboard test
            val message = "Clippy is alive."
            ImGui.clipboardText = message
            clipboardText = ImGui.clipboardText
            assert(message == clipboardText)
        }
    }

    // ## Test UTF-8 encoding and decoding.
    // Note that this is ONLY testing encoding/decoding, we are not attempting to display those characters not trying to be i18n compliant
    e.registerTest("misc", "misc_utf8").let { t ->

        fun memCmp(a: CharArray, b: CharArray, num: Int): Boolean {
            for (i in 0 until num)
                if (a[i] != b[i])
                    return false
            return true
        }

        fun memCmp(a: ByteArray, b: ByteArray, num: Int): Boolean {
            for (i in 0 until num)
                if (a[i] != b[i])
                    return false
            return true
        }

        // FIXME-UTF8: Once Dear ImGui supports codepoints above 0xFFFF we should only use 32-bit code-point testing variant.
        fun checkUtf8(utf8_: String, unicode_: String): Boolean {
            val utf8 = utf8_.toByteArray()
            val unicode = unicode_.toCharArray()
//        IM_STATIC_ASSERT(sizeof(ImWchar) == sizeof(char16_t));
            val utf8Len = utf8.strlen()
            val maxChars = utf8Len * 4

            val converted = CharArray(maxChars)
            val reconverted = ByteArray(maxChars)

            // Convert UTF-8 text to unicode codepoints and check against expected value.
            var resultBytes = textStrFromUtf8(converted, utf8, utf8Len)
            var success = unicode.strlen == resultBytes && memCmp(converted, unicode, resultBytes)

            // Convert resulting unicode codepoints back to UTF-8 and check them against initial UTF-8 input value.
            if (success) {
                resultBytes = textStrToUtf8(reconverted, converted)
                success = success && utf8Len == resultBytes && memCmp(utf8, reconverted, resultBytes)
            }

            return success
        }

        val getFirstCodepoint = { str: ByteArray ->
            val end = str.strlen()
            val (codepoint1, consumed1) = textCharFromUtf8(str, 0, end)
            val (codepoint2, consumed2) = textCharFromUtf8(str, 0, -1)
            consumed1 shouldBe consumed2
//            IM_CHECK_LE_NO_RET(str + consumed1, end)
//            IM_CHECK_LE_NO_RET(str + consumed2, end)
//            IM_CHECK_OP_NO_RET(codepoint1, (unsigned int)expect, op)
            codepoint1
        }

        t.testFunc = {
            fun CHECK_UTF8(text: String) = checkUtf8(text, text)
            // #define IM_CHECK_UTF8_CP32(_TEXT) (CheckUtf8_cp32(u8##_TEXT, U##_TEXT))

            // Test data taken from https://bitbucket.org/knight666/utf8rewind/src/default/testdata/big-list-of-naughty-strings-master/blns.txt

            // Special Characters
            // Strings which contain common special ASCII characters (may need to be escaped)
            assert(CHECK_UTF8(",./;'[]\\-="))
            assert(CHECK_UTF8("<>?:\"{}|_+"))
            assert(CHECK_UTF8("!@#$%^&*()`~"))

            // Unicode Symbols
            // Strings which contain common unicode symbols (e.g. smart quotes)
            assert(CHECK_UTF8("\u03a9\u2248\u00e7\u221a\u222b\u02dc\u00b5\u2264\u2265\u00f7"))
            assert(CHECK_UTF8("\u00e5\u00df\u2202\u0192\u00a9\u02d9\u2206\u02da\u00ac\u2026\u00e6"))
            assert(CHECK_UTF8("\u0153\u2211\u00b4\u00ae\u2020\u00a5\u00a8\u02c6\u00f8\u03c0\u201c\u2018"))
            assert(CHECK_UTF8("\u00a1\u2122\u00a3\u00a2\u221e\u00a7\u00b6\u2022\u00aa\u00ba\u2013\u2260"))
            assert(CHECK_UTF8("\u00b8\u02db\u00c7\u25ca\u0131\u02dc\u00c2\u00af\u02d8\u00bf"))
            assert(CHECK_UTF8("\u00c5\u00cd\u00ce\u00cf\u02dd\u00d3\u00d4\uf8ff\u00d2\u00da\u00c6\u2603"))
            assert(CHECK_UTF8("\u0152\u201e\u00b4\u2030\u02c7\u00c1\u00a8\u02c6\u00d8\u220f\u201d\u2019"))
            assert(CHECK_UTF8("`\u2044\u20ac\u2039\u203a\ufb01\ufb02\u2021\u00b0\u00b7\u201a\u2014\u00b1"))
            assert(CHECK_UTF8("\u215b\u215c\u215d\u215e"))
            assert(CHECK_UTF8("\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669"))

            // Unicode Subscript/Superscript
            // Strings which contain unicode subscripts/superscripts; can cause rendering issues
            assert(CHECK_UTF8("\u2070\u2074\u2075"))
            assert(CHECK_UTF8("\u2080\u2081\u2082"))
            assert(CHECK_UTF8("\u2070\u2074\u2075\u2080\u2081\u2082"))

            // Two-Byte Characters
            // Strings which contain two-byte characters: can cause rendering issues or character-length issues
            assert(CHECK_UTF8("\u7530\u4e2d\u3055\u3093\u306b\u3042\u3052\u3066\u4e0b\u3055\u3044"))
            assert(CHECK_UTF8("\u30d1\u30fc\u30c6\u30a3\u30fc\u3078\u884c\u304b\u306a\u3044\u304b"))
            assert(CHECK_UTF8("\u548c\u88fd\u6f22\u8a9e"))
            assert(CHECK_UTF8("\u90e8\u843d\u683c"))
            assert(CHECK_UTF8("\uc0ac\ud68c\uacfc\ud559\uc6d0 \uc5b4\ud559\uc5f0\uad6c\uc18c"))
            assert(CHECK_UTF8("\ucc26\ucc28\ub97c \ud0c0\uace0 \uc628 \ud3b2\uc2dc\ub9e8\uacfc \uc45b\ub2e4\ub9ac \ub620\ubc29\uac01\ud558"))
            assert(CHECK_UTF8("\u793e\u6703\u79d1\u5b78\u9662\u8a9e\u5b78\u7814\u7a76\u6240"))
            assert(CHECK_UTF8("\uc6b8\ub780\ubc14\ud1a0\ub974"))
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\U0002070e\U00020731\U00020779\U00020c53\U00020c78\U00020c96\U00020ccf"));

            // Emoji
            // Strings which contain Emoji; should be the same behavior as two-byte characters, but not always
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\U0001f60d"));
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\U0001f469\U0001f3fd"));
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\U0001f47e \U0001f647 \U0001f481 \U0001f645 \U0001f646 \U0001f64b \U0001f64e \U0001f64d"));
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\U0001f435 \U0001f648 \U0001f649 \U0001f64a"));
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\u2764\ufe0f \U0001f494 \U0001f48c \U0001f495 \U0001f49e \U0001f493 \U0001f497 \U0001f496 \U0001f498 \U0001f49d \U0001f49f \U0001f49c \U0001f49b \U0001f49a \U0001f499"));
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\u270b\U0001f3ff \U0001f4aa\U0001f3ff \U0001f450\U0001f3ff \U0001f64c\U0001f3ff \U0001f44f\U0001f3ff \U0001f64f\U0001f3ff"));
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\U0001f6be \U0001f192 \U0001f193 \U0001f195 \U0001f196 \U0001f197 \U0001f199 \U0001f3e7"));
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("0\ufe0f\u20e3 1\ufe0f\u20e3 2\ufe0f\u20e3 3\ufe0f\u20e3 4\ufe0f\u20e3 5\ufe0f\u20e3 6\ufe0f\u20e3 7\ufe0f\u20e3 8\ufe0f\u20e3 9\ufe0f\u20e3 \U0001f51f"));

            // Unicode Numbers
            // Strings which contain unicode numbers; if the code is localized, it should see the input as numeric
            assert(CHECK_UTF8("\uff11\uff12\uff13"))
            assert(CHECK_UTF8("\u0661\u0662\u0663"))

            // Unicode Spaces
            // Strings which contain unicode space characters with special properties (c.f. https://www.cs.tut.fi/~jkorpela/chars/spaces.html)
            assert(CHECK_UTF8("\u200b"))
            assert(CHECK_UTF8("\u180e"))
            assert(CHECK_UTF8("\ufeff"))
            assert(CHECK_UTF8("\u2423"))
            assert(CHECK_UTF8("\u2422"))
            assert(CHECK_UTF8("\u2421"))

            // Trick Unicode
            // Strings which contain unicode with unusual properties (e.g. Right-to-left override) (c.f. http://www.unicode.org/charts/PDF/U2000.pdf)
            assert(CHECK_UTF8("\u202a\u202atest\u202a"))
            assert(CHECK_UTF8("\u202btest\u202b"))
            assert(CHECK_UTF8("test"))
            assert(CHECK_UTF8("test\u2060test\u202b"))
            assert(CHECK_UTF8("\u2066test\u2067"))

            // Unicode font
            // Strings which contain bold/italic/etc. versions of normal characters
            assert(CHECK_UTF8("\uff34\uff48\uff45 \uff51\uff55\uff49\uff43\uff4b \uff42\uff52\uff4f\uff57\uff4e \uff46\uff4f\uff58 \uff4a\uff55\uff4d\uff50\uff53 \uff4f\uff56\uff45\uff52 \uff54\uff48\uff45 \uff4c\uff41\uff5a\uff59 \uff44\uff4f\uff47"))
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\U0001d413\U0001d421\U0001d41e \U0001d42a\U0001d42e\U0001d422\U0001d41c\U0001d424 \U0001d41b\U0001d42b\U0001d428\U0001d430\U0001d427 \U0001d41f\U0001d428\U0001d431 \U0001d423\U0001d42e\U0001d426\U0001d429\U0001d42c \U0001d428\U0001d42f\U0001d41e\U0001d42b \U0001d42d\U0001d421\U0001d41e \U0001d425\U0001d41a\U0001d433\U0001d432 \U0001d41d\U0001d428\U0001d420"));
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\U0001d57f\U0001d58d\U0001d58a \U0001d596\U0001d59a\U0001d58e\U0001d588\U0001d590 \U0001d587\U0001d597\U0001d594\U0001d59c\U0001d593 \U0001d58b\U0001d594\U0001d59d \U0001d58f\U0001d59a\U0001d592\U0001d595\U0001d598 \U0001d594\U0001d59b\U0001d58a\U0001d597 \U0001d599\U0001d58d\U0001d58a \U0001d591\U0001d586\U0001d59f\U0001d59e \U0001d589\U0001d594\U0001d58c"));
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\U0001d47b\U0001d489\U0001d486 \U0001d492\U0001d496\U0001d48a\U0001d484\U0001d48c \U0001d483\U0001d493\U0001d490\U0001d498\U0001d48f \U0001d487\U0001d490\U0001d499 \U0001d48b\U0001d496\U0001d48e\U0001d491\U0001d494 \U0001d490\U0001d497\U0001d486\U0001d493 \U0001d495\U0001d489\U0001d486 \U0001d48d\U0001d482\U0001d49b\U0001d49a \U0001d485\U0001d490\U0001d488"));
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\U0001d4e3\U0001d4f1\U0001d4ee \U0001d4fa\U0001d4fe\U0001d4f2\U0001d4ec\U0001d4f4 \U0001d4eb\U0001d4fb\U0001d4f8\U0001d500\U0001d4f7 \U0001d4ef\U0001d4f8\U0001d501 \U0001d4f3\U0001d4fe\U0001d4f6\U0001d4f9\U0001d4fc \U0001d4f8\U0001d4ff\U0001d4ee\U0001d4fb \U0001d4fd\U0001d4f1\U0001d4ee \U0001d4f5\U0001d4ea\U0001d503\U0001d502 \U0001d4ed\U0001d4f8\U0001d4f0"));
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\U0001d54b\U0001d559\U0001d556 \U0001d562\U0001d566\U0001d55a\U0001d554\U0001d55c \U0001d553\U0001d563\U0001d560\U0001d568\U0001d55f \U0001d557\U0001d560\U0001d569 \U0001d55b\U0001d566\U0001d55e\U0001d561\U0001d564 \U0001d560\U0001d567\U0001d556\U0001d563 \U0001d565\U0001d559\U0001d556 \U0001d55d\U0001d552\U0001d56b\U0001d56a \U0001d555\U0001d560\U0001d558"));
            // IM_CHECK_NO_RET(IM_CHECK_UTF8_CP32("\U0001d683\U0001d691\U0001d68e \U0001d69a\U0001d69e\U0001d692\U0001d68c\U0001d694 \U0001d68b\U0001d69b\U0001d698\U0001d6a0\U0001d697 \U0001d68f\U0001d698\U0001d6a1 \U0001d693\U0001d69e\U0001d696\U0001d699\U0001d69c \U0001d698\U0001d69f\U0001d68e\U0001d69b \U0001d69d\U0001d691\U0001d68e \U0001d695\U0001d68a\U0001d6a3\U0001d6a2 \U0001d68d\U0001d698\U0001d690"));
            assert(CHECK_UTF8("\u24af\u24a3\u24a0 \u24ac\u24b0\u24a4\u249e\u24a6 \u249d\u24ad\u24aa\u24b2\u24a9 \u24a1\u24aa\u24b3 \u24a5\u24b0\u24a8\u24ab\u24ae \u24aa\u24b1\u24a0\u24ad \u24af\u24a3\u24a0 \u24a7\u249c\u24b5\u24b4 \u249f\u24aa\u24a2"))

            // Invalid inputs
            // FIXME-MISC: ImTextCharFromUtf8() returns 0 codepoint when first byte is not valid utf-8. If first byte is valid utf-8 but codepoint is still invalid - IM_UNICODE_CODEPOINT_INVALID is returned.
//            getFirstCodepoint("\x80") == 0);         // U+0000 - U+007F   00-7F
//            IM_CHECK_NO_RET(get_first_codepoint("\xFF") == 0);
            val validRanges = arrayOf(
                    byteArrayOf(0xC2.b, 0xDF.b, 0x80.b, 0xBF.b, 0x00.b, 0x00.b, 0x00.b, 0x00.b), // U+0080   - U+07FF   C2-DF  80-BF
                    byteArrayOf(0xE0.b, 0xE0.b, 0xA0.b, 0xBF.b, 0x80.b, 0xBF.b, 0x00.b, 0x00.b), // U+0800   - U+0FFF   E0     A0-BF  80-BF
                    byteArrayOf(0xE1.b, 0xEC.b, 0x80.b, 0xBF.b, 0x80.b, 0xBF.b, 0x00.b, 0x00.b), // U+1000   - U+CFFF   E1-EC  80-BF  80-BF
                    byteArrayOf(0xED.b, 0xED.b, 0x80.b, 0x9F.b, 0x80.b, 0xBF.b, 0x00.b, 0x00.b), // U+D000   - U+D7FF   ED     80-9F  80-BF
                    byteArrayOf(0xEE.b, 0xEF.b, 0x80.b, 0xBF.b, 0x80.b, 0xBF.b, 0x00.b, 0x00.b)) // U+E000   - U+FFFF   EE-EF  80-BF  80-BF
//                #ifdef IMGUI_USE_WCHAR32
//                    { 0xF0, 0xF0,  0x90, 0xBF,  0x80, 0xBF,  0x80, 0xBF }, // U+10000  - U+3FFFF  F0     90-BF  80-BF  80-BF
//                { 0xF1, 0xF3,  0x80, 0xBF,  0x80, 0xBF,  0x80, 0xBF }, // U+40000  - U+FFFFF  F1-F3  80-BF  80-BF  80-BF
//                { 0xF4, 0xF4,  0x80, 0x8F,  0x80, 0xBF,  0x80, 0xBF }, // U+100000 - U+10FFFF F4     80-8F  80-BF  80-BF
//                #endif
            for (rangeN in validRanges.indices) {
                val range = validRanges[rangeN]
                val seq = ByteArray(4)

                // 6 bit mask, 2 bits for each of 1-3 bytes in tested sequence.
                for (mask in 0 until (1 shl (3 * 2))) {
                    // First byte follows a sequence between valid ranges. Use always-valid byte, couple out of range cases are tested manually.
                    seq[0] = range[mask % 2]

                    // 1-3 bytes will be tested as follows: in range, below valid range, above valid range.
                    for (n in 1..3) {
                        // Bit 0 - 0: test out of range, 1: test in range.
                        // Bit 1 - 0: test end of range, 1: test start of range.
                        val shift = (n - 1) * 2
                        val b = (mask and (3 shl shift)) ushr shift
                        val byteN = n * 2
                        if (range[byteN + 0] != 0.b) {
                            seq[n] = range[byteN + if (b has 2) 0 else 1]
                            if (b hasnt 1)
                                seq[n] = (seq[n] + if (b has 2) -1 else +1).b // Move byte out of valid range
                        } else
                            seq[n] = 0.b
                    }

                    //ctx->LogDebug("%02X%02X%02X%02X %d %d", seq[0], seq[1], seq[2], seq[3], range_n, mask);
                    val inRangeMask = seq[1].bool.i or (if (seq[2] != 0.b) 4 else 0) or if (seq[3] != 0.b) 16 else 0
                    if ((mask and inRangeMask) == inRangeMask) // All bytes were in a valid range.
                        getFirstCodepoint(seq) shouldNotBe UNICODE_CODEPOINT_INVALID
                    else
                        getFirstCodepoint(seq) shouldBe UNICODE_CODEPOINT_INVALID
                }
            }
//            IM_CHECK_EQ_NO_RET(get_first_codepoint("\xC1\x80"), (unsigned int)IM_UNICODE_CODEPOINT_INVALID)         // Two byte sequence, first byte before valid range.
//            IM_CHECK_EQ_NO_RET(get_first_codepoint("\xF5\x80\x80\x80"), (unsigned int)IM_UNICODE_CODEPOINT_INVALID) // Four byte sequence, first byte after valid range.

            // Incomplete inputs
//            IM_CHECK_NO_RET(get_first_codepoint("\xE0\xA0") == IM_UNICODE_CODEPOINT_INVALID)
//            #ifdef IMGUI_USE_WCHAR32
//                IM_CHECK_NO_RET(get_first_codepoint("\xF0\x90\x80") == IM_UNICODE_CODEPOINT_INVALID)
//            IM_CHECK_NO_RET(get_first_codepoint("\xED\xA0\x80") == IM_UNICODE_CODEPOINT_INVALID)
//            #endif
        }
    }

    // ## Test ImGuiTextFilter
    e.registerTest("misc", "misc_text_filter").let { t ->
        t.guiFunc = {
            dsl.window("Text filter", null, WindowFlag.NoSavedSettings.i) {
                filter.draw("Filter", ImGui.fontSize * 16)   // Test input filter drawing
            }
        }
        t.testFunc = { ctx: TestContext ->
            // Test ImGuiTextFilter::Draw()
            ctx.setRef("Text filter")
            ctx.itemInput("Filter")
            ctx.keyCharsAppend("Big,Cat,, ,  ,Bird") // Trigger filter rebuild

            // Test functionality
            val filter = TextFilter()
            filter += "-bar"
            assert(!filter.passFilter("bartender"))
            assert(filter.passFilter("cartender"))

            filter.clear()
            filter += "bar "
            assert(filter.passFilter("bartender"))
            assert(!filter.passFilter("cartender"))

            filter.clear()
            filter += "bar"
            assert(filter.passFilter("bartender"))
            assert(!filter.passFilter("cartender"))
        }
    }

    // ## Visual ImBezierClosestPoint test.
    e.registerTest("misc", "misc_bezier_closest_point").let { t ->
        t.guiFunc = { ctx: TestContext ->

            val style = ctx.uiContext!!.style

            ImGui.setNextWindowSize(Vec2(600, 400), Cond.Appearing)
            ImGui.begin("Bezier", null, WindowFlag.NoSavedSettings.i)
            ImGui.dragInt("Segments", ::numSegments, 0.05f, 0, 20)

            val drawList = ImGui.windowDrawList
            val mousePos = ImGui.mousePos
            val wp = ImGui.windowPos

            // Draw modifiable control points
            for (pt in points) {
                val halfCircle = 2f
                val fullCircle = halfCircle * 2f
                val r = Rect(wp + pt - halfCircle, wp + pt + halfCircle)
                val id = ImGui.getID(points.indexOf(pt).L)

                ImGui.itemAdd(r, id)
                val isHovered = ImGui.isItemHovered()
                val isActive = ImGui.isItemActive
                if (isHovered || isActive)
                    drawList.addCircleFilled(r.center, fullCircle, COL32(0, 255, 0, 255))
                else
                    drawList.addCircle(r.center, fullCircle, COL32(0, 255, 0, 255))

                if (isActive)
                    if (ImGui.isMouseDown(MouseButton.Left))
                        pt put (mousePos - wp)
                    else
                        ImGui.clearActiveID()
                else if (ImGui.isMouseDown(MouseButton.Left) && isHovered)
                    ImGui.setActiveID(id, ImGui.currentWindow)
            }
            drawList.addLine(wp + points[0], wp + points[1], COL32(0, 255, 0, 100))
            drawList.addLine(wp + points[2], wp + points[3], COL32(0, 255, 0, 100))

            // Draw curve itself
            drawList.addBezierCurve(wp + points[0], wp + points[1], wp + points[2], wp + points[3], COL32_WHITE, 2f, numSegments)

            // Draw point closest to the mouse cursor
            val point = when (numSegments) {
                0 -> bezierClosestPointCasteljau(wp + points[0], wp + points[1], wp + points[2], wp + points[3], mousePos, style.curveTessellationTol)
                else -> bezierClosestPoint(wp + points[0], wp + points[1], wp + points[2], wp + points[3], mousePos, numSegments)
            }
            drawList.addCircleFilled(point, 4f, COL32(255, 0, 0, 255))

            ImGui.end()
        }
    }

    // FIXME-TESTS
    e.registerTest("demo", "demo_misc_001").let { t ->
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Dear ImGui Demo")
            ctx.itemOpen("Widgets")
            ctx.itemOpen("Basic")
            ctx.itemClick("Basic/Button")
            ctx.itemClick("Basic/radio a")
            ctx.itemClick("Basic/radio b")
            ctx.itemClick("Basic/radio c")
            ctx.itemClick("Basic/combo")
            ctx.itemClick("Basic/combo")
            ctx.itemClick("Basic/color 2/##ColorButton")
            //ctx->ItemClick("##Combo/BBBB");     // id chain
            ctx.sleepShort()
            ctx.popupCloseAll()

            //ctx->ItemClick("Layout & Scrolling");  // FIXME: close popup
            ctx.itemOpen("Layout & Scrolling")
            ctx.itemOpen("Scrolling")
            ctx.itemHold("Scrolling/>>", 1f)
            ctx.sleepShort()
        }
    }

    // ## Coverage: open everything in demo window
    // ## Extra: test for inconsistent ScrollMax.y across whole demo window
    // ## Extra: run Log/Capture api on whole demo window
    e.registerTest("demo", "demo_cov_auto_open").let { t ->
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Dear ImGui Demo")
            ctx.itemOpenAll("")

            // Additional tests we bundled here because we are benefiting from the "opened all" state
            val window = ctx.getWindowByRef("")!!
            ctx.scrollVerifyScrollMax(window)

            // Test the Log/Capture api
            var clipboard = ImGui.clipboardText
            assert(clipboard.isEmpty())
            ctx.itemClick("Capture\\/Logging/LogButtons/Log To Clipboard")
            clipboard = ImGui.clipboardText
            val clipboardLen = clipboard.length
            assert(clipboardLen >= 15000) // This is going to vary (as of 2019-11-18 on Master this 22766)
        }
    }

    // ## Coverage: closes everything in demo window
    e.registerTest("demo", "demo_cov_auto_close").let { t ->
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Dear ImGui Demo")
            ctx.itemCloseAll("")
        }
    }

    e.registerTest("demo", "demo_cov_001").let { t ->
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Dear ImGui Demo")
            ctx.itemOpen("Help")
            ctx.itemOpen("Configuration")
            ctx.itemOpen("Window options")
            ctx.itemOpen("Widgets")
            ctx.itemOpen("Layout & Scrolling")
            ctx.itemOpen("Popups & Modal windows")
//        #if helpers.getIMGUI_HAS_TABLE
//        ctx->ItemOpen("Tables & Columns")
//        #else
            ctx.itemOpen("Columns")
//        #endif
            ctx.itemOpen("Filtering")
            ctx.itemOpen("Inputs, Navigation & Focus")
        }
    }

    // ## Open misc elements which are beneficial to coverage and not covered with ItemOpenAll
    e.registerTest("demo", "demo_cov_002").let { t ->
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Dear ImGui Demo")
            ctx.itemOpen("Layout & Scrolling")
            ctx.itemOpen("Scrolling")
            ctx.itemCheck("Scrolling/Show Horizontal contents size demo window")   // FIXME-TESTS: ItemXXX functions could do the recursion (e.g. Open parent)
            ctx.itemUncheck("Scrolling/Show Horizontal contents size demo window")

            ctx.setRef("Dear ImGui Demo")
            ctx.menuCheck("Tools/About Dear ImGui")
            ctx.setRef("About Dear ImGui")
            ctx.itemCheck("Config\\/Build Information")
            ctx.setRef("Dear ImGui Demo")

            ctx.setRef("Dear ImGui Demo")
            ctx.menuCheck("Tools/Style Editor")
            ctx.setRef("Dear ImGui Style Editor")
            ctx.itemClick("##tabs/Sizes")
            ctx.itemClick("##tabs/Colors")
            ctx.itemClick("##tabs/Fonts")
            ctx.itemClick("##tabs/Rendering")

            ctx.setRef("Dear ImGui Demo")
            ctx.menuCheck("Examples/Custom rendering")
            ctx.setRef("Example: Custom rendering")
            ctx.itemClick("##TabBar/Primitives")
            ctx.itemClick("##TabBar/Canvas")
            ctx.itemClick("##TabBar/BG\\/FG draw lists")

            ctx.setRef("Dear ImGui Demo")
            ctx.menuUncheckAll("Examples")
            ctx.menuUncheckAll("Tools")
        }
    }

    e.registerTest("demo", "demo_cov_apps").let { t ->
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Dear ImGui Demo")
            ctx.menuClick("Menu/Open Recent/More..")
            ctx.menuCheckAll("Examples")
            ctx.menuUncheckAll("Examples")
            ctx.menuCheckAll("Tools")
            ctx.menuUncheckAll("Tools")
        }
    }

    // ## Coverage: select all styles via the Style Editor
    e.registerTest("demo", "demo_cov_styles").let { t ->
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Dear ImGui Demo")
            ctx.menuCheck("Tools/Style Editor")

            val refWindow = TestRef(path = "Dear ImGui Style Editor")
            ctx.setRef(refWindow)
            ctx.itemClick("Colors##Selector")
            ctx.yield()
            val refPopup = ctx.focusWindowRef

            val styleBackup = ImGui.style
            val items = TestItemList()
            ctx.gatherItems(items, refPopup)
            for (item in items) {
                ctx.setRef(refWindow)
                ctx.itemClick("Colors##Selector")
                ctx.setRef(refPopup)
                ctx.itemClick(item.id)
            }
            gImGui!!.style = styleBackup
        }
    }

    // ## Coverage: exercice some actions in ColorEditOptionsPopup() and ColorPickerOptionsPopup(
    e.registerTest("demo", "demo_cov_color_picker").let { t ->
        t.testFunc = { ctx: TestContext ->
            ctx.setRef("Dear ImGui Demo")
            ctx.itemOpen("Widgets")
            ctx.itemOpen("Basic")

            ctx.mouseMove("Basic/color 2/##ColorButton")
            ctx.mouseClick(1) // Open picker settings popup
            ctx.yield()

            ctx.setRef(ctx.focusWindowRef)
            ctx.itemClick("RGB")
            ctx.itemClick("HSV")
            ctx.itemClick("Hex")
            ctx.itemClick("RGB")
            ctx.itemClick("0..255")
            ctx.itemClick("0.00..1.00")
            ctx.itemClick("0..255")

            ctx.itemClick("Copy as..")
            ctx.keyPressMap(Key.Escape) // Close popup

            for (pickerType in 0..1) {
                ctx.setRef("Dear ImGui Demo")
                ctx.mouseMove("Basic/color 2/##ColorButton")
                ctx.mouseClick(0) // Open color picker
                ctx.yield()

                // Open color picker style chooser
                ctx.setRef(ctx.focusWindowRef)
                ctx.mouseMoveToPos(ctx.getWindowByRef("")!!.rect().center)
                ctx.mouseClick(1)
                ctx.yield()

                // Select picker type
                ctx.setRef(ctx.focusWindowRef)
                ctx.mouseMove(ctx.getID("##selectable", ctx.getIDByInt(pickerType)))
                ctx.mouseClick(0)

                // Interact with picker
                ctx.setRef(ctx.focusWindowRef)
                if (pickerType == 0) {
                    ctx.mouseMove("##picker/sv", TestOpFlag.MoveToEdgeU or TestOpFlag.MoveToEdgeL)
                    ctx.mouseDown(0)
                    ctx.mouseMove("##picker/sv", TestOpFlag.MoveToEdgeD or TestOpFlag.MoveToEdgeR)
                    ctx.mouseMove("##picker/sv")
                    ctx.mouseUp(0)

                    ctx.mouseMove("##picker/hue", TestOpFlag.MoveToEdgeU.i)
                    ctx.mouseDown(0)
                    ctx.mouseMove("##picker/hue", TestOpFlag.MoveToEdgeD.i)
                    ctx.mouseMove("##picker/hue", TestOpFlag.MoveToEdgeU.i)
                    ctx.mouseUp(0)
                } else if (pickerType == 1) {
                    ctx.mouseMove("##picker/hsv")
                    ctx.mouseClick(0)
                }

                ctx.popupCloseAll()
            }
        }
    }

    // ## Coverage: open everything in metrics window
    e.registerTest("demo", "demo_cov_metrics").let { t ->
        t.testFunc = { ctx: TestContext ->

            // Ensure Metrics windows is closed when beginning the test
            ctx.setRef("/Dear ImGui Demo")
            ctx.menuCheck("Tools/Metrics")
            ctx.setRef("/Dear ImGui Metrics")
            ctx.itemCloseAll("")

            // FIXME-TESTS: Maybe add status flags filter to GatherItems() ?
            val items = TestItemList()
            ctx.gatherItems(items, "", 1)
            for (item in items) {
                if (item.statusFlags hasnt ItemStatusFlag.Openable)
                    continue
                //if (item.ID != ctx->GetID("Settings")) // [DEBUG]
                //    continue;

                ctx.itemOpen(item.id)

                // FIXME-TESTS: Anything and "DrawLists" DrawCmd sub-items are updated when hovering items,
                // they make the tests fail because some "MouseOver" can't find gathered items and make the whole test stop.
                // Maybe make it easier to perform some filtering, aka OpenAll except "XXX"
                // Maybe could add support for ImGuiTestOpFlags_NoError in the ItemOpenAll() path?
                val maxCountPerDepth = intArrayOf(4, 4, 0)
                run {
                    val filter = TestActionFilter().apply {
                        maxDepth = 2
                        maxItemCountPerDepth = maxCountPerDepth
                        requireAllStatusFlags = ItemStatusFlag.Openable.i
                    }
                    if (item.id == ctx.getID("Windows") || item.id == ctx.getID("Viewport") || item.id == ctx.getID("Viewports"))
                        filter.maxDepth = 1
                    else if (item.id == ctx.getID("DrawLists"))
                        filter.maxDepth = 1
                    ctx.itemActionAll(TestAction.Open, item.id, filter)
                }

                // Toggle all tools (to enable/disable them, then restore their initial state)
                if (item.id == ctx.getID("Tools")) {
                    val filter = TestActionFilter().apply {
                        requireAllStatusFlags = ItemStatusFlag.Checkable.i
                        maxDepth = 1
                        maxPasses = 1
                    }
                    ctx.itemActionAll(TestAction.Click, "Tools", filter)
                    ctx.itemActionAll(TestAction.Click, "Tools", filter)
                }

                // FIXME-TESTS: in docking branch this is under Viewports
                if (item.id == ctx.getID("DrawLists")) {
                    val filter = TestActionFilter().apply {
                        maxDepth = 2;
                        maxItemCountPerDepth = maxCountPerDepth
                    }
                    ctx.itemActionAll(TestAction.Hover, "DrawLists", filter)
                }

                // Close
                ctx.itemCloseAll(item.id)
                ctx.itemClose(item.id)
            }
            ctx.windowClose("")
        }
    }
}

val defaultRanges = arrayOf(
        IntRange(0x0020, 0x00FF), // Basic Latin + Latin Supplement
        IntRange(0x0080, 0x00FF)) // Latin_Supplement

val filter = TextFilter()

// FIXME-TESTS: Store normalized?
val points = arrayOf(Vec2(30, 75), Vec2(185, 355), Vec2(400, 60), Vec2(590, 370))
var numSegments = 0