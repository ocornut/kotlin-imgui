package engine.context

import engine.CaptureArgs
import engine.CaptureFlag
import engine.CaptureFlags
import engine.engine.CHECK_RETV
import engine.engine.TestRef
import engine.engine.captureScreenshot

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

fun TestContext.captureScreenshot(args: CaptureArgs): Boolean {
    if (isError)
        return false

    return REGISTER_DEPTH {
        logInfo("CaptureScreenshot()")
        engine!! captureScreenshot args.also {
            logDebug("Saved '${args.outSavedFileName}' (${args.outImageSize.x}*${args.outImageSize.y} pixels)")
        }
    }
}

// FIXME-TESTS: Figure how to get capturing to have zero delay (only 1 yield)
// FIXME-TESTS: Add ImGuiCaptureFlags_NoHideOtherWindows
//void ImGuiTestContext::CaptureScreenshotSimple(ImGuiTestRef ref, int capture_flags)
//{
//    //int frame_count = this->FrameCount;
//    ImGuiCaptureArgs args;
//    CaptureInitArgs(&args, capture_flags);
//    CaptureAddWindow(&args, ref);
//    CaptureScreenshot(&args);
//    //IM_ASSERT(frame_count == FrameCount);
//}

//bool ImGuiTestContext::BeginCaptureGif(ImGuiCaptureArgs* args)
//{
//    if (IsError())
//    return false;
//    IMGUI_TEST_CONTEXT_REGISTER_DEPTH(this);
//    LogInfo("BeginCaptureGif()");
//    return ImGuiTestEngine_BeginCaptureAnimation(Engine, args);
//}
//
//bool ImGuiTestContext::EndCaptureGif(ImGuiCaptureArgs* args)
//{
//    bool ret = Engine->CaptureContext.IsCapturingGif() && ImGuiTestEngine_EndCaptureAnimation(Engine, args);
//    if (ret)
//    {
//        // In-progress capture was canceled by user. Delete incomplete file.
//        if (IsError())
//        {
//            //ImFileDelete(args->OutSavedFileName);
//            return false;
//        }
//
//        LogDebug("Saved '%s' (%d*%d pixels)", args->OutSavedFileName, (int)args->OutImageSize.x, (int)args->OutImageSize.y);
//    }
//    return ret;
//}