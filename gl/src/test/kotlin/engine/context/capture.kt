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

//bool ImGuiTestContext::BeginCaptureGif(ImGuiCaptureArgs* args)
//{
//    IMGUI_TEST_CONTEXT_REGISTER_DEPTH(this);
//    LogInfo("CaptureScreenshot()");
//    return ImGuiTestEngine_BeginCaptureAnimation(Engine, args);
//}
//
//bool ImGuiTestContext::EndCaptureGif(ImGuiCaptureArgs* args)
//{
//    bool ret = ImGuiTestEngine_EndCaptureAnimation(Engine, args);
//    LogDebug("Saved '%s' (%d*%d pixels)", args->OutSavedFileName, (int)args->OutImageSize.x, (int)args->OutImageSize.y);
//    return ret;
//}