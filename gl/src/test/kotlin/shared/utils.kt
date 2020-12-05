package shared

import com.github.ajalt.mordant.AnsiColorCode
import com.github.ajalt.mordant.TermColors
import glm_.L
import glm_.max
import glm_.min
import glm_.vec2.Vec2
import java.io.File
import java.io.PrintStream
import kotlin.math.atan2
import kotlin.random.Random

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
// - ImParseFindIniSection()
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

//-----------------------------------------------------------------------------

// Generate a random convex shape with num_points points, writing them into poly_points. poly_seed specifies the random seed value. shape_center and shape_size define where the shape will be located and the size (of the largest axis).
// (based on algorithm from http://cglab.ca/~sander/misc/ConvexGeneration/convex.html)
fun generateRandomConvexShape(polyPoints: List<Vec2>, shapeCenter: Vec2, shapeSize: Float, polySeed: Int) {

    val numPoints = polyPoints.size
    assert(numPoints >= 3)

    val random = Random(polySeed)

    // Generate two lists of numbers
    val xPoints = FloatArray(numPoints) { random.nextFloat() + Float.MIN_VALUE }
    val yPoints = FloatArray(numPoints){ random.nextFloat() + Float.MIN_VALUE }

    // Sort
    xPoints.sort()
    yPoints.sort()

    // Get the extremities
    val minX = xPoints[0]
    val maxX = xPoints[numPoints - 1]
    val minY = yPoints[0]
    val maxY = yPoints[numPoints - 1]

    // Split into pairs of chains, one for each "side" of the shape

    val xChain = FloatArray(numPoints)
    val yChain = FloatArray(numPoints)

    var xChainCurrentA = minX
    var xChainCurrentB = minX
    var yChainCurrentA = minY
    var yChainCurrentB = minY

    for (i in 1 until (numPoints - 1)) {
        if (random.nextBoolean()) {
            xChain[i - 1] = xPoints[i] - xChainCurrentA
            xChainCurrentA = xPoints[i]
            yChain[i - 1] = yPoints[i] - yChainCurrentA
            yChainCurrentA = yPoints[i]
        }
        else
        {
            xChain[i - 1] = xChainCurrentB - xPoints[i]
            xChainCurrentB = xPoints[i]
            yChain[i - 1] = yChainCurrentB - yPoints[i]
            yChainCurrentB = yPoints[i]
        }
    }

    xChain[numPoints - 2] = maxX - xChainCurrentA
    xChain[numPoints - 1] = xChainCurrentB - maxX
    yChain[numPoints - 2] = maxY - yChainCurrentA
    yChain[numPoints - 1] = yChainCurrentB - maxY

    // Build shuffle list
    val shuffleList = IntArray(numPoints) { it }

    for (i in 0 until numPoints * 2) {
        val indexA = random.nextInt(numPoints)
        val indexB = random.nextInt(numPoints)
        val temp = shuffleList[indexA]
        shuffleList[indexA] = shuffleList[indexB]
        shuffleList[indexB] = temp
    }

    // Generate random vectors from the X/Y chains

    for (i in 0 until numPoints)
        polyPoints[i].put(xChain[i], yChain[shuffleList[i]])

    // Sort by angle of vector
    polyPoints.sortedBy { atan2(it.y, it.x) }

    // Convert into absolute co-ordinates
    val currentPos = Vec2()
    val centerPos = Vec2()
    val minPos = Vec2(Float.MAX_VALUE)
    val maxPos = Vec2(Float.MIN_VALUE)
    for (i in 0 until numPoints) {
        val newPos = Vec2(currentPos.x + polyPoints[i].x, currentPos.y + polyPoints[i].y)
        polyPoints[i] put currentPos
        centerPos.put(centerPos.x + currentPos.x, centerPos.y + currentPos.y)
        minPos.x = minPos.x min currentPos.x
        minPos.y = minPos.y min currentPos.y
        maxPos.x = maxPos.x max currentPos.x
        maxPos.y = maxPos.y max currentPos.y
        currentPos put newPos
    }

    // Re-scale and center
    centerPos.put(centerPos / numPoints)

    val size = maxPos - minPos

    val scale = shapeSize / (size.x max size.y)

    for (i in 0 until numPoints) {
        polyPoints[i].x = shapeCenter.x + (polyPoints[i].x - centerPos.x) * scale
        polyPoints[i].y = shapeCenter.y + (polyPoints[i].y - centerPos.y) * scale
    }
}

