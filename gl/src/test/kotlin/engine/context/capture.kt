package engine.context

import engine.CaptureArgs
import engine.CaptureFlag
import engine.CaptureFlags
import engine.core.CHECK_RETV
import engine.core.TestRef
import engine.core.captureScreenshot

// Capture

fun TestContext.captureInitArgs(args: CaptureArgs, flags: CaptureFlags = CaptureFlag.None.i) {
    args.inFlags = flags
    args.inPadding = 13f
    args.inOutputFileTemplate = "captures/${test!!.name}_%04d.png".format(captureCounter)
    captureCounter++
}

// [JVM]
fun TestContext.captureAddWindow(args: CaptureArgs, ref: String): Boolean =
        captureAddWindow(args, TestRef(path = ref))

fun TestContext.captureAddWindow(args: CaptureArgs, ref: TestRef): Boolean {
    val window = getWindowByRef(ref)
    if (window == null)
        CHECK_RETV(window != null, false)
    args.inCaptureWindows += window!!
    return window != null
}

fun TestContext.captureScreenshot(args: CaptureArgs): Boolean = REGISTER_DEPTH {
    logInfo("CaptureScreenshot()")
    engine!! captureScreenshot args.also {
        logDebug("Saved '${args.outSavedFileName}' (${args.outImageSize.x}*${args.outImageSize.y} pixels)")
    }
}