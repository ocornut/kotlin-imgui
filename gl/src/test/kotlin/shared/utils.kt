package shared

import com.github.ajalt.mordant.AnsiColorCode
import com.github.ajalt.mordant.TermColors
import glm_.L
import java.io.File
import java.io.PrintStream

//-----------------------------------------------------------------------------
// OS helpers
//-----------------------------------------------------------------------------
// - ImOsCreateProcess()
// - ImOsOpenInShell()
// - ImOsConsoleSetTextColor()
// - ImOsIsDebuggerPresent()
// - ImOsOutputDebugString()
//-----------------------------------------------------------------------------

//enum ImOsConsoleStream
//{
//    ImOsConsoleStream_StandardOutput,
//    ImOsConsoleStream_StandardError
//};

val termColor = TermColors()
typealias OsConsoleTextColor = AnsiColorCode
//enum class OsConsoleTextColor { Black, White, BrightWhite, BrightRed, BrightGreen, BrightBlue, BrightYellow }

//bool        ImOsCreateProcess(const char* cmd_line);
fun osOpenInShell(path: String) = Unit // TODD
fun osConsoleSetTextColor(stream: PrintStream, color: OsConsoleTextColor) = Unit
fun osIsDebuggerPresent() = true

//-----------------------------------------------------------------------------
// File/Directory handling helpers
//-----------------------------------------------------------------------------
// - ImFileExist()
// - ImFileCreateDirectoryChain()
// - ImFileFindInParents()
// - ImFileLoadSourceBlurb()
//-----------------------------------------------------------------------------


//-----------------------------------------------------------------------------
// Time helpers
//-----------------------------------------------------------------------------
// - ImTimeGetInMicroseconds()
//-----------------------------------------------------------------------------


//-----------------------------------------------------------------------------
// Threading helpers
//-----------------------------------------------------------------------------
// - ImThreadSleepInMilliseconds()
// - ImThreadSetCurrentThreadDescription()
//-----------------------------------------------------------------------------

fun sleepInMilliseconds(ms: Int) = Thread.sleep(ms.L)


//-----------------------------------------------------------------------------
// Parsing helpers
//-----------------------------------------------------------------------------
// - ImParseSplitCommandLine()
// - ImParseDateFromCompilerIntoYMD()
//-----------------------------------------------------------------------------



//-----------------------------------------------------------------------------
// Build info helpers
//-----------------------------------------------------------------------------
// - ImBuildGetCompilationInfo()
// - ImBuildGetGitBranchName()
//-----------------------------------------------------------------------------


//const ImBuildInfo&  ImGetBuildInfo(); [JVM] -> simply instantiate BuildInfo
val gitBranchName: String
    get() = File(".git${File.separatorChar}HEAD").readText().substringAfterLast('/')