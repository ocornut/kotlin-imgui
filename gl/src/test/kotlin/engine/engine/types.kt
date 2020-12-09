package engine.engine

import engine.KeyState
import glm_.has
import imgui.*
import uno.kotlin.NUL

//-------------------------------------------------------------------------
// Types
//-------------------------------------------------------------------------

inline class TestVerboseLevel(val i: Int) {
    operator fun compareTo(b: TestVerboseLevel): Int = i.compareTo(b.i)
    val name
        get() = when (this) {
            Silent -> "Silent"
            Error -> "Error"
            Warning -> "Warning"
            Info -> "Info"
            Debug -> "Debug"
            Trace -> "Trace"
            else -> error("")
        }

    companion object {
        val Silent = TestVerboseLevel(0)  // -v0
        val Error = TestVerboseLevel(1)   // -v1
        val Warning = TestVerboseLevel(2) // -v2
        val Info = TestVerboseLevel(3)    // -v3
        val Debug = TestVerboseLevel(4)   // -v4
        val Trace = TestVerboseLevel(5)
        val COUNT = TestVerboseLevel(6)
    }
}

inline class TestStatus(val i: Int) {
    companion object {
        val Unknown = TestStatus(-1)
        val Success = TestStatus(0)
        val Queued = TestStatus(1)
        val Running = TestStatus(2)
        val Error = TestStatus(3)
    }
}

inline class TestGroup(val i: Int) {
    companion object {
        val Unknown = TestGroup(-1)
        val Tests = TestGroup(0)
        val Perfs = TestGroup(1)
        val COUNT = TestGroup(2)
    }
}

inline class TestFlag(val i: TestFlags) {
    companion object {
        val None = TestFlag(TestFlags(0))
        val NoWarmUp = TestFlag(TestFlags(1 shl 0))    // By default, we run the GUI func twice before starting the test code
        val NoAutoFinish = TestFlag(TestFlags(1 shl 1))// By default, tests with no test func end on Frame 0 (after the warm up). Setting this require test to call ctx->Finish().
    }
}

// Flags for IM_CHECK* macros.
inline class TestCheckFlag(val i: TestCheckFlags) {
    companion object {
        val None = TestCheckFlag(TestCheckFlags(0))
        val SilentSuccess = TestCheckFlag(TestCheckFlags(1 shl 0))
    }
}

// Flags for ImGuiTestContext::Log* functions.
inline class TestLogFlag(val i: TestLogFlags) {
    companion object {
        val None = TestLogFlag(TestLogFlags(0))
        val NoHeader = TestLogFlag(TestLogFlags(1 shl 0))  // Do not display frame count and depth padding
    }
}

// Generic flags for various ImGuiTestContext functions
inline class TestOpFlag(val i: TestOpFlags) {
    infix fun or(f: TestOpFlag) = TestOpFlags(i.i or f.i.i)

    companion object {
        val None = TestOpFlag(TestOpFlags(0))
        val Verbose = TestOpFlag(TestOpFlags(1 shl 0))
        val NoCheckHoveredId = TestOpFlag(TestOpFlags(1 shl 1))
        val NoError = TestOpFlag(TestOpFlags(1 shl 2))   // Don't abort/error e.g. if the item cannot be found
        val NoFocusWindow = TestOpFlag(TestOpFlags(1 shl 3))
        val NoAutoUncollapse = TestOpFlag(TestOpFlags(1 shl 4))   // Disable automatically uncollapsing windows (useful when specifically testing Collapsing behaviors)
        val IsSecondAttempt = TestOpFlag(TestOpFlags(1 shl 5))
        val MoveToEdgeL = TestOpFlag(TestOpFlags(1 shl 6))   // Dumb aiming helpers to test widget that care about clicking position. May need to replace will better functionalities.
        val MoveToEdgeR = TestOpFlag(TestOpFlags(1 shl 7))
        val MoveToEdgeU = TestOpFlag(TestOpFlags(1 shl 8))
        val MoveToEdgeD = TestOpFlag(TestOpFlags(1 shl 9))
    }
}

infix fun Int.has(f: TestOpFlag) = has(f.i.i)

inline class TestRunFlag(val i: TestRunFlags) {
    infix fun or(f: TestRunFlag) = i or f.i

    companion object {
        val None = TestRunFlag(TestRunFlags(0))
        val GuiFuncDisable = TestRunFlag(TestRunFlags(1 shl 0)) // Used internally to temporarily disable the GUI func (at the end of a test, etc)
        val GuiFuncOnly = TestRunFlag(TestRunFlags(1 shl 1))    // Set when user selects "Run GUI func"
        val NoSuccessMsg = TestRunFlag(TestRunFlags(1 shl 2))
        val NoStopOnError = TestRunFlag(TestRunFlags(1 shl 3))
        val NoBreakOnError = TestRunFlag(TestRunFlags(1 shl 4))
        val ManualRun = TestRunFlag(TestRunFlags(1 shl 5))
        val CommandLine = TestRunFlag(TestRunFlags(1 shl 6))
    }
}

enum class TestInputType { None, Key, Nav, Char }

// Weak reference to an Item/Window given an ID or ID path.
class TestRef(var id: ID = 0,
              var path: String? = null) {

    val isEmpty: Boolean
        get() = id == 0 && (path == null || path!!.isEmpty())
}

class TestInput(
        val type: TestInputType,
        val key: Key = Key.Count,
        val keyMods: KeyModFlags = KeyMod.None.i,
        val navInput: NavInput = NavInput.Count,
        val char: Char = NUL,
        val state: KeyState = KeyState.Unknown) {
    companion object {
        fun fromKey(v: Key, state: KeyState, mods: KeyModFlags = KeyMod.None.i) = TestInput(TestInputType.Key, v, mods, state = state)
        fun fromNav(v: NavInput, state: KeyState) = TestInput(TestInputType.Nav, navInput = v, state = state)
        infix fun fromChar(v: Char) = TestInput(TestInputType.Char, char = v)
    }
}

