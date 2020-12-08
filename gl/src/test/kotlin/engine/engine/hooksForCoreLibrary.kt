package engine.engine

import engine.StackLevelInfo
import engine.TestEngine
import engine.context.logEx
import glm_.L
import glm_.func.common.min
import glm_.hasnt
import imgui.DataType
import imgui.ID
import imgui.classes.Context
import imgui.internal.classes.Rect
import imgui.internal.sections.ItemStatusFlag
import imgui.internal.sections.ItemStatusFlags
import imgui.internal.sections.NavLayer

//-------------------------------------------------------------------------
// [SECTION] HOOKS FOR CORE LIBRARY
//-------------------------------------------------------------------------
// - ImGuiTestEngineHook_ItemAdd()
// - ImGuiTestEngineHook_ItemInfo()
// - ImGuiTestEngineHook_Log()
// - ImGuiTestEngineHook_IdInfo()
// - ImGuiTestEngineHook_AssertFunc()
//-------------------------------------------------------------------------

fun hook_Shutdown(uiCtx: Context) {
    val engine = uiCtx.testEngine as? TestEngine
    engine?.unbindImGuiContext(uiCtx)
}

fun hook_prenewframe(uiCtx: Context) {
    (uiCtx.testEngine as? TestEngine)?.preNewFrame(uiCtx)
}

fun hook_postnewframe(uiCtx: Context) {
    (uiCtx.testEngine as? TestEngine)?.postNewFrame(uiCtx)
}

fun hook_itemAdd(uiCtx: Context, bb: Rect, id: ID) {

    val engine = uiCtx.testEngine as TestEngine

    assert(id != 0)
    val g = uiCtx
    val window = g.currentWindow!!

    // FIXME-OPT: Early out if there are no active Info/Gather tasks.

    // Info Tasks
    engine.findLocateTask(id)?.let { task ->
        task.result.also {
            it.timestampMain = g.frameCount
            it.id = id
            it.parentID = window.idStack.last()
            it.window = window
            it.rectFull put bb
            it.rectClipped put bb
            it.rectClipped clipWithFull window.clipRect      // This two step clipping is important, we want RectClipped to stays within RectFull
            it.rectClipped clipWithFull it.rectFull
            it.navLayer = window.dc.navLayerCurrent
            it.depth = 0
            it.statusFlags = when (window.dc.lastItemId) {
                id -> window.dc.lastItemStatusFlags
                else -> ItemStatusFlag.None.i
            }
        }
    }

    // Stack ID query
    // (Note: this assume that the ID was computed with the current ID stack, which tends to be the case for our widget)
    if (engine.stackTool.queryStackId == id && engine.stackTool.queryStep == 0) {
        //IM_ASSERT(engine->StackTool.Results.Size == 0); // double query OR id conflict?
        engine.stackTool.queryStep++
//        engine.stackTool.results.resize(window->IDStack.Size + 1)
        for (n in 0 until window.idStack.size + 1) {
            val info = StackLevelInfo()
            info.id = window.idStack.getOrElse(n) { id }
            engine.stackTool.results += info
        }
    }

    // Gather Task (only 1 can be active)
    if (engine.gatherTask.inParentID != 0 && window.dc.navLayerCurrent == NavLayer.Main) { // FIXME: Layer filter?
        val gatherParentId = engine.gatherTask.inParentID
        var depth = -1
        if (gatherParentId == window.idStack.last())
            depth = 0
        else {
            val maxDepth = window.idStack.size min engine.gatherTask.inDepth
            for (nDepth in 1 until maxDepth)
                if (window.idStack[window.idStack.lastIndex - nDepth] == gatherParentId) {
                    depth = nDepth
                    break
                }
        }
        if (depth != -1)
            engine.gatherTask.lastItemInfo = engine.gatherTask.outList!!.getOrAddByKey(id).also {
                it.timestampMain = engine.frameCount
                it.id = id
                it.parentID = window.idStack.last()
                it.window = window
                it.rectFull put bb
                it.rectClipped put bb
                it.rectClipped clipWithFull window.clipRect      // This two step clipping is important, we want RectClipped to stays within RectFull
                it.rectClipped clipWithFull it.rectFull
                it.navLayer = window.dc.navLayerCurrent
                it.depth = depth
            }
    }
}

