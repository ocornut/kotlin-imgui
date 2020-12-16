package engine

import app.gApp
import gli_.has
import gli_.hasnt
import glm_.f
import glm_.i
import glm_.max
import glm_.min
import glm_.vec2.Vec2
import glm_.vec2.operators.minus
import glm_.vec4.Vec4i
import imgui.*
import imgui.api.g
import imgui.api.gImGui
import imgui.internal.classes.Rect
import imgui.internal.classes.Window
import kool.free
import kool.lim
import kool.set
import sliceAt
import java.awt.Transparency
import java.awt.image.*
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.reflect.KMutableProperty0
import imgui.WindowFlag as Wf

//-----------------------------------------------------------------------------
// [SECTION] ImGuiCaptureImageBuf
// Helper class for simple bitmap manipulation (not particularly efficient!)
//-----------------------------------------------------------------------------
class CaptureImageBuf {

    var width = 0
    var height = 0
    var data: ByteBuffer? = null

    //    ~ImageBuf() TODO
//    { Clear(); }
//
//    void Clear()                                           // Free allocated memory buffer if such exists.
    fun createEmpty(w: Int, h: Int) {                         // Reallocate buffer for pixel data, and zero it.
        width = w
        height = h
        data = ByteBuffer.allocate(width * height * 4)
    }

    // Save pixel data to specified image file.
    fun saveFile(filename: String) {
        assert(data != null)
        val bytes = ByteArray(data!!.lim) { data!![it] }
        val buffer = DataBufferByte(bytes, bytes.size)
        val raster = Raster.createInterleavedRaster(buffer, width, height, 4 * width, 4, IntArray(4) { it }, null)
        val cm = ComponentColorModel(ColorModel.getRGBdefault().colorSpace, true, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE)
        val image = BufferedImage(cm, raster, false, null)
        ImageIO.write(image, filename, File(filename))
    }

    fun removeAlpha() {                                     // Clear alpha channel from all pixels.
        val data = data!!.asIntBuffer()
        var p = 0
        var n = width * height
        while (n-- > 0) {
            data[p] = data[p] or 0xFF000000.i
            p++
        }
    }

    fun blitSubImage(dstX: Int, dstY: Int, srcX: Int, srcY: Int, w: Int, h: Int, source: CaptureImageBuf) {
//        assert(source && "Source image is null.");
        assert(dstX >= 0 && dstY >= 0) { "Destination coordinates can not be negative." }
        assert(srcX >= 0 && srcY >= 0) { "Source coordinates can not be negative." }
        assert(dstX + w <= width && dstY + h <= height) { "Destination image is too small." }
        assert(srcX + w <= source.width && srcY + h <= source.height) { "Source image is too small." }

        for (y in 0 until h)
            for (i in 0 until source.width * 4)
                data!![(dstY + y) * width + dstX + i] = source.data!![(srcY + y) * source.width + srcX]
    }

    fun clear() {
        if (gApp.optGui)
            data?.free()
        data = null
    }
}

typealias ScreenCaptureFunc = (extend: Vec4i, pixels: ByteBuffer, userData: Any?) -> Boolean

enum class CaptureFlag(val i: CaptureFlags) {
    None(0),      //
    StitchFullContents(1 shl 0), // Expand window to it's content size and capture its full height.
    HideCaptureToolWindow(1 shl 1), // Current window will not appear in screenshots or helper UI.
    ExpandToIncludePopups(1 shl 2), // Expand capture area to automatically include visible popups and tooltips.
    HideMouseCursor(1 shl 3),   // Do not render software mouse cursor during capture.
    Instant(1 shl 4),   // Perform capture on very same frame. Only works when capturing a rectangular region. Unsupported features: content stitching, window hiding, window relocation.
    Default_(StitchFullContents.i or HideCaptureToolWindow.i)
}

infix fun Int.wo(f: CaptureFlag) = and(f.i.inv())
infix fun Int.has(f: CaptureFlag) = has(f.i)
infix fun Int.hasnt(f: CaptureFlag) = hasnt(f.i)

typealias CaptureFlags = Int

enum class CaptureToolState {
    None,                             // No capture in progress.
    PickingSingleWindow,              // CaptureWindowPicker() is selecting a window under mouse cursor.
    SelectRectStart,                  // Next mouse click will create selection rectangle.
    SelectRectUpdate,                 // Update selection rectangle until mouse is released.
    Capturing                         // Capture is in progress.
}

