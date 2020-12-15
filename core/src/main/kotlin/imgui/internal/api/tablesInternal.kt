package imgui.internal.api

import glm_.f
import glm_.has
import glm_.max
import glm_.min
import glm_.vec2.Vec2
import imgui.*
import imgui.ImGui.beginChildEx
import imgui.api.g
import imgui.ImGui.calcItemSize
import imgui.ImGui.isClippedEx
import imgui.ImGui.itemSize
import imgui.ImGui.pushOverrideID
import imgui.ImGui.setNextWindowContentSize
import imgui.internal.classes.*
import kotlin.math.ceil
import imgui.TableColumnFlag as Tcf
import imgui.TableFlag as Tf
import imgui.TableRowFlag as Trf
import imgui.WindowFlag as Wf

// Tables: Internals
interface tablesInternal {

    fun tableFindByID(id: ID): Table? = g.tables.getByKey(id)

    fun beginTableEx(name: String, id: ID, columnsCount: Int, flags_: TableFlags = Tf.None.i, outerSize: Vec2 = Vec2(),
                     innerWidth: Float = 0f): Boolean {

        var flags = flags_
        val outerWindow = ImGui.currentWindow
        if (outerWindow.skipItems) // Consistent with other tables + beneficial side effect that assert on miscalling EndTable() will be more visible.
            return false

        // Sanity checks
        assert(columnsCount > 0 && columnsCount <= TABLE_MAX_COLUMNS) { "Only 1..64 columns allowed!" }
        if (flags has Tf.ScrollX)
            assert(innerWidth >= 0f)

        // If an outer size is specified ahead we will be able to early out when not visible. Exact clipping rules may evolve.
        val useChildWindow = flags has (Tf.ScrollX or Tf.ScrollY)
        val availSize = ImGui.contentRegionAvail
        val actualOuterSize = calcItemSize(outerSize, availSize.x max 1f, if(useChildWindow) availSize.y max 1f else 0f)
        val outerRect = Rect(outerWindow.dc.cursorPos, outerWindow.dc.cursorPos + actualOuterSize)
        if (useChildWindow && isClippedEx(outerRect, 0, false)) {
            itemSize(outerRect)
            return false
        }

        // Acquire storage for the table
        val table = g.tables.getOrAddByKey(id)
        val instanceNo = if(table.lastFrameActive != g.frameCount) 0 else table.instanceCurrent + 1
        val instanceId: ID = id + instanceNo
        val tableLastFlags = table.flags
        if (instanceNo > 0)
            assert(table.columnsCount == columnsCount) { "BeginTable(): Cannot change columns count mid-frame while preserving same ID" }

        // Fix flags
        flags = tableFixFlags(flags, outerWindow)

        // Initialize
        table.id = id
        table.flags = flags
        table.instanceCurrent = instanceNo
        table.lastFrameActive = g.frameCount
        table.outerWindow = outerWindow
        table.innerWindow = outerWindow
        table.columnsCount = columnsCount
        table.isLayoutLocked = false
        table.innerWidth = innerWidth
        table.outerRect put outerRect
        table.workRect put outerRect

        // When not using a child window, WorkRect.Max will grow as we append contents.
        if (useChildWindow) {
            // Ensure no vertical scrollbar appears if we only want horizontal one, to make flag consistent
            // (we have no other way to disable vertical scrollbar of a window while keeping the horizontal one showing)
            val overrideContentSize = Vec2(Float.MAX_VALUE)
            if (flags has Tf.ScrollX && flags hasnt Tf.ScrollY)
                overrideContentSize.y = Float.MIN_VALUE

            // Ensure specified width (when not specified, Stretched columns will act as if the width == OuterWidth and
            // never lead to any scrolling). We don't handle inner_width < 0.0f, we could potentially use it to right-align
            // based on the right side of the child window work rect, which would require knowing ahead if we are going to
            // have decoration taking horizontal spaces (typically a vertical scrollbar).
            if (flags has Tf.ScrollX && innerWidth > 0f)
                overrideContentSize.x = innerWidth

            if (overrideContentSize.x != Float.MAX_VALUE || overrideContentSize.y != Float.MAX_VALUE) // TODO glm -> anyNotEqual
                setNextWindowContentSize(Vec2{ if(overrideContentSize[it] != Float.MAX_VALUE) overrideContentSize[it] else 0f})

            // Create scrolling region (without border and zero window padding)
            val childFlags = if(flags has Tf.ScrollX) Wf.HorizontalScrollbar else Wf.None
            beginChildEx(name, instanceId, table.outerRect.size, false, childFlags.i)
            table.innerWindow = g.currentWindow
            val inner = table.innerWindow!!
            table.workRect put inner.workRect
            table.outerRect put inner.rect()
            assert(inner.windowPadding.x == 0f && inner.windowPadding.y == 0f && inner.windowBorderSize == 0f) // TODO glm -> allEqual
        }

        // Push a standardized ID for both child-using and not-child-using tables
        pushOverrideID(instanceId)

        // Backup a copy of host window members we will modify
        val innerWindow = table.innerWindow!!
        table.hostIndentX = innerWindow.dc.indent
        table.hostClipRect put innerWindow.clipRect
        table.hostSkipItems = innerWindow.skipItems
        table.hostBackupWorkRect put innerWindow.workRect
        table.hostBackupParentWorkRect put innerWindow.parentWorkRect
        table.hostBackupColumnsOffset = outerWindow.dc.columnsOffset
        table.hostBackupPrevLineSize put innerWindow.dc.prevLineSize
        table.hostBackupCurrLineSize put innerWindow.dc.currLineSize
        table.hostBackupCursorMaxPos put innerWindow.dc.cursorMaxPos
        table.hostBackupItemWidth = outerWindow.dc.itemWidth
        table.hostBackupItemWidthStackSize = outerWindow.dc.itemWidthStack.size
        innerWindow.parentWorkRect put table.workRect
        innerWindow.dc.prevLineSize put 0f
        innerWindow.dc.currLineSize put 0f

        // Padding and Spacing
        // - None               ........Content..... Pad .....Content........
        // - PadOuter           | Pad ..Content..... Pad .....Content.. Pad |
        // - PadInner           ........Content.. Pad | Pad ..Content........
        // - PadOuter+PadInner  | Pad ..Content.. Pad | Pad ..Content.. Pad |
        val padOuterX = when {
                flags has Tf.NoPadOuterX -> false
                else -> when {
                        flags has Tf.PadOuterX -> true
                        else -> flags has Tf.BordersOuterV
                }
        }
        val padInnerX = flags hasnt Tf.NoPadInnerX
        val innerSpacingForBorder = if(flags has Tf.BordersInnerV) TABLE_BORDER_SIZE else 0f
        val innerSpacingExplicit = if(padInnerX && flags hasnt Tf.BordersInnerV) g.style.cellPadding.x else 0f
        val innerPaddingExplicit = if(padInnerX && flags has Tf.BordersInnerV) g.style.cellPadding.x else 0f
        table.cellSpacingX1 = innerSpacingExplicit + innerSpacingForBorder
        table.cellSpacingX2 = innerSpacingExplicit - table.cellSpacingX1
        table.cellPaddingX = innerSpacingExplicit
        table.cellPaddingY = g.style.cellPadding.y

        val outerPaddingForBorder = if(flags has Tf.BordersOuterV) TABLE_BORDER_SIZE else 0f
        val outerPaddingExplicit = if(padOuterX) g.style.cellPadding.x else 0f
        table.outerPaddingX = (outerPaddingForBorder + outerPaddingExplicit) - table.cellPaddingX

        table.currentColumn = -1
        table.currentRow = -1
        table.rowBgColorCounter = 0
        table.lastRowFlags = Trf.None.i
        table.innerClipRect put if(innerWindow === outerWindow) table.workRect else innerWindow.clipRect
        table.innerClipRect clipWith table.workRect     // We need this to honor inner_width
        table.innerClipRect clipWithFull table.hostClipRect
        table.innerClipRect.max.y = if(flags has Tf.NoHostExtendY) table.innerClipRect.max.y min innerWindow.workRect.max.y else innerWindow.clipRect.max.y

        // Initial draw cmd starts with a BgClipRect that matches the one of its host, to facilitate merge draw commands by default.
        // This is because all our cell highlight are manually clipped with BgClipRect
        // Larger at first, if/after unfreezing will become same as tight
        table.bgClipRect put table.innerClipRect
        table.bgClipRectForDrawCmd put table.hostClipRect
        assert(table.bgClipRect.min.y <= table.bgClipRect.max.y)

        table.rowPosY1 = table.workRect.min.y // This is needed somehow
        table.rowPosY2 = table.rowPosY1 // "
        table.rowTextBaseline = 0f // This will be cleared again by TableBeginRow()
        table.freezeRowsRequest = 0 // This will be setup by TableSetupScrollFreeze(), if any
                table.freezeRowsCount = 0 // "
        table.freezeColumnsRequest = 0
        table.freezeColumnsCount = 0
        table.isUnfrozen = true
        table.declColumnsCount = 0
        table.rightMostEnabledColumn = -1

        // Using opaque colors facilitate overlapping elements of the grid
        table.borderColorStrong = Col.TableBorderStrong.u32
        table.borderColorLight = Col.TableBorderLight.u32
        table.borderX1 = table.innerClipRect.min.x// +((table->Flags & ImGuiTableFlags_BordersOuter) ? 0.0f : -1.0f);
        table.borderX2 = table.innerClipRect.max.x// +((table->Flags & ImGuiTableFlags_BordersOuter) ? 0.0f : +1.0f);

        // Make table current
        val tableIdx = g.tables.getIndex(table)
        g.currentTableStack += PtrOrIndex(tableIdx)
        g.currentTable = table
        outerWindow.dc.currentTableIdx = tableIdx.i
        if (innerWindow !== outerWindow) // So EndChild() within the inner window can restore the table properly.
            innerWindow.dc.currentTableIdx = tableIdx.i

        if (tableLastFlags has Tf.Reorderable && flags hasnt Tf.Reorderable)
            table.isResetDisplayOrderRequest = true

        // Mark as used
        if (tableIdx >= g.tablesLastTimeActive.size)
            for (i in g.tablesLastTimeActive.size until tableIdx.i)
                g.tablesLastTimeActive += -1f
        g.tablesLastTimeActive[tableIdx.i] = g.time.f
        table.memoryCompacted = false

        // Setup memory buffer (clear data if columns count changed)
        val storedSize = table.columns.size
        if (storedSize != 0 && storedSize != columnsCount) {
//            IM_FREE(table->RawData);
//            table->RawData = NULL;
        }
//        if (table->RawData == NULL)
        if (table.columns.isEmpty()) {
            table beginInitMemory columnsCount
            table.isInitializing = true
            table.isSettingsRequestLoad = true
        }
        if (table.isResetAllRequest)
            table.resetSettings()
        if (table.isInitializing)
        {
            // Initialize for new settings
            table.settingsOffset = -1
            table.isSortSpecsDirty = true
            for (n in 0 until columnsCount) {
                var column = table.columns[n]
                val widthAuto = column.widthAuto
//                *column = ImGuiTableColumn()
                table.columns[n] = TableColumn()
                column = table.columns[n]
                column.widthAuto = widthAuto
                column.isPreserveWidthAuto = true // Preserve WidthAuto when reinitializing a live table: not technically necessary but remove a visible flicker
                column.displayOrder = n
                table.displayOrderToIndex[n] = n
                column.isEnabled = true
                column.isEnabledNextFrame = true
            }
        }

        // Load settings
        if (table.isSettingsRequestLoad)
            table.loadSettings()

        // Handle DPI/font resize
        // This is designed to facilitate DPI changes with the assumption that e.g. style.CellPadding has been scaled as well.
        // It will also react to changing fonts with mixed results. It doesn't need to be perfect but merely provide a decent transition.
        // FIXME-DPI: Provide consistent standards for reference size. Perhaps using g.CurrentDpiScale would be more self explanatory.
        // This is will lead us to non-rounded WidthRequest in columns, which should work but is a poorly tested path.
        val newRefScaleUnit = g.fontSize // g.Font->GetCharAdvance('A') ?
        if (table.refScale != 0f && table.refScale != newRefScaleUnit) {
            val scaleFactor = newRefScaleUnit / table.refScale
            //IMGUI_DEBUG_LOG("[table] %08X RefScaleUnit %.3f -> %.3f, scaling width by %.3f\n", table->ID, table->RefScaleUnit, new_ref_scale_unit, scale_factor);
            for (n in 0 until columnsCount)
                table.columns[n].widthRequest = table.columns[n].widthRequest * scaleFactor
        }
        table.refScale = newRefScaleUnit

        // Disable output until user calls TableNextRow() or TableNextColumn() leading to the TableUpdateLayout() call..
        // This is not strictly necessary but will reduce cases were "out of table" output will be misleading to the user.
        // Because we cannot safely assert in EndTable() when no rows have been created, this seems like our best option.
        innerWindow.skipItems = true

        // Clear names
        // FIXME-TABLE: probably could be done differently...
        if (table.columnsNames.isNotEmpty()) {
            table.columnsNames.clear()
            for (columnN in 0 until table.columnsCount) {
                val column = table.columns[columnN]
                column.nameOffset = -1
            }
        }

        // Apply queued resizing/reordering/hiding requests
        table.beginApplyRequests()

        return true
    }

//  -> Table class
//    IMGUI_API void          TableBeginInitMemory(ImGuiTable* table, int columns_count);
//    IMGUI_API void          TableBeginApplyRequests(ImGuiTable* table);
//    IMGUI_API void          TableSetupDrawChannels(ImGuiTable* table);
//    IMGUI_API void          TableUpdateLayout(ImGuiTable* table);
//    IMGUI_API void          TableUpdateBorders(ImGuiTable* table);
//    IMGUI_API void          TableDrawBorders(ImGuiTable* table);
//    IMGUI_API void          TableDrawContextMenu(ImGuiTable* table);
//    IMGUI_API void          TableMergeDrawChannels(ImGuiTable* table);
//    IMGUI_API void          TableSortSpecsSanitize(ImGuiTable* table);
//    IMGUI_API void          TableSortSpecsBuild(ImGuiTable* table);

