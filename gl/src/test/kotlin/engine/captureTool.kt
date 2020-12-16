package engine

import app.gApp
import engine.engine.TestEngineScreenCaptureFunc
import gli_.has
import gli_.hasnt
import glm_.f
import glm_.i
import glm_.max
import glm_.min
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import glm_.vec4.Vec4i
import imgui.*
import imgui.api.g
import imgui.api.gImGui
import imgui.internal.classes.Rect
import imgui.internal.classes.Window
import imgui.internal.sections.ItemFlag
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
    var data: ByteBuffer? = null // RGBA8

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
            data[p] = data[p] or COL32_A_MASK
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

enum class CaptureFlag(val i: CaptureFlags) {
    None(0),      //
    StitchFullContents(1 shl 0), // Expand window to it's content size and capture its full height.
    ExpandToIncludePopups(1 shl 1), // Expand capture area to automatically include visible popups and tooltips.
    HideMouseCursor(1 shl 2),   // Do not render software mouse cursor during capture.
    Instant(1 shl 3),   // Perform capture on very same frame. Only works when capturing a rectangular region. Unsupported features: content stitching, window hiding, window relocation.
}

infix fun Int.wo(f: CaptureFlag) = and(f.i.inv())
infix fun Int.has(f: CaptureFlag) = has(f.i)
infix fun Int.hasnt(f: CaptureFlag) = hasnt(f.i)

typealias CaptureFlags = Int

enum class CaptureToolState { None, PickingSingleWindow, Capturing }

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
}

enum class CaptureStatus { InProgress, Done, Error }