// Defines input and output arguments for capture process.
class CaptureArgs {
    // [Input]
    var inFlags: CaptureFlags = 0                    // Flags for customizing behavior of screenshot tool.
    val inCaptureWindows = ArrayList<Window>()               // Windows to capture. All other windows will be hidden. May be used with InCaptureRect to capture only some windows in specified rect.
    var inCaptureRect = Rect()                  // Screen rect to capture. Does not include padding.
    var inPadding = 16f              // Extra padding at the edges of the screenshot. Ensure that there is available space around capture rect horizontally, also vertically if ImGuiCaptureFlags_StitchFullContents is not used.
    var inFileCounter = 0              // Counter which may be appended to file name when saving. By default counting starts from 1. When done this field holds number of saved files.
    var inOutputImageBuf: CaptureImageBuf? = null        // Output will be saved to image buffer if specified.
    var inOutputFileTemplate = "" // Output will be saved to a file if InOutputImageBuf is NULL.
    var inRecordFPSTarget = 25        // FPS target for recording gifs.

    // [Output]
    val outImageSize = Vec2() // Produced image size.
    var outSavedFileName = ""     // Saved file name, if any.

    // [Internal]
    internal var capturing = false             // FIXME: Why is this here??!
}

enum class CaptureStatus { InProgress, Done, Error }