    fun tableFixColumnSortDirection(column: TableColumn) {
        // Initial sort state
        if (column.sortDirection == SortDirection.None)
            column.sortDirection = when {
                column.flags has Tcf.PreferSortDescending -> SortDirection.Descending
                else -> SortDirection.Ascending
            }
        // Handle NoSortAscending/NoSortDescending
        if (column.sortDirection == SortDirection.Ascending && column.flags has Tcf.NoSortAscending)
            column.sortDirection = SortDirection.Descending
        else if (column.sortDirection == SortDirection.Descending && column.flags has Tcf.NoSortDescending)
            column.sortDirection = SortDirection.Ascending
    }

    //  -> Table class
//    IMGUI_API void          TableBeginRow(ImGuiTable* table)
//    IMGUI_API void          TableEndRow(ImGuiTable* table)
//    IMGUI_API void          TableBeginCell(ImGuiTable* table, int column_n)
//    IMGUI_API void          TableEndCell(ImGuiTable* table)
//    IMGUI_API ImRect        TableGetCellBgRect(const ImGuiTable* table, int column_n)
//    IMGUI_API const char*   TableGetColumnName(const ImGuiTable* table, int column_n)
//    IMGUI_API ImGuiID       TableGetColumnResizeID(const ImGuiTable* table, int column_n, int instance_no = 0)
//    IMGUI_API void          TableSetColumnWidthAutoSingle(ImGuiTable* table, int column_n)
//    IMGUI_API void          TableSetColumnWidthAutoAll(ImGuiTable* table)
//    IMGUI_API void          TableRemove(ImGuiTable* table)
//    IMGUI_API void          TableGcCompactTransientBuffers(ImGuiTable* table)

    /** Compact and remove unused settings data (currently only used by TestEngine) */
    fun tableGcCompactSettings() {
        g.settingsTables.removeIf { it.id == 0 }
//        var requiredMemory = 0;
//        for (settings in g.settingsTables)
//            if (settings.id != 0)
//                requiredMemory += tableSettingsCalcChunkSize(settings.columnsCount);
//        if (requiredMemory == g.settingsTables.Buf.Size)
//            return;
//        ImChunkStream<ImGuiTableSettings> new_chunk_stream;
//        new_chunk_stream.Buf.reserve(requiredMemory);
//        for (ImGuiTableSettings* settings = g.SettingsTables.begin(); settings != NULL; settings = g.SettingsTables.next_chunk(settings))
//        if (settings->ID != 0)
//        memcpy(new_chunk_stream.alloc_chunk(TableSettingsCalcChunkSize(settings->ColumnsCount)), settings, TableSettingsCalcChunkSize(settings->ColumnsCount));
//        g.SettingsTables.swap(new_chunk_stream);
    }
}