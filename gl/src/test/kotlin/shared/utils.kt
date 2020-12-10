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


// Maths/Geometry helpers

// Generate a random convex shape with 'points_count' points, writing them into 'points'.
// 'poly_seed' specifies the random seed value.
// 'shape_center' and 'shape_size' define where the shape will be located and the size (of the largest axis).
// (based on algorithm from http://cglab.ca/~sander/misc/ConvexGeneration/convex.html)
fun geomGenerateRandomConvexShape(points: List<Vec2>, shapeCenter: Vec2, shapeSize: Float, polySeed: Int) {

    val pointsCount = points.size
    assert(pointsCount >= 3)

    val random = Random(polySeed)

    // Generate two lists of numbers
    val xPoints = FloatArray(pointsCount) { random.nextFloat() + Float.MIN_VALUE }
    val yPoints = FloatArray(pointsCount){ random.nextFloat() + Float.MIN_VALUE }

    // Sort
    xPoints.sort()
    yPoints.sort()

    // Get the extremities
    val minX = xPoints[0]
    val maxX = xPoints[pointsCount - 1]
    val minY = yPoints[0]
    val maxY = yPoints[pointsCount - 1]

    // Split into pairs of chains, one for each "side" of the shape

    val xChain = FloatArray(pointsCount)
    val yChain = FloatArray(pointsCount)

    var xChainCurrentA = minX
    var xChainCurrentB = minX
    var yChainCurrentA = minY
    var yChainCurrentB = minY

    for (i in 1 until (pointsCount - 1)) {
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

    xChain[pointsCount - 2] = maxX - xChainCurrentA
    xChain[pointsCount - 1] = xChainCurrentB - maxX
    yChain[pointsCount - 2] = maxY - yChainCurrentA
    yChain[pointsCount - 1] = yChainCurrentB - maxY

    // Build shuffle list
    val shuffleList = IntArray(pointsCount) { it }

    for (i in 0 until pointsCount * 2) {
        val indexA = random.nextInt(pointsCount)
        val indexB = random.nextInt(pointsCount)
        val temp = shuffleList[indexA]
        shuffleList[indexA] = shuffleList[indexB]
        shuffleList[indexB] = temp
    }

    // Generate random vectors from the X/Y chains

    for (i in 0 until pointsCount)
        points[i].put(xChain[i], yChain[shuffleList[i]])

    // Sort by angle of vector
    points.sortedBy { atan2(it.y, it.x) }

    // Convert into absolute co-ordinates
    val currentPos = Vec2()
    val centerPos = Vec2()
    val minPos = Vec2(Float.MAX_VALUE)
    val maxPos = Vec2(Float.MIN_VALUE)
    for (i in 0 until pointsCount) {
        val newPos = Vec2(currentPos.x + points[i].x, currentPos.y + points[i].y)
        points[i] put currentPos
        centerPos.put(centerPos.x + currentPos.x, centerPos.y + currentPos.y)
        minPos.x = minPos.x min currentPos.x
        minPos.y = minPos.y min currentPos.y
        maxPos.x = maxPos.x max currentPos.x
        maxPos.y = maxPos.y max currentPos.y
        currentPos put newPos
    }

    // Re-scale and center
    centerPos.put(centerPos / pointsCount)

    val size = maxPos - minPos

    val scale = shapeSize / (size.x max size.y)

    for (i in 0 until pointsCount) {
        points[i].x = shapeCenter.x + (points[i].x - centerPos.x) * scale
        points[i].y = shapeCenter.y + (points[i].y - centerPos.y) * scale
    }
}