// Implements functionality for capturing images
class CaptureContext(
        var screenCaptureFunc: ScreenCaptureFunc? = null) {              // Graphics backend specific function that captures specified portion of framebuffer and writes RGBA data to `pixels` buffer.

    var screenCaptureUserData: Any? = null                // Custom user pointer which is passed to ScreenCaptureFunc. (Optional)

    // [Internal]
    internal var captureRect = Rect()                   // Viewport rect that is being captured.
    internal var combinedWindowRectPos = Vec2()         // Top-left corner of region that covers all windows included in capture. This is not same as _CaptureRect.Min when capturing explicitly specified rect.
    internal var output = CaptureImageBuf()                        // Output image buffer.
    internal var chunkNo = 0                   // Number of chunk that is being captured when capture spans multiple frames.
    internal var _frameNo = 0                   // Frame number during capture process that spans multiple frames.
    internal val windowBackupRects = ArrayList<Rect>()             // Backup window state that will be restored when screen capturing is done. Size and order matches windows of ImGuiCaptureArgs::InCaptureWindows.
    internal val windowBackupRectsWindows = ArrayList<Window>()      // Backup windows that will have their state restored. args->InCaptureWindows can not be used because popups may get closed during capture and no longer appear in that list.
    internal var displayWindowPaddingBackup = Vec2()   // Backup padding. We set it to {0, 0} during capture.
    internal var displaySafeAreaPaddingBackup = Vec2()  // Backup padding. We set it to {0, 0} during capture.
    var _recording = false             // Flag indicating that GIF recording is in progress.
    var _lastRecordedFrameTimeSec = 0L     // Time when last GIF frame was recorded.
    var _gifWriter: GifWriter? = null              // GIF image writer state.
    val _mouseRelativeToWindowPos = Vec2(-Float.MAX_VALUE)      // Mouse cursor position relative to captured window (when _StitchFullContents is in use).
    var _hoveredWindow: Window? = null          // Window which was hovered at capture start.
    var _captureArgs: CaptureArgs? = null            // Current capture args. Set only if capture is in progress.
    var _mouseDrawCursorBackup = false // Initial value of g.IO.MouseDrawCursor.

    // Should be called after ImGui::NewFrame() and before submitting any UI.
    // (ImGuiTestEngine automatically calls that for you, so this only apply to independently created instance)
    fun postNewFrame() {

        val args = _captureArgs
        if (args == null)
            return

        val g = gImGui!!

        if (args.inFlags has CaptureFlag.HideMouseCursor) {
            if (_frameNo == 0)
                _mouseDrawCursorBackup = g.io.mouseDrawCursor

            g.io.mouseDrawCursor = false
        }

        if (_frameNo > 2 && args.inFlags has CaptureFlag.StitchFullContents) {
            // Force mouse position. Hovered window is reset in ImGui::NewFrame() based on mouse real mouse position.
            assert(args.inCaptureWindows.size == 1)
            g.io.mousePos put (args.inCaptureWindows.first().pos + _mouseRelativeToWindowPos)
            g.hoveredWindow = _hoveredWindow
            g.hoveredRootWindow = _hoveredWindow?.rootWindow
        }
    }

    // Capture a screenshot. If this function returns true then it should be called again with same arguments on the next frame.
    // Returns true when capture is in progress.
    fun captureUpdate(args: CaptureArgs): CaptureStatus {

        val g = gImGui!!
        val io = g.io
//        IM_ASSERT(args != NULL);
        assert(screenCaptureFunc != null)
        assert(args.inOutputImageBuf != null || args.inOutputFileTemplate.isNotEmpty())
        assert(args.inRecordFPSTarget != 0)
        assert(!_recording || args.inOutputFileTemplate.isNotEmpty()) { "Output file must be specified when recording gif." }
        assert(!_recording || args.inFlags hasnt CaptureFlag.StitchFullContents) { "Image stitching is not supported when recording gifs." }

        val output = args.inOutputImageBuf ?: output

        // Hide other windows so they can't be seen visible behind captured window
        if (args.inCaptureWindows.isNotEmpty())
            hideOtherWindows(args)

        // Recording will be set to false when we are stopping GIF capture.
        val isRecordingGif = isCapturingGif
        val currentTimeSec = ImGui.time

        if (isRecordingGif || _lastRecordedFrameTimeSec == 0L) {
            val deltaSec = currentTimeSec - _lastRecordedFrameTimeSec
            if (deltaSec < 1.0 / args.inRecordFPSTarget)
                return CaptureStatus.InProgress
        }

        // Capture can be performed in single frame if we are capturing a rect.
        val singleFrameCapture = args.inFlags has CaptureFlag.Instant
        val isCapturingRect = args.inCaptureRect.width > 0 && args.inCaptureRect.height > 0
        if (singleFrameCapture) {
            assert(args.inCaptureWindows.isEmpty())
            assert(isCapturingRect)
            assert(!isRecordingGif)
            assert(args.inFlags hasnt CaptureFlag.StitchFullContents)
        }

        //-----------------------------------------------------------------
        // Frame 0: Initialize capture state
        //-----------------------------------------------------------------
        if (_frameNo == 0) {
            // Create output folder and decide of output filename
//            if (args->InOutputFileTemplate[0])
//            {
//                int file_name_size = IM_ARRAYSIZE(args->OutSavedFileName);
//                ImFormatString(args->OutSavedFileName, file_name_size, args->InOutputFileTemplate, args->InFileCounter + 1);
//                ImPathFixSeparatorsForCurrentOS(args->OutSavedFileName);
//                if (!ImFileCreateDirectoryChain(args->OutSavedFileName, ImPathFindFilename(args->OutSavedFileName)))
//                {
//                    printf("Capture Tool: unable to create directory for file '%s'.\n", args->OutSavedFileName);
//                    return ImGuiCaptureToolStatus_Error;
//                }
//
//                // File template will most likely end with .png, but we need .gif for animated images.
//                if (is_recording_gif)
//                  if (char* ext = (char*)ImPathFindExtension(args->OutSavedFileName))
//                    ImStrncpy(ext, ".gif", ext - args->OutSavedFileName);
//            }

            // When recording, same args should have been passed to BeginGifCapture().
            assert(!_recording || _captureArgs === args)

            _captureArgs = args
            chunkNo = 0
            captureRect.put(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            windowBackupRects.clear()
            combinedWindowRectPos put Float.MAX_VALUE
            displayWindowPaddingBackup = g.style.displayWindowPadding
            displaySafeAreaPaddingBackup = g.style.displaySafeAreaPadding
            g.style.displayWindowPadding put 0                    // Allow window to be positioned fully outside
            g.style.displaySafeAreaPadding put 0                  // of visible viewport.
            args.capturing = true

            if (isCapturingRect) {
                // Capture arbitrary rectangle. If any windows are specified in this mode only they will appear in captured region.
                captureRect put args.inCaptureRect
                if (args.inCaptureWindows.isEmpty() && !singleFrameCapture) {
                    // Gather all top level windows. We will need to move them in order to capture regions larger than viewport.
                    for (window in g.windows) {
                        // Child windows will be included by their parents.
                        if (window.parentWindow != null)
                            continue
                        if ((window.flags has Wf._Popup || window.flags has Wf._Tooltip) && args.inFlags hasnt CaptureFlag.ExpandToIncludePopups.i)
                            continue
                        args.inCaptureWindows += window
                    }
                }
            }

            // Save rectangle covering all windows and find top-left corner of combined rect which will be used to
            // translate this group of windows to top-left corner of the screen.
            for (window in args.inCaptureWindows) {
                windowBackupRects += window.rect()
                windowBackupRectsWindows += window
                combinedWindowRectPos.put(combinedWindowRectPos.x min window.pos.x, combinedWindowRectPos.y min window.pos.y)
            }

            if (args.inFlags has CaptureFlag.StitchFullContents.i) {
                assert(!isCapturingRect) { "Capture Tool: capture of full window contents is not possible when capturing specified rect." }
                assert(args.inCaptureWindows.size == 1) { "Capture Tool: capture of full window contents is not possible when capturing more than one window." }

                // Resize window to it's contents and capture it's entire width/height. However if window is bigger than it's contents - keep original size.
                val window = args.inCaptureWindows.first()
                val fullSize = Vec2(window.sizeFull)

                // cursor to appear +-10px higher than it was positioned at.
                _mouseRelativeToWindowPos put (io.mousePos - window.pos + window.scroll)

                // FIXME-CAPTURE: Window width change may affect vertical content size if window contains text that wraps. To accurately position mouse cursor for capture we avoid horizontal resize.
                // Instead window width should be set manually before capture, as it is simple to do and most of the time we already have a window of desired width.
                //full_size.x = ImMax(window->SizeFull.x, window->ContentSize.x + (window->WindowPadding.x + window->WindowBorderSize) * 2);
                fullSize.y = window.sizeFull.y max (window.contentSize.y + (window.windowPadding.y + window.windowBorderSize) * 2 + window.titleBarHeight + window.menuBarHeight)
                window.setSize(fullSize)
                _hoveredWindow = g.hoveredWindow
            } else {
                _mouseRelativeToWindowPos put -Float.MAX_VALUE
                _hoveredWindow = null
            }
        } else
            assert(args === _captureArgs) // Capture args can not change mid-capture.

        //-----------------------------------------------------------------
        // Frame 1: Skipped to allow window size to update fully
        //-----------------------------------------------------------------

        //-----------------------------------------------------------------
        // Frame 2: Position windows, lock rectangle, create capture buffer
        //-----------------------------------------------------------------
        if (_frameNo == 2 || singleFrameCapture) {
            // Move group of windows so combined rectangle position is at the top-left corner + padding and create combined
            // capture rect of entire area that will be saved to screenshot. Doing this on the second frame because when
            // ImGuiCaptureToolFlags_StitchFullContents flag is used we need to allow window to reposition.
            val moveOffset = args.inPadding - combinedWindowRectPos
//            #ifdef IMGUI_HAS_VIEWPORT
//                if (io.ConfigFlags & ImGuiConfigFlags_ViewportsEnable)
//            {
//                ImGuiViewport* main_viewport = ImGui::GetMainViewport()
//                moveOffset += main_viewport->Pos
//            }
//            #endif
            for (window in args.inCaptureWindows) {
                // Repositioning of a window may take multiple frames, depending on whether window was already rendered or not.
                if (args.inFlags has CaptureFlag.StitchFullContents)
                    window.setPos(window.pos + moveOffset)
                captureRect add window.rect()
            }

            // Include padding in capture.
            if (!isCapturingRect)
                captureRect expand args.inPadding

//            ImRect clip_rect(ImVec2(0, 0), io.DisplaySize);
//            #ifdef IMGUI_HAS_VIEWPORT
//                    if (io.ConfigFlags & ImGuiConfigFlags_ViewportsEnable)
//            {
//                ImGuiViewport* main_viewport = ImGui::GetMainViewport();
//                clip_rect = ImRect(main_viewport->Pos, main_viewport->Pos + main_viewport->Size);
//            }
//            #endif
//            if (args->InFlags & ImGuiCaptureFlags_StitchFullContents)
//            IM_ASSERT(_CaptureRect.Min.x >= clip_rect.Min.x && _CaptureRect.Max.x <= clip_rect.Max.x);  // Horizontal stitching is not implemented. Do not allow capture that does not fit into viewport horizontally.
//            else
//            _CaptureRect.ClipWith(clip_rect);   // Can not capture area outside of screen. Clip capture rect, since we capturing only visible rect anyway.

            // Initialize capture buffer.
            args.outImageSize put captureRect.size
            output.createEmpty(captureRect.width.i, captureRect.height.i)
        }

        //-----------------------------------------------------------------
        // Frame 4+N*4: Capture a frame
        //-----------------------------------------------------------------
        if (_frameNo % 4 == 0 || (isRecordingGif && _frameNo > 2) || singleFrameCapture) {
            // FIXME: Implement capture of regions wider than viewport.
            // Capture a portion of image. Capturing of windows wider than viewport is not implemented yet.
            val captureRect = Rect(captureRect)
            val clipRect = Rect(Vec2(), io.displaySize)
//            #ifdef IMGUI_HAS_VIEWPORT
//                if (io.ConfigFlags & ImGuiConfigFlags_ViewportsEnable)
//            {
//                ImGuiViewport* main_viewport = ImGui::GetMainViewport()
//                clipRect = ImRect(main_viewport->Pos, main_viewport->Pos + main_viewport->Size)
//            }
//            #endif
            captureRect clipWith clipRect
            val captureHeight = io.displaySize.y min this.captureRect.height.i
            val x1 = (captureRect.min.x - clipRect.min.x).i
            val y1 = (captureRect.min.y - clipRect.min.y).i
            val w = captureRect.width.i
            val h = min(output.height - chunkNo * captureHeight, captureHeight).i
            if (h > 0) {

//                IM_ASSERT(w == output->Width);
//                if (args->InFlags & ImGuiCaptureFlags_StitchFullContents)
//                IM_ASSERT(h <= output->Height);     // When stitching, image can be taller than captured viewport.
//                else
//                IM_ASSERT(h == output->Height);

                if (!screenCaptureFunc!!(Vec4i(x1, y1, w, h), output.data!!.sliceAt(chunkNo * w * captureHeight), screenCaptureUserData)) {
                    println("Screen capture function failed.")
                    return CaptureStatus.Error
                }

                if (args.inFlags has CaptureFlag.StitchFullContents) {
                    // Window moves up in order to expose it's lower part.
                    for (window in args.inCaptureWindows)
                        window.setPos(window.pos - Vec2(0f, h.f))
                    captureRect translateY -h.f
                    chunkNo++
                }

                if (isRecordingGif) {
                    // _GifWriter is NULL when recording just started. Initialize recording state.
//                    const int gif_frame_interval = 100 / args->InRecordFPSTarget;
                    if (_gifWriter == null) {
                        // First GIF frame, initialize now that dimensions are known.
//                        unsigned width = (unsigned)capture_rect.GetWidth()
//                        unsigned height = (unsigned)capture_rect.GetHeight()
//                        IM_ASSERT(_GifWriter == NULL);
//                        _GifWriter = IM_NEW(GifWriter)
//                        GifBegin(_GifWriter, args->OutSavedFileName, width, height, gif_frame_interval)
                    }

//                    // Save new GIF frame
//                    // FIXME: Not optimal at all (e.g. compare to gifsicle -O3 output)
//                    GifWriteFrame(_GifWriter, (const uint8_t*)output->Data, output->Width, output->Height, gif_frame_interval, 8, false);
//                    _LastRecordedFrameTimeMs = current_time_ms;
                }
            }

            // Image is finalized immediately when we are not stitching. Otherwise image is finalized when we have captured and stitched all frames.
            if (!_recording && (args.inFlags hasnt CaptureFlag.StitchFullContents || h <= 0)) {
                output.removeAlpha()

                if (_gifWriter != null) {
                    // At this point _Recording is false, but we know we were recording because _GifWriter is not NULL. Finalize gif animation here.
//                    GifEnd(_GifWriter);
//                    IM_DELETE(_GifWriter);
//                    _GifWriter = NULL;
//                }
//                else if (args->InOutputImageBuf == NULL)
//                {
//                    // Save single frame.
//                    args->InFileCounter++;
//                    output->SaveFile(args->OutSavedFileName);
//                    output->Clear();
                }

                // Restore window positions unconditionally. We may have moved them ourselves during capture.
                for (i in windowBackupRects.indices) {
                    val window = windowBackupRectsWindows[i]
                    if (window.hidden) continue

                    val rect = windowBackupRects[i]
                    window.setPos(rect.min, Cond.Always)
                    window.setSize(rect.size, Cond.Always)
                }

                if (args.inFlags has CaptureFlag.HideMouseCursor)
                    g.io.mouseDrawCursor = _mouseDrawCursorBackup

                _frameNo = 0
                chunkNo = 0
                _lastRecordedFrameTimeSec = 0
                g.style.displayWindowPadding put displayWindowPaddingBackup
                g.style.displaySafeAreaPadding put displaySafeAreaPaddingBackup
                args.capturing = false
                _captureArgs = null
                return CaptureStatus.Done
            }
        }

        // Keep going
        _frameNo++
        return CaptureStatus.InProgress
    }

    // Begin gif capture. args->InOutputFileTemplate must be specified. Call CaptureUpdate() every frame afterwards until it returns false.
    fun beginGifCapture(args: CaptureArgs) {} // TODO sync
    fun endGifCapture() {} // TODO sync

    val isCapturingGif: Boolean
        get() = _recording || _gifWriter != null

    companion object {
        fun hideOtherWindows(args: CaptureArgs) {
            for (window in g.windows) {
//                #ifdef IMGUI_HAS_VIEWPORT
//                    if ((io.ConfigFlags & ImGuiConfigFlags_ViewportsEnable) && (args->InFlags & ImGuiCaptureFlags_StitchFullContents))
//                {
//                    // FIXME-VIEWPORTS: Content stitching is not possible because window would get moved out of main viewport and detach from it. We need a way to force captured windows to remain in main viewport here.
//                    IM_ASSERT(false);
//                    return ImGuiCaptureStatus_Error;
//                }
//                #endif

                var shouldHideWindow = !args.inCaptureWindows.contains(window)
                if (window.flags has Wf._ChildWindow)
                    shouldHideWindow = false
                else if (window.flags has Wf._Popup && args.inFlags has CaptureFlag.ExpandToIncludePopups)
                    shouldHideWindow = false
//                #if IMGUI_HAS_DOCK
//            else if ((window->Flags & ImGuiWindowFlags_DockNodeHost))
//                for (ImGuiWindow* capture_window : args->InCaptureWindows)
//                {
//                    if (capture_window->DockNode != NULL && capture_window->DockNode->HostWindow == window)
//                    {
//                        shouldHideWindow = false
//                        break
//                    }
//                }
//                #endif

                if (shouldHideWindow) {
                    // FIXME: We cannot just set ->Hidden because not sure of timing where this is called relative to other windows.
                    // two call sites for HideWindow() one in CaptureUpdate() which timing is not defined by specs, one in UI code (WHY?)
                    window.hidden = true
                    // FIXME: 2020/11/30 changed from overwriting HiddenFramesCannotSkipItems which has too many side-effects...
                    // Overwriting HiddenFramesCanSkipItems has less side effects.
                    // e.g. reopening Combo box after a capture pass it would be marked as "window_just_appearing_after_hidden_for_resize" in Begin(),
                    // leading to auto-positioning in Begin() using regular popup policy, which leads BeginCombo() on N+1 to use a wrong value for AutoPosLastDirection.
                    window.hiddenFramesCanSkipItems = 2
                }
            }
        }
    }
}

// Implements UI for capturing images
// (when using ImGuiTestEngine scripting API you may not need to use this at all)
class CaptureTool {
    val context = CaptureContext()                        // Screenshot capture context.
    var flags: CaptureFlags = CaptureFlag.Default_.i // Customize behavior of screenshot capture process. Flags are used by both ImGuiCaptureTool and ImGuiCaptureContext.
    var visible = false                // Tool visibility state.
    var padding = 16f                // Extra padding around captured area.
    var saveFileName = "captures/imgui_capture_%04d.png"              // File name where screenshots will be saved. May contain directories or variation of %d format.
    var snapGridSize = 32f           // Size of the grid cell for "snap to grid" functionality.
    var lastSaveFileName = ""          // File name of last captured file.

    var captureArgsPicker = CaptureArgs()             // Capture args for single window picker widget.
    var captureArgsSelector = CaptureArgs()           // Capture args for multiple window selector widget.
    var captureState: CaptureToolState = CaptureToolState.None // Which capture function is in progress.
    var windowNameMaxPosX = 170f    // X post after longest window name in CaptureWindowsSelector().

    // Public

    // Render a capture tool window with various options and utilities.
    fun showCaptureToolWindow(pOpen: KMutableProperty0<Boolean>? = null) {}
    fun setCaptureFunc(captureFunc: ScreenCaptureFunc) {}

    // [Internal]

    // Render a window picker that captures picked window to file specified in file_name.
    fun captureWindowPicker(title: String, args: CaptureArgs) {}

    // Render a selector for selecting multiple windows for capture.
    fun captureWindowsSelector(title: String, args: CaptureArgs) {}

    // Snaps edges of all visible windows to a virtual grid.
    fun snapWindowsToGrid(cellSize: Float, padding: Float) {}
}