// label is optional
fun TestEngineHook_ItemInfo(uiCtx: Context, id: ID, label: String, flags: ItemStatusFlags) {

    val engine = uiCtx.testEngine as TestEngine

    assert(id != 0)
    val g = uiCtx
    //ImGuiWindow* window = g.CurrentWindow;
    //IM_ASSERT(window->DC.LastItemId == id || window->DC.LastItemId == 0); // Need _ItemAdd() to be submitted before _ItemInfo()

    // Update Info Task status flags
    engine.findLocateTask(id)?.let { task ->
        task.result.also {
            it.timestampStatus = g.frameCount
            it.statusFlags = flags
            if (label.isNotEmpty())
                it.debugLabel = label
        }
    }

    // Update Gather Task status flags
    engine.gatherTask.lastItemInfo?.let {
        if (it.id == id) {
            it.timestampStatus = g.frameCount
            it.statusFlags = flags
            if (label.isNotEmpty())
                it.debugLabel = label
        }
    }

    // Update Find by Label Task
    val labelTask = engine.findByLabelTask
    val inLabel = labelTask.inLabel
    if (labelTask.outItemId == 0 && inLabel != null && inLabel == label) {
        var match = false //(label_task->InBaseId == 0);
        if (!match) {
            // FIXME-TESTS: Depth limit?
            for (pIdStack in g.currentWindow!!.idStack.asReversed())
                if (pIdStack == labelTask.inBaseId) {
                    val filterFlags = labelTask.inFilterItemStatusFlags
                    if (filterFlags != 0)
                        if (filterFlags hasnt flags)
                            continue
                    match = true
                    break
                }
        }
        // FIXME-TESTS: Return other than final id
        if (match)
            labelTask.outItemId = id
    }
}

// Forward core/user-land text to test log
fun hook_log(uiCtx: Context, fmt: String) {
    val engine = uiCtx.testEngine as TestEngine

    engine.testContext!!.logEx(TestVerboseLevel.Debug, TestLogFlag.None.i, fmt)
}

fun hook_IdInfo(uiCtx: Context, dataType: DataType, id: ID, dataId: Any) {
    val engine = uiCtx.testEngine as TestEngine
    val info = engine.stackTool.queryIdInfoOutput!!
    assert(engine.stackTool.results.indexOf(info) != -1)
    assert(info.id == id)

    if (dataType == DataType.Int)
        info.desc = "int ${(dataId as Int).L}"
    else if (dataType == DataType._String)
        info.desc = "str \"${dataId as String}\""
    else if (dataType == DataType._Pointer)
        info.desc = "ptr ${dataId.hashCode()}"
    else if (dataType == DataType._ID) {
        if (!info.querySuccess)
            info.desc = "ovr 0x%08X".format(id)
    } else
        assert(false)
    info.querySuccess = true
}

//fun hook_idInfo(uiCtx: Context, dataType: DataType, id: ID, dataId: Any, const void* data_id_end)
//{
//    ImGuiTestEngine * engine = (ImGuiTestEngine *) ui_ctx->TestEngine
//    ImGuiStackLevelInfo * info = engine->StackTool.QueryIdInfoOutput
//    IM_ASSERT(engine->StackTool.Results.index_from_ptr(info) != -1)
//    IM_ASSERT(info->ID == id)
//
//    if (data_type == ImGuiDataType_String) {
//        if (data_id_end)
//            ImFormatString(info->Desc, IM_ARRAYSIZE(info->Desc), "str \"%.*s\"", (int)((const char*)data_id_end-(const char*)data_id), (const char*)data_id)
//        else
//        ImFormatString(info->Desc, IM_ARRAYSIZE(info->Desc), "str \"%s\"", (const char*)data_id)
//    } else
//        IM_ASSERT(0)
//    info->QuerySuccess = true
//}

//fun hookAssertfunc(expr: String, const char* file, const char* function, int line)
//{
//    ImGuiTestEngine* engine = GImGuiTestEngine;
//    if (ImGuiTestContext* ctx = engine->TestContext)
//    {
//        ctx->LogError("Assert: '%s'", expr);
//        ctx->LogWarning("In %s:%d, function %s()", file, line, function);
//        if (ImGuiTest* test = ctx->Test)
//            ctx->LogWarning("While running test: %s %s", test->Category, test->Name);
//    }
//
//    // Consider using github.com/scottt/debugbreak
//    #ifdef _MSC_VER
//        __debugbreak();
//    #else
//    IM_ASSERT(0);
//    #endif
//}