// Implements functionality for capturing images
class CaptureContext(

        // IO

        var screenCaptureFunc: TestEngineScreenCaptureFunc? = null) {              // Graphics backend specific function that captures specified portion of framebuffer and writes RGBA data to `pixels` buffer.

    var screenCaptureUserData: Any? = null                // Custom user pointer which is passed to ScreenCaptureFunc. (Optional)

    // [Internal]
    internal var _captureRect = Rect()                   // Viewport rect that is being captured.
    internal val _capturedWindowRect = Rect()       // Top-left corner of region that covers all windows included in capture. This is not same as _CaptureRect.Min when capturing explicitly specified rect.
    internal var chunkNo = 0                   // Number of chunk that is being captured when capture spans multiple frames.
    internal var _frameNo = 0                   // Frame number during capture process that spans multiple frames.
    internal val _windowBackupRects = ArrayList<Rect>()             // Backup window state that will be restored when screen capturing is done. Size and order matches windows of ImGuiCaptureArgs::InCaptureWindows.
    internal val _windowBackupRectsWindows = ArrayList<Window>()      // Backup windows that will have their state restored. args->InCaptureWindows can not be used because popups may get closed during capture and no longer appear in that list.
    internal var _displayWindowPaddingBackup = Vec2()   // Backup padding. We set it to {0, 0} during capture.
    internal var _displaySafeAreaPaddingBackup = Vec2()  // Backup padding. We set it to {0, 0} during capture.
    val _mouseRelativeToWindowPos = Vec2(-Float.MAX_VALUE)      // Mouse cursor position relative to captured window (when _StitchFullContents is in use).
    var _hoveredWindow: Window? = null          // Window which was hovered at capture start.
    var _captureBuf: CaptureImageBuf? = null                    // Output image buffer.
    var _captureArgs: CaptureArgs? = null            // Current capture args. Set only if capture is in progress.
    var _mouseDrawCursorBackup = false // Initial value of g.IO.MouseDrawCursor.

    // [Internal] Gif recording
    var _gifRecording = false          // Flag indicating that GIF recording is in progress.
    var _gifLastFrameTime = 0.0          // Time when last GIF frame was recorded.
    var _gifWriter: GifWriter? = null              // GIF image writer state.

    // Should be called after ImGui::NewFrame() and before submitting any UI.
    // (ImGuiTestEngine automatically calls that for you, so this only apply to independently created instance)
    fun postNewFrame() {

        val args = _captureArgs ?: return

        val g = gImGui!!

        // Override mouse cursor
        // FIXME: Could override in Pre+Post Render() hooks to avoid doing a backup.
        if (args.inFlags has CaptureFlag.HideMouseCursor) {
            if (_frameNo == 0)
                _mouseDrawCursorBackup = g.io.mouseDrawCursor
            g.io.mouseDrawCursor = false
        }

        // Force mouse position. Hovered window is reset in ImGui::NewFrame() based on mouse real mouse position.
        // FIXME: Would be saner to override io.MousePos in Pre NewFrame() hook.
        if (_frameNo > 2 && args.inFlags has CaptureFlag.StitchFullContents) {
            // Force mouse position. Hovered window is reset in ImGui::NewFrame() based on mouse real mouse position.
            assert(args.inCaptureWindows.size == 1)
            g.io.mousePos put (args.inCaptureWindows[0].pos + _mouseRelativeToWindowPos)
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

        if(_gifRecording) {
            assert(args.inOutputFileTemplate.isNotEmpty()) { "Output file must be specified when recording gif." }
            assert(args.inOutputImageBuf == null) { "Output buffer cannot be specified when recording gifs." }
            assert(args.inFlags hasnt CaptureFlag.StitchFullContents) { "Image stitching is not supported when recording gifs." }
        }

        val output = args.inOutputImageBuf ?: _captureBuf!!
        val viewportRect = getMainViewportRect()

        // Hide other windows so they can't be seen visible behind captured window
        if (args.inCaptureWindows.isNotEmpty())
            hideOtherWindows(args)

        // Recording will be set to false when we are stopping GIF capture.
        val isRecordingGif = isCapturingGif
        val currentTimeSec = ImGui.time

        if (isRecordingGif || _gifLastFrameTime > 0.0) {
            val deltaSec = currentTimeSec - _gifLastFrameTime
            if (deltaSec < 1.0 / args.inRecordFPSTarget)
                return CaptureStatus.InProgress
        }

        // Capture can be performed in single frame if we are capturing a rect.
        val instantCapture = args.inFlags has CaptureFlag.Instant
        val isCapturingRect = args.inCaptureRect.width > 0 && args.inCaptureRect.height > 0
        if (instantCapture) {
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
//                    printf("ImGuiCaptureContext: unable to create directory for file '%s'.\n", args->OutSavedFileName);
//                    return ImGuiCaptureToolStatus_Error;
//                }
//
//                // File template will most likely end with .png, but we need .gif for animated images.
//                if (is_recording_gif)
//                  if (char* ext = (char*)ImPathFindExtension(args->OutSavedFileName))
//                    ImStrncpy(ext, ".gif", ext - args->OutSavedFileName);
//            }

            // When recording, same args should have been passed to BeginGifCapture().
            assert(!_gifRecording || _captureArgs === args)

            _captureArgs = args
            chunkNo = 0
            _captureRect.put(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            _capturedWindowRect put _captureRect
            _windowBackupRects.clear()
            _windowBackupRectsWindows.clear()
            _displayWindowPaddingBackup = g.style.displayWindowPadding
            _displaySafeAreaPaddingBackup = g.style.displaySafeAreaPadding
            g.style.displayWindowPadding put 0    // Allow windows to be positioned fully outside of visible viewport.
            g.style.displaySafeAreaPadding put 0

            if (isCapturingRect) {
                // Capture arbitrary rectangle. If any windows are specified in this mode only they will appear in captured region.
                _captureRect put args.inCaptureRect
                if (args.inCaptureWindows.isEmpty() && !instantCapture) {
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
                _capturedWindowRect add window.rect()
                _windowBackupRects += window.rect()
                _windowBackupRectsWindows += window
            }

            if (args.inFlags has CaptureFlag.StitchFullContents.i) {
                assert(!isCapturingRect) { "ImGuiCaptureContext: capture of full window contents is not possible when capturing specified rect." }
                assert(args.inCaptureWindows.size == 1) { "ImGuiCaptureContext: capture of full window contents is not possible when capturing more than one window." }

                // Resize window to it's contents and capture it's entire width/height. However if window is bigger than it's contents - keep original size.
                val window = args.inCaptureWindows[0]
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
        if (_frameNo == 2 || instantCapture) {
            // Move group of windows so combined rectangle position is at the top-left corner + padding and create combined
            // capture rect of entire area that will be saved to screenshot. Doing this on the second frame because when
            // ImGuiCaptureToolFlags_StitchFullContents flag is used we need to allow window to reposition.
            val moveOffset = Vec2(args.inPadding) - _capturedWindowRect.min + viewportRect.min
            for (window in args.inCaptureWindows) {
                // Repositioning of a window may take multiple frames, depending on whether window was already rendered or not.
                if (args.inFlags has CaptureFlag.StitchFullContents)
                    window.setPos(window.pos + moveOffset)
                _captureRect add window.rect()
            }

            // Include padding in capture.
            if (!isCapturingRect)
                _captureRect expand args.inPadding

//            const ImRect clip_rect = viewport_rect
//            if (args->InFlags & ImGuiCaptureFlags_StitchFullContents)
//            IM_ASSERT(_CaptureRect.Min.x >= clip_rect.Min.x && _CaptureRect.Max.x <= clip_rect.Max.x);  // Horizontal stitching is not implemented. Do not allow capture that does not fit into viewport horizontally.
//            else
//            _CaptureRect.ClipWith(clip_rect);   // Can not capture area outside of screen. Clip capture rect, since we capturing only visible rect anyway.

            // Initialize capture buffer.
            args.outImageSize put _captureRect.size
            output.createEmpty(_captureRect.width.i, _captureRect.height.i)
        }

        //-----------------------------------------------------------------
        // Frame 4+N*4: Capture a frame
        //-----------------------------------------------------------------
        if ((_frameNo > 2 && _frameNo % 4 == 0) || (isRecordingGif && _frameNo > 2) || instantCapture) {
            // FIXME: Implement capture of regions wider than viewport.
            // Capture a portion of image. Capturing of windows wider than viewport is not implemented yet.
            val clipRect = Rect(viewportRect)
            val captureRect = Rect(_captureRect)
            captureRect clipWith clipRect
            val captureHeight = io.displaySize.y min this._captureRect.height.i
            val x1 = (captureRect.min.x - clipRect.min.x).i
            val y1 = (captureRect.min.y - clipRect.min.y).i
            val w = captureRect.width.i
            val h = min(output.height - chunkNo * captureHeight, captureHeight).i
            if (h > 0) {
                assert(w == output.width)
                if (args.inFlags has CaptureFlag.StitchFullContents)
                    assert(h <= output.height)     // When stitching, image can be taller than captured viewport.
                else
                    assert(h == output.height)

                var viewportId: ID = 0
                if (!screenCaptureFunc!!(viewportId, Vec4i(x1, y1, w, h), output.data!!.sliceAt(chunkNo * w * captureHeight), screenCaptureUserData)) {
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
//                        const unsigned width = (unsigned)capture_rect.GetWidth()
//                        const unsigned height = (unsigned)capture_rect.GetHeight()
//                        IM_ASSERT(_GifWriter == NULL);
//                        _GifWriter = IM_NEW(GifWriter)
//                        GifBegin(_GifWriter, args->OutSavedFileName, width, height, gif_frame_interval)
                    }

//                    // Save new GIF frame
//                    // FIXME: Not optimal at all (e.g. compare to gifsicle -O3 output)
//                    GifWriteFrame(_GifWriter, (const uint8_t*)output->Data, output->Width, output->Height, gif_frame_interval, 8, false);
//                    _GifLastFrameTime = current_time_ms;
                }
            }

            // Image is finalized immediately when we are not stitching. Otherwise image is finalized when we have captured and stitched all frames.
            if (!_gifRecording && (args.inFlags hasnt CaptureFlag.StitchFullContents || h <= 0)) {
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
                for (i in _windowBackupRects.indices) {
                    val window = _windowBackupRectsWindows[i]
                    if (window.hidden) continue

                    window.setPos(_windowBackupRects[i].min, Cond.Always)
                    window.setSize(_windowBackupRects[i].size, Cond.Always)
                }
                if (args.inFlags has CaptureFlag.HideMouseCursor)
                    g.io.mouseDrawCursor = _mouseDrawCursorBackup
                g.style.displayWindowPadding put _displayWindowPaddingBackup
                g.style.displaySafeAreaPadding put _displaySafeAreaPaddingBackup

                _frameNo = 0
                chunkNo = 0
                _gifLastFrameTime = 0.0
                _captureArgs = null
                return CaptureStatus.Done
            }
        }

        // Keep going
        _frameNo++
        return CaptureStatus.InProgress
    }

    // Begin gif capture. Call CaptureUpdate() every frame afterwards until it returns false.
    fun beginGifCapture(args: CaptureArgs) {} // TODO sync
    fun endGifCapture() {} // TODO sync

    val isCapturingGif: Boolean
        get() = _gifRecording || _gifWriter != null

    companion object {
        fun hideOtherWindows(args: CaptureArgs) {
            for (window in g.windows) {
//                #ifdef IMGUI_HAS_VIEWPORT
//                // FIXME-VIEWPORTS: Content stitching is not possible because window would get moved out of main viewport and detach from it. We need a way to force captured windows to remain in main viewport here.
//        //if ((io.ConfigFlags & ImGuiConfigFlags_ViewportsEnable) && (args->InFlags & ImGuiCaptureFlags_StitchFullContents))
//        //    IM_ASSERT(false);
//                #endif

                if (window in args.inCaptureWindows)
                    continue
                if (window.flags has Wf._ChildWindow)
                    continue
                else if (window.flags has Wf._Popup && args.inFlags has CaptureFlag.ExpandToIncludePopups)
                    continue
//                #if IMGUI_HAS_DOCK
//                bool should_hide_window = true;
//             if ((window->Flags & ImGuiWindowFlags_DockNodeHost))
//                for (ImGuiWindow* capture_window : args->InCaptureWindows)
//                {
//                    if (capture_window->DockNode != NULL && capture_window->DockNode->HostWindow == window)
//                    {
//                        shouldHideWindow = false
//                        break
//                    }
//                }
//                if (!should_hide_window)
//                    continue;
//                #endif

                // Not overwriting HiddenFramesCanSkipItems or HiddenFramesCannotSkipItems since they have side-effects (e.g. preserving ContentsSize)
                if (window.wasActive || window.active)
                    window.hiddenFramesForRenderOnly = 2
            }
        }
    }
}

fun getMainViewportRect(): Rect {
//    #ifdef IMGUI_HAS_VIEWPORT
//        if (g.IO.ConfigFlags & ImGuiConfigFlags_ViewportsEnable)
//    {
//        ImGuiViewport* main_viewport = ImGui::GetMainViewport();
//        return ImRect(main_viewport->Pos, main_viewport->Pos + main_viewport->Size);
//    }
//    #endif
    return Rect(Vec2(0), g.io.displaySize)
}

fun pushDisabled() {
    val style = ImGui.style
    val col = style.colors[Col.Text]
    ImGui.pushItemFlag(ItemFlag.Disabled.i, true)
    ImGui.pushStyleColor(Col.Text, Vec4(col.x, col.y, col.z, col.w * 0.5f))
}

fun popDisabled() {
    ImGui.popStyleColor()
    ImGui.popItemFlag()
}

// Implements UI for capturing images
// (when using ImGuiTestEngine scripting API you may not need to use this at all)
class CaptureTool {
    val context = CaptureContext()                        // Screenshot capture context.
    var visible = false                // Tool visibility state (as a convenience for user)
    var snapGridSize = 32f           // Size of the grid cell for "snap to grid" functionality.
    var lastOutputFileName = ""          // File name of last captured file.

    var _captureArgs = CaptureArgs()             // Capture args
    var captureState: CaptureToolState = CaptureToolState.None // Which capture function is in progress.
    val _selectedWindows = ArrayList<ID>()

    init {
        _captureArgs.inOutputFileTemplate = "captures/imgui_capture_%04d.png"
    }

    // Public

    // Render a capture tool window with various options and utilities.
    fun showCaptureToolWindow(pOpen: KMutableProperty0<Boolean>? = null) {}
    fun setCaptureFunc(captureFunc: TestEngineScreenCaptureFunc) {}

    // [Internal]

    // Render a window picker that captures picked window to file specified in file_name.
    fun captureWindowPicker(args: CaptureArgs) {}

    // Render a selector for selecting multiple windows for capture.
    fun captureWindowsSelector(args: CaptureArgs) {}

    // Snaps edges of all visible windows to a virtual grid.
    fun snapWindowsToGrid(cellSize: Float, padding: Float) {}
}
