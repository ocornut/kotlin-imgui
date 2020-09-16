package imgui.internal.api

import gli_.has
import gli_.hasnt
import glm_.*
import glm_.vec2.Vec2
import imgui.*
import imgui.ImGui.beginChildEx
import imgui.ImGui.beginPopup
import imgui.ImGui.buttonBehavior
import imgui.ImGui.calcItemSize
import imgui.ImGui.clearActiveID
import imgui.ImGui.contentRegionAvail
import imgui.ImGui.endPopup
import imgui.ImGui.getColorU32
import imgui.ImGui.io
import imgui.ImGui.isClippedEx
import imgui.ImGui.isMouseDoubleClicked
import imgui.ImGui.itemSize
import imgui.ImGui.keepAliveID
import imgui.ImGui.markIniSettingsDirty
import imgui.ImGui.menuItem
import imgui.ImGui.mouseCursor
import imgui.ImGui.popClipRect
import imgui.ImGui.popItemFlag
import imgui.ImGui.pushClipRect
import imgui.ImGui.pushID
import imgui.ImGui.pushItemFlag
import imgui.ImGui.separator
import imgui.ImGui.setNextWindowContentSize
import imgui.ImGui.style
import imgui.ImGui.tableFixFlags
import imgui.api.g
import imgui.api.tables.Companion.TABLE_RESIZE_SEPARATOR_FEEDBACK_TIMER
import imgui.api.tables.Companion.TABLE_RESIZE_SEPARATOR_HALF_THICKNESS
import imgui.api.tables.Companion.tableFixColumnFlags
import imgui.api.tables.Companion.tableGetMinColumnWidth
import imgui.classes.Context
import imgui.font.FontAtlas.BitArray
import imgui.internal.*
import imgui.internal.classes.*
import kool.BYTES
import kotlin.math.max
import imgui.TableColumnFlag as Tcf
import imgui.TableFlag as Tf
import imgui.TableRowFlag as Trf

// [Internal]
internal interface table {

    fun findTableByID(id: ID): Table? = g.tables.getByKey(id)

    fun beginTableEx(
            name: String, id: ID, columnsCount: Int, flags_: TableFlags = Tf.None.i,
            outerSize: Vec2 = Vec2(), innerWidth: Float = 0f
    ): Boolean {

        var flags = flags_

        val outerWindow = ImGui.currentWindow
        if (outerWindow.skipItems) // Consistent with other tables + beneficial side effect that assert on miscalling EndTable() will be more visible.
            return false

        // Sanity checks
        assert(columnsCount in 1..TABLE_MAX_COLUMNS) { "Only 1..64 columns allowed!" }
        if (flags has Tf.ScrollX)
            assert(innerWidth >= 0f)

        val useChildWindow = flags has (Tf.ScrollX or Tf.ScrollY)
        val availSize = contentRegionAvail
        val actualOuterSize = calcItemSize(outerSize, availSize.x max 1f, if (useChildWindow) availSize.y max 1f else 0f)
        val outerRect = Rect(outerWindow.dc.cursorPos, outerWindow.dc.cursorPos + actualOuterSize)

        // If an outer size is specified ahead we will be able to early out when not visible. Exact clipping rules may evolve.
        if (useChildWindow && isClippedEx(outerRect, 0, false)) {
            itemSize(outerRect)
            return false
        }

        flags = tableFixFlags(flags)
        if (outerWindow.flags has WindowFlag.NoSavedSettings)
            flags = flags or Tf.NoSavedSettings

        // Acquire storage for the table
        val table = g.tables.getOrAddByKey(id)
        val tableLastFlags = table.flags
        val instanceNo = if (table.lastFrameActive != g.frameCount) 0 else table.instanceCurrent + 1
        val instanceId = id + instanceNo
        if (instanceNo > 0)
            assert(table.columnsCount == columnsCount) { "BeginTable(): Cannot change columns count mid-frame while preserving same ID" }

        // Initialize
        table.id = id
        table.flags = flags
        table.instanceCurrent = instanceNo
        table.lastFrameActive = g.frameCount
        table.outerWindow = outerWindow
        table.innerWindow = outerWindow
        table.columnsCount = columnsCount
        table.columnsNames.clear()
        table.isInitializing = false
        table.isLayoutLocked = false
        table.innerWidth = innerWidth
        table.outerRect put outerRect
        table.workRect put outerRect

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

            if (overrideContentSize.x != Float.MAX_VALUE || overrideContentSize.y != Float.MAX_VALUE) // TODO glm
                setNextWindowContentSize(Vec2(if (overrideContentSize.x != Float.MAX_VALUE) overrideContentSize.x else 0f, if (overrideContentSize.y != Float.MAX_VALUE) overrideContentSize.y else 0f))

            // Create scrolling region (without border = zero window padding)
            val childFlags = if (flags has Tf.ScrollX) WindowFlag.HorizontalScrollbar else WindowFlag.None
            beginChildEx(name, instanceId, table.outerRect.size, false, childFlags.i)
            table.innerWindow = g.currentWindow
            table.workRect put table.innerWindow!!.workRect
            table.outerRect put table.innerWindow!!.rect()
        } else // WorkRect.Max will grow as we append contents.
            pushID(instanceId)

        // Backup a copy of host window members we will modify
        val innerWindow = table.innerWindow!!
        table.hostIndentX = innerWindow.dc.indent
        table.hostClipRect put innerWindow.clipRect
        table.hostSkipItems = innerWindow.skipItems
        table.hostWorkRect put innerWindow.workRect
        table.hostCursorMaxPos put innerWindow.dc.cursorMaxPos

        // Borders
        // - None               ........Content..... Pad .....Content........
        // - VOuter             | Pad ..Content..... Pad .....Content.. Pad |       // FIXME-TABLE: Not handled properly
        // - VInner             ........Content.. Pad | Pad ..Content........       // FIXME-TABLE: Not handled properly
        // - VOuter+VInner      | Pad ..Content.. Pad | Pad ..Content.. Pad |

        val hasCellPaddingX = flags has Tf.BordersVOuter
        table.cellPaddingX1 = if (hasCellPaddingX) style.cellPadding.x + 1f else 0f
        table.cellPaddingX2 = if (hasCellPaddingX) style.cellPadding.x else 0f
        table.cellPaddingY = style.cellPadding.y
        table.cellSpacingX = if (hasCellPaddingX) 0f else style.cellPadding.x

        table.currentColumn = -1
        table.currentRow = -1
        table.rowBgColorCounter = 0
        table.lastRowFlags = Trf.None.i
        table.innerClipRect put if (innerWindow === outerWindow) table.workRect else innerWindow.clipRect
        table.innerClipRect clipWith table.workRect     // We need this to honor inner_width
        table.innerClipRect clipWith table.hostClipRect
        table.innerClipRect.max.y = if (flags has Tf.NoHostExtendY) table.workRect.max.y else innerWindow.clipRect.max.y
        table.backgroundClipRect put table.innerClipRect
        table.rowPosY1 = table.workRect.min.y // This is needed somehow
        table.rowPosY2 = table.workRect.min.y // This is needed somehow
        table.rowTextBaseline = 0f // This will be cleared again by TableBeginRow()
        table.freezeRowsRequest = (flags and Tf.ScrollFreezeRowsMask_.i) shr Tf.ScrollFreezeRowsShift_.i
        table.freezeRowsCount = if (innerWindow.scroll.y != 0f) table.freezeRowsRequest else 0
        table.freezeColumnsRequest = (flags and Tf.ScrollFreezeColumnsMask_.i) shr Tf.ScrollFreezeColumnsShift_.i
        table.freezeColumnsCount = if (innerWindow.scroll.x != 0f) table.freezeColumnsRequest else 0
        table.isFreezeRowsPassed = table.freezeRowsCount == 0
        table.declColumnsCount = 0
        table.hoveredColumnBody = -1
        table.hoveredColumnBorder = -1
        table.rightMostVisibleColumn = -1

        // Using opaque colors facilitate overlapping elements of the grid
        table.borderColorStrong = Col.TableBorderStrong.u32
        table.borderColorLight = Col.TableBorderLight.u32
        table.borderX1 = table.innerClipRect.min.x// +((table->Flags & ImGuiTableFlags_BordersOuter) ? 0.0f : -1.0f);
        table.borderX2 = table.innerClipRect.max.x// +((table->Flags & ImGuiTableFlags_BordersOuter) ? 0.0f : +1.0f);

        // Make table current
        g.currentTableStack += PtrOrIndex(g.tables.getIndex(table))
        g.currentTable = table
        outerWindow.dc.currentTable = table
        if (tableLastFlags has Tf.Reorderable && flags hasnt Tf.Reorderable)
            table.isResetDisplayOrderRequest = true

        // Setup default columns state. Clear data if columns count changed
        val storedSize = table.columns.size
//        if (storedSize != 0 && storedSize != columnsCount) {
//            table.rawData.clear()
//        }
        if (table.columns.isEmpty()) {
            // Allocate single buffer for our arrays
//            val spanAllocator = SpanAllocator(2)
            table.displayOrderToIndex = ByteArray(columnsCount)
            table.columns = Array(columnsCount) {
                table.displayOrderToIndex[it] = it.toByte()
                TableColumn().apply { displayOrder = it }
            }
//            spanAllocator.reserveBytes(0, columnsCount * sizeof(ImGuiTableColumn))
//            span_allocator.ReserveBytes(1, columns_count * sizeof(ImS8))
//            table->RawData.resize(span_allocator.GetArenaSizeInBytes());
//            span_allocator.SetArenaBasePtr(table->RawData.Data)
//            spanAllocator[0] = table.columns
//            spanAllocator[1] = table.displayOrderToIndex
//            span_allocator.GetSpan(0, &table->Columns)
//            span_allocator.GetSpan(1, &table->DisplayOrderToIndex)
            table.isInitializing = true
            table.isSettingsRequestLoad = true
            table.isSortSpecsDirty = true
        }

        // Load settings
        if (table.isSettingsRequestLoad)
            tableLoadSettings(table)

        // Disable output until user calls TableNextRow() or TableNextCell() leading to the TableUpdateLayout() call..
        // This is not strictly necessary but will reduce cases were "out of table" output will be misleading to the user.
        // Because we cannot safely assert in EndTable() when no rows have been created, this seems like our best option.
        innerWindow.skipItems = true

        // Update/lock which columns will be Visible for the frame
        tableBeginUpdateColumns(table)

        return true
    }

    fun tableBeginUpdateColumns(table: Table) {

        // Handle resizing request
        // (We process this at the first TableBegin of the frame)
        // FIXME-TABLE: Preserve contents width _while resizing down_ until releasing.
        // FIXME-TABLE: Contains columns if our work area doesn't allow for scrolling.
        if (table.instanceCurrent == 0) {
            if (table.resizedColumn != -1 && table.resizedColumnNextWidth != Float.MAX_VALUE)
                tableSetColumnWidth(table, table.columns[table.resizedColumn]!!, table.resizedColumnNextWidth)
            table.lastResizedColumn = table.resizedColumn
            table.resizedColumnNextWidth = Float.MAX_VALUE
            table.resizedColumn = -1
        }

        // Handle reordering request
        // Note: we don't clear ReorderColumn after handling the request.
        if (table.instanceCurrent == 0) {
            if (table.heldHeaderColumn == -1 && table.reorderColumn != -1)
                table.reorderColumn = -1
            table.heldHeaderColumn = -1
            if (table.reorderColumn != -1 && table.reorderColumnDir != 0) {
                // We need to handle reordering across hidden columns.
                // In the configuration below, moving C to the right of E will lead to:
                //    ... C [D] E  --->  ... [D] E  C   (Column name/index)
                //    ... 2  3  4        ...  2  3  4   (Display order)
                val reorderDir = table.reorderColumnDir
                assert(reorderDir == -1 || reorderDir == +1)
                assert(table.flags has Tf.Reorderable)
                val srcColumn = table.columns[table.reorderColumn]!!
                val dstColumn = table.columns[if (reorderDir == -1) srcColumn.prevVisibleColumn else srcColumn.nextVisibleColumn]
//                IM_UNUSED(dstColumn)
                val srcOrder = srcColumn.displayOrder
                val dstOrder = dstColumn!!.displayOrder
                srcColumn.displayOrder = dstOrder
                var orderN = srcOrder + reorderDir
                while (orderN != dstOrder + reorderDir) {
                    table.columns[table.displayOrderToIndex[orderN].i]!!.displayOrder -= reorderDir
                    orderN += reorderDir
                }
                assert(dstColumn.displayOrder == dstOrder - reorderDir)

                // Display order is stored in both columns->IndexDisplayOrder and table->DisplayOrder[],
                // rebuild the later from the former.
                for (columnN in 0 until table.columnsCount)
                    table.displayOrderToIndex[table.columns[columnN]!!.displayOrder] = columnN.b
                table.reorderColumnDir = 0
                table.isSettingsDirty = true
            }
        }

        // Handle display order reset request
        if (table.isResetDisplayOrderRequest) {
            for (n in 0 until table.columnsCount) {
                table.displayOrderToIndex[n] = n.b
                table.columns[n]!!.displayOrder = n
            }
            table.isResetDisplayOrderRequest = false
            table.isSettingsDirty = true
        }

        // Setup and lock Visible state and order
        table.columnsVisibleCount = 0
        table.isDefaultDisplayOrder = true
        var lastVisibleColumn: TableColumn? = null
        var wantColumnAutoFit = false
        for (orderN in 0 until table.columnsCount) {
            val columnN = table.displayOrderToIndex[orderN].i
            if (columnN != orderN)
                table.isDefaultDisplayOrder = false
            val column = table.columns[columnN]!!
            column.nameOffset = -1
            if (table.flags hasnt Tf.Hideable || column.flags has Tcf.NoHide)
                column.isVisibleNextFrame = true
            if (column.isVisible != column.isVisibleNextFrame) {
                column.isVisible = column.isVisibleNextFrame
                table.isSettingsDirty = true
                if (!column.isVisible && column.sortOrder != -1)
                    table.isSortSpecsDirty = true
            }
            if (column.sortOrder > 0 && table.flags hasnt Tf.MultiSortable)
                table.isSortSpecsDirty = true
            if (column.autoFitQueue != 0x00)
                wantColumnAutoFit = true

            val indexMask = 1L shl columnN
            val displayOrderMask = 1L shl column.displayOrder
            if (column.isVisible) {
                column.prevVisibleColumn = -1
                column.nextVisibleColumn = -1
                lastVisibleColumn?.let {
                    it.nextVisibleColumn = columnN
                    column.prevVisibleColumn = table.columns.indexOf(it)
                }
                column.indexWithinVisibleSet = table.columnsVisibleCount
                table.columnsVisibleCount++
                table.visibleMaskByIndex = table.visibleMaskByIndex or indexMask
                table.visibleMaskByDisplayOrder = table.visibleMaskByDisplayOrder or displayOrderMask
                lastVisibleColumn = column
            } else {
                column.indexWithinVisibleSet = -1
                table.visibleMaskByIndex = table.visibleMaskByIndex wo indexMask
                table.visibleMaskByDisplayOrder = table.visibleMaskByDisplayOrder wo displayOrderMask
            }
            assert(column.indexWithinVisibleSet <= column.displayOrder)
        }
        table.visibleUnclippedMaskByIndex = table.visibleMaskByIndex // Columns will be masked out by TableUpdateLayout() when Clipped
        table.rightMostVisibleColumn = lastVisibleColumn?.let { table.columns.indexOf(it) } ?: -1

        // Disable child window clipping while fitting columns. This is not strictly necessary but makes it possible to avoid
        // the column fitting to wait until the first visible frame of the child container (may or not be a good thing).
        if (wantColumnAutoFit && table.outerWindow !== table.innerWindow)
            table.innerWindow!!.skipItems = false
    }

    fun tableUpdateDrawChannels(table: Table) {
        // Allocate draw channels.
        // - We allocate them following storage order instead of display order so reordering columns won't needlessly
        //   increase overall dormant memory cost.
        // - We isolate headers draw commands in their own channels instead of just altering clip rects.
        //   This is in order to facilitate merging of draw commands.
        // - After crossing FreezeRowsCount, all columns see their current draw channel changed to a second set of channels.
        // - We only use the dummy draw channel so we can push a null clipping rectangle into it without affecting other
        //   channels, while simplifying per-row/per-cell overhead. It will be empty and discarded when merged.
        // Draw channel allocation (before merging):
        // - NoClip                       --> 1+1 channels: background + foreground (same clip rect == 1 draw call)
        // - Clip                         --> 1+N channels
        // - FreezeRows || FreezeColumns  --> 1+N*2 (unless scrolling value is zero)
        // - FreezeRows && FreezeColunns  --> 2+N*2 (unless scrolling value is zero)
        val freezeRowMultiplier = if (table.freezeRowsCount > 0) 2 else 1
        val channelsForRow = if (table.flags has Tf.NoClipX) 1 else table.columnsVisibleCount
        val channelsForBackground = 1
        val channelsForDummy = (table.columnsVisibleCount < table.columnsCount || table.visibleUnclippedMaskByIndex != table.visibleMaskByIndex).i
        val channelsTotal = channelsForBackground + (channelsForRow * freezeRowMultiplier) + channelsForDummy
        table.drawSplitter.split(table.innerWindow!!.drawList, channelsTotal)
        table.dummyDrawChannel = if (channelsForDummy != 0) channelsTotal - 1 else -1

        var drawChannelCurrent = 1
        for (columnN in 0 until table.columnsCount) {
            val column = table.columns[columnN]!!
            if (!column.isClipped) {
                column.drawChannelRowsBeforeFreeze = drawChannelCurrent
                column.drawChannelRowsAfterFreeze = drawChannelCurrent + if (table.freezeRowsCount > 0) channelsForRow else 0
                if (table.flags hasnt Tf.NoClipX)
                    drawChannelCurrent++
            } else {
                column.drawChannelRowsBeforeFreeze = table.dummyDrawChannel
                column.drawChannelRowsAfterFreeze = table.dummyDrawChannel
            }
            column.drawChannelCurrent = column.drawChannelRowsBeforeFreeze
        }
    }

    /** Layout columns for the frame
     *  Runs on the first call to TableNextRow(), to give a chance for TableSetupColumn() to be called first.
     *  FIXME-TABLE: Our width (and therefore our WorkRect) will be minimal in the first frame for WidthAlwaysAutoResize
     *  columns, increase feedback side-effect with widgets relying on WorkRect.Max.x. Maybe provide a default distribution
     *  for WidthAlwaysAutoResize columns? */
    fun tableUpdateLayout(table: Table) {

        assert(!table.isLayoutLocked)

        // Compute offset, clip rect for the frame
        // (can't make auto padding larger than what WorkRect knows about so right-alignment matches)
        val workRect = Rect(table.workRect)
        val paddingAutoX = table.cellPaddingX2
        val spacingAutoX = table.cellSpacingX * (1f + 2f) // CellSpacingX is >0.0f when there's no vertical border, in which case we add two extra CellSpacingX to make auto-fit look nice instead of cramped. We may want to expose this somehow.
        val minColumnWidth = tableGetMinColumnWidth

        var countFixed = 0
        var sumWeightsStretched = 0f // Sum of all weights for weighted columns.
        var sumWidthFixedRequests = 0f // Sum of all width for fixed and auto-resize columns, excluding width contributed by Stretch columns.
        table.leftMostStretchedColumnDisplayOrder = -1
        table.columnsAutoFitWidth = 0f
        for (orderN in 0 until table.columnsCount) {
            if (table.visibleMaskByDisplayOrder hasnt (1L shl orderN))
                continue
            val columnN = table.displayOrderToIndex[orderN].i
            val column = table.columns[columnN]!!

            // Adjust flags: default width mode + weighted columns are not allowed when auto extending
            // FIXME-TABLE: Clarify why we need to do this again here and not just in TableSetupColumn()
            column.flags = tableFixColumnFlags(table, column.flagsIn)
            if (column.flags hasnt Tcf.IndentMask_)
                column.flags = column.flags or if (columnN == 0) Tcf.IndentEnable else Tcf.IndentDisable

            // We have a unusual edge case where if the user doesn't call TableGetSortSpecs() but has sorting enabled
            // or varying sorting flags, we still want the sorting arrows to honor those flags.
            if (table.flags has Tf.Sortable)
                tableFixColumnSortDirection(column)

            // Calculate "ideal" column width for nothing to be clipped.
            // Combine width from regular rows + width from headers unless requested not to.
            val columnContentWidthRows = max(column.contentWidthRowsFrozen, column.contentWidthRowsUnfrozen).f
            val columnContentWidthHeaders = column.contentWidthHeadersIdeal.f
            var columnWidthIdeal = columnContentWidthRows
            if (table.flags hasnt Tf.NoHeadersWidth && column.flags hasnt Tcf.NoHeaderWidth)
                columnWidthIdeal = columnWidthIdeal max columnContentWidthHeaders
            columnWidthIdeal = (columnWidthIdeal + paddingAutoX) max minColumnWidth
            table.columnsAutoFitWidth += columnWidthIdeal

            if (column.flags has (Tcf.WidthAlwaysAutoResize or Tcf.WidthFixed)) {
                // Latch initial size for fixed columns
                countFixed += 1
                val autoFix = column.autoFitQueue != 0x00 || column.flags has Tcf.WidthAlwaysAutoResize
                if (autoFix) {
                    column.widthRequest = columnWidthIdeal

                    // FIXME-TABLE: Increase minimum size during init frame to avoid biasing auto-fitting widgets
                    // (e.g. TextWrapped) too much. Otherwise what tends to happen is that TextWrapped would output a very
                    // large height (= first frame scrollbar display very off + clipper would skip lots of items).
                    // This is merely making the side-effect less extreme, but doesn't properly fixes it.
                    if (column.autoFitQueue > 0x01 && table.isInitializing)
                        column.widthRequest = column.widthRequest max (minColumnWidth * 4f)
                }
                sumWidthFixedRequests += column.widthRequest
            } else {
                assert(column.flags has Tcf.WidthStretch)
                val initSize = column.widthStretchWeight < 0f
                if (initSize)
                    column.widthStretchWeight = 1f
                sumWeightsStretched += column.widthStretchWeight
                if (table.leftMostStretchedColumnDisplayOrder == -1)
                    table.leftMostStretchedColumnDisplayOrder = column.displayOrder
            }
        }

        // CellSpacingX is >0.0f when there's no vertical border, in which case we add two extra CellSpacingX to make auto-fit look nice instead of cramped.
        // We may want to expose this somehow.
        table.columnsAutoFitWidth += spacingAutoX * (table.columnsVisibleCount - 1)

        // Layout
        // Remove -1.0f to cancel out the +1.0f we are doing in EndTable() to make last column line visible
        val widthSpacings = table.cellSpacingX * (table.columnsVisibleCount - 1)
        val widthAvail = when {
            table.flags has Tf.ScrollX && table.innerWidth == 0f -> table.innerClipRect.width
            else -> workRect.width
        } - widthSpacings - 1f
        val widthAvailForStretchedColumns = widthAvail - sumWidthFixedRequests
        var widthRemainingForStretchedColumns = widthAvailForStretchedColumns

        // Apply final width based on requested widths
        // Mark some columns as not resizable
        var countResizable = 0
        table.columnsTotalWidth = widthSpacings
        for (orderN in 0 until table.columnsCount) {
            if (table.visibleMaskByDisplayOrder hasnt (1L shl orderN))
                continue
            val column = table.columns[table.displayOrderToIndex[orderN].i]!!

            // Allocate width for stretched/weighted columns
            if (column.flags has Tcf.WidthStretch) {
                // WidthStretchWeight gets converted into WidthRequest
                val weightRatio = column.widthStretchWeight / sumWeightsStretched
                column.widthRequest = floor(max(widthAvailForStretchedColumns * weightRatio, minColumnWidth) + 0.01f)
                widthRemainingForStretchedColumns -= column.widthRequest

                // [Resize Rule 2] Resizing from right-side of a weighted column preceding a fixed column
                // needs to forward resizing to left-side of fixed column. We also need to copy the NoResize flag..
                if (column.nextVisibleColumn != -1)
                    table.columns[column.nextVisibleColumn]?.let { nextColumn ->
                        if (nextColumn.flags has Tcf.WidthFixed)
                            column.flags = column.flags or (nextColumn.flags and Tcf.NoDirectResize_)
                    }
            }

            // [Resize Rule 1] The right-most visible column is not resizable if there is at least one Stretch column
            // (see comments in TableResizeColumn())
            if (column.nextVisibleColumn == -1 && table.leftMostStretchedColumnDisplayOrder != -1)
                column.flags = column.flags or Tcf.NoDirectResize_

            if (column.flags hasnt Tcf.NoResize)
                countResizable++

            // Assign final width, record width in case we will need to shrink
            column.widthGiven = floor(column.widthRequest max minColumnWidth)
            table.columnsTotalWidth += column.widthGiven
        }

//        #if 0
//        const float width_excess = table->ColumnsTotalWidth-work_rect.GetWidth()
//        if ((table->Flags & ImGuiTableFlags_SizingPolicyStretchX) && width_excess > 0.0f)
//        {
//            // Shrink widths when the total does not fit
//            // FIXME-TABLE: This is working but confuses/conflicts with manual resizing.
//            // FIXME-TABLE: Policy to shrink down below below ideal/requested width if there's no room?
//            g.ShrinkWidthBuffer.resize(table->ColumnsVisibleCount)
//            for (int order_n = 0, visible_n = 0; order_n < table->ColumnsCount; order_n++)
//            {
//                if (!(table->ActiveMaskByDisplayOrder & ((ImU64)1 << order_n)))
//                continue
//                const int column_n = table->DisplayOrder[order_n]
//                g.ShrinkWidthBuffer[visible_n].Index = column_n
//                g.ShrinkWidthBuffer[visible_n].Width = table->Columns[column_n].WidthGiven
//                visible_n++
//            }
//            ShrinkWidths(g.ShrinkWidthBuffer.Data, g.ShrinkWidthBuffer.Size, width_excess)
//            for (int n = 0; n < g.ShrinkWidthBuffer.Size; n++)
//            table->Columns[g.ShrinkWidthBuffer.Data[n].Index].WidthGiven = ImMax(g.ShrinkWidthBuffer.Data[n].Width, min_column_size)
//            // FIXME: Need to alter table->ColumnsTotalWidth
//        }
//        else
//        #endif

        // Redistribute remainder width due to rounding (remainder width is < 1.0f * number of Stretch column).
        // Using right-to-left distribution (more likely to match resizing cursor), could be adjusted depending
        // on where the mouse cursor is and/or relative weights.
        // FIXME-TABLE: May be simpler to store floating width and floor final positions only
        // FIXME-TABLE: Make it optional? User might prefer to preserve pixel perfect same size?
        if (widthRemainingForStretchedColumns >= 1f) {
            var orderN = table.columnsCount // - 1 [JVM] trick to change orderN in while statement
            while (sumWeightsStretched > 0f && widthRemainingForStretchedColumns >= 1f && --orderN >= 0) {
                if (table.visibleMaskByDisplayOrder hasnt (1L shl orderN))
                    continue
                val column = table.columns[table.displayOrderToIndex[orderN].i]!!
                if (column.flags hasnt Tcf.WidthStretch)
                    continue
                column.widthRequest += 1f
                column.widthGiven += 1f
                widthRemainingForStretchedColumns -= 1f
            }
        }

        // Setup final position, offset and clipping rectangles
        var visibleN = 0
        var offsetX = if (table.freezeColumnsCount > 0) table.outerRect.min.x else workRect.min.x
        val hostClipRect = Rect(table.innerClipRect)
        for (orderN in 0 until table.columnsCount) {
            val columnN = table.displayOrderToIndex[orderN].i
            val column = table.columns[columnN]!!

            if (table.freezeColumnsCount > 0 && table.freezeColumnsCount == visibleN)
                offsetX += workRect.min.x - table.outerRect.min.x

            if (table.visibleMaskByDisplayOrder hasnt (1L shl orderN)) {
                // Hidden column: clear a few fields and we are done with it for the remainder of the function.
                // We set a zero-width clip rect but set Min.y/Max.y properly to not interfere with the clipper.
                column.minX = offsetX
                column.maxX = offsetX
                column.startXRows = offsetX
                column.startXHeaders = offsetX
                column.widthGiven = 0f
                column.clipRect.min.x = offsetX
                column.clipRect.min.y = workRect.min.y
                column.clipRect.max.x = offsetX
                column.clipRect.max.y = Float.MAX_VALUE
                column.clipRect clipWithFull hostClipRect
                column.isClipped = true
                column.skipItems = true
                continue
            }

            var maxX = Float.MAX_VALUE
            if (table.flags has Tf.ScrollX) {
                // Frozen columns can't reach beyond visible width else scrolling will naturally break.
                if (orderN < table.freezeColumnsRequest)
                    maxX = table.innerClipRect.max.x - (table.freezeColumnsRequest - orderN) * minColumnWidth
            } else {
                // If horizontal scrolling if disabled, we apply a final lossless shrinking of columns in order to make
                // sure they are all visible. Because of this we also know that all of the columns will always fit in
                // table->WorkRect and therefore in table->InnerRect (because ScrollX is off)
                if (table.flags hasnt Tf.NoKeepColumnsVisible)
                    maxX = table.workRect.max.x - (table.columnsVisibleCount - (column.indexWithinVisibleSet + 1)) * minColumnWidth
            }
            if (offsetX + column.widthGiven > maxX)
                column.widthGiven = max(maxX - offsetX, minColumnWidth)

            column.minX = offsetX
            column.maxX = column.minX + column.widthGiven

            // A one pixel padding on the right side makes clipping more noticeable and contents look less cramped.
            column.clipRect.put(column.minX, workRect.min.y, column.maxX/* -1f */, Float.MAX_VALUE)
            column.clipRect clipWithFull hostClipRect

            column.isClipped = column.clipRect.max.x <= column.clipRect.min.x && column.autoFitQueue hasnt 1 && column.cannotSkipItemsQueue hasnt 1
            column.skipItems = column.isClipped || table.hostSkipItems
            if (column.isClipped)
            // Columns with the _WidthAlwaysAutoResize sizing policy will never be updated then.
                table.visibleUnclippedMaskByIndex = table.visibleUnclippedMaskByIndex wo (1L shl columnN)
            else {
                // Starting cursor position
                column.startXRows = column.minX + table.cellPaddingX1
                column.startXHeaders = column.startXRows

                // Alignment
                // FIXME-TABLE: This align based on the whole column width, not per-cell, and therefore isn't useful in
                // many cases (to be able to honor this we might be able to store a log of cells width, per row, for
                // visible rows, but nav/programmatic scroll would have visible artifacts.)
                //if (column->Flags & ImGuiTableColumnFlags_AlignRight)
                //    column->StartXRows = ImMax(column->StartXRows, column->MaxX - column->ContentWidthRowsUnfrozen);
                //else if (column->Flags & ImGuiTableColumnFlags_AlignCenter)
                //    column->StartXRows = ImLerp(column->StartXRows, ImMax(column->StartXRows, column->MaxX - column->ContentWidthRowsUnfrozen), 0.5f);

                // Reset content width variables
                val initialMaxPosX = column.minX + table.cellPaddingX1
                column.contentMaxPosRowsFrozen = initialMaxPosX
                column.contentMaxPosRowsUnfrozen = initialMaxPosX
                column.contentMaxPosHeadersUsed = initialMaxPosX
                column.contentMaxPosHeadersIdeal = initialMaxPosX
            }

            // Don't decrement auto-fit counters until container window got a chance to submit its items
            if (!table.hostSkipItems) {
                column.autoFitQueue = column.autoFitQueue shr 1
                column.cannotSkipItemsQueue = column.cannotSkipItemsQueue shr 1
            }

            if (visibleN < table.freezeColumnsCount)
                hostClipRect.min.x = hostClipRect.min.x max (column.maxX + 2f)

            offsetX += column.widthGiven + table.cellSpacingX
            visibleN++
        }

        // Clear Resizable flag if none of our column are actually resizable (either via an explicit _NoResize flag,
        // either because of using _WidthAlwaysAutoResize/_WidthStretch).
        if (countResizable == 0 && table.flags has Tf.Resizable)
            table.flags = table.flags wo Tf.Resizable

        // Allocate draw channels
        tableUpdateDrawChannels(table)

        // Borders
        if (table.flags has Tf.Resizable)
            tableUpdateBorders(table)

        // Reset fields after we used them in TableSetupResize()
        table.lastFirstRowHeight = 0f
        table.isLayoutLocked = true
        table.isUsingHeaders = false

        // Context menu
        if (table.isContextPopupOpen && table.instanceCurrent == table.instanceInteracted)
            if (beginPopup("##TableContextMenu")) {
                tableDrawContextMenu(table, table.contextPopupColumn)
                endPopup()
            } else
                table.isContextPopupOpen = false

        // Initial state
        val innerWindow = table.innerWindow!!
        if (table.flags has Tf.NoClipX)
            table.drawSplitter.setCurrentChannel(innerWindow.drawList, 1)
        else
            innerWindow.drawList.pushClipRect(innerWindow.clipRect.min, innerWindow.clipRect.max, false)
    }

    /** Process interaction on resizing borders. Actual size change will be applied in EndTable()
     *  - Set table->HoveredColumnBorder with a short delay/timer to reduce feedback noise
     *  - Submit ahead of table contents and header, use ImGuiButtonFlags_AllowItemOverlap to prioritize widgets
     *    overlapping the same area. */
    fun tableUpdateBorders(table: Table) {

        assert(table.flags has Tf.Resizable)

        // At this point OuterRect height may be zero or under actual final height, so we rely on temporal coherency and
        // use the final height from last frame. Because this is only affecting _interaction_ with columns, it is not
        // really problematic (whereas the actual visual will be displayed in EndTable() and using the current frame height).
        // Actual columns highlight/render will be performed in EndTable() and not be affected.
        val bordersFullHeight = !table.isUsingHeaders || table.flags has Tf.BordersVFullHeight
        val hitHalfWidth = TABLE_RESIZE_SEPARATOR_HALF_THICKNESS
        val hitY1 = table.outerRect.min.y
        val hitY2Full = table.outerRect.max.y max (hitY1 + table.lastOuterHeight)
        val hitY2 = if (bordersFullHeight) hitY2Full else hitY1 + table.lastFirstRowHeight
        val mouseXHoverBody = if (io.mousePos.y >= hitY1 && io.mousePos.y < hitY2Full) io.mousePos.x else Float.MAX_VALUE

        for (orderN in 0 until table.columnsCount) {

            if (table.visibleMaskByDisplayOrder hasnt (1L shl orderN))
                continue

            val columnN = table.displayOrderToIndex[orderN].i
            val column = table.columns[columnN]!!

            // Detect hovered column:
            // - we perform an unusually low-level check here.. not using IsMouseHoveringRect() to avoid touch padding.
            // - we don't care about the full set of IsItemHovered() feature either.
            if (mouseXHoverBody >= column.minX && mouseXHoverBody < column.maxX)
                table.hoveredColumnBody = columnN

            if (column.flags has (Tcf.NoResize or Tcf.NoDirectResize_))
                continue

            val columnId = tableGetColumnResizeID(table, columnN, table.instanceCurrent)
            val hitRect = Rect(column.maxX - hitHalfWidth, hitY1, column.maxX + hitHalfWidth, hitY2)
            //GetForegroundDrawList()->AddRect(hit_rect.Min, hit_rect.Max, IM_COL32(255, 0, 0, 100));
            keepAliveID(columnId)

            val (pressed, hovered_, held_) = buttonBehavior(hitRect, columnId, ButtonFlag.FlattenChildren or ButtonFlag.AllowItemOverlap or ButtonFlag.PressedOnClick or ButtonFlag.PressedOnDoubleClick)
            var hovered = hovered_
            var held = held_
            if (pressed && isMouseDoubleClicked(MouseButton.Left) && column.flags hasnt Tcf.WidthStretch) {
                // FIXME-TABLE: Double-clicking on column edge could auto-fit weighted column?
                tableSetColumnAutofit(table, columnN)
                clearActiveID()
                held = false
                hovered = false
            }
            if (held) {
                table.resizedColumn = columnN
                table.instanceInteracted = table.instanceCurrent
            }
            if ((hovered && g.hoveredIdTimer > TABLE_RESIZE_SEPARATOR_FEEDBACK_TIMER) || held) {
                table.hoveredColumnBorder = columnN
                mouseCursor = MouseCursor.ResizeEW
            }
        }
    }

    /** Public wrapper */
    fun tableSetColumnWidth(columnN: Int, width: Float) {
        val table = g.currentTable!!
//        assert(table != null)
        assert(!table.isLayoutLocked)
        assert(columnN >= 0 && columnN < table.columnsCount)
        tableSetColumnWidth(table, table.columns[columnN]!!, width)
    }

    /** [Internal] */
    fun tableSetColumnWidth(table: Table, column0: TableColumn, column0Width_: Float) {

        var column0Width = column0Width_

        // Constraints
        val minWidth = tableGetMinColumnWidth
        var maxWidth0 = Float.MAX_VALUE
        if (table.flags hasnt Tf.ScrollX)
            maxWidth0 = (table.workRect.max.x - column0.minX) - (table.columnsVisibleCount - (column0.indexWithinVisibleSet + 1)) * minWidth
        column0Width = clamp(column0Width, minWidth, maxWidth0)

        // Compare both requested and actual given width to avoid overwriting requested width when column is stuck (minimum size, bounded)
        if (column0.widthGiven == column0Width || column0.widthRequest == column0Width)
            return

        val column1 = table.columns.getOrNull(column0.nextVisibleColumn)

        // In this surprisingly not simple because of how we support mixing Fixed and Stretch columns.
        // When forwarding resize from Wn| to Fn+1| we need to be considerate of the _NoResize flag on Fn+1.
        // FIXME-TABLE: Find a way to rewrite all of this so interactions feel more consistent for the user.
        // Scenarios:
        // - F1 F2 F3  resize from F1| or F2|   --> ok: alter ->WidthRequested of Fixed column. Subsequent columns will be offset.
        // - F1 F2 F3  resize from F3|          --> ok: alter ->WidthRequested of Fixed column. If active, ScrollX extent can be altered.
        // - F1 F2 W3  resize from F1| or F2|   --> ok: alter ->WidthRequested of Fixed column. If active, ScrollX extent can be altered, but it doesn't make much sense as the Weighted column will always be minimal size.
        // - F1 F2 W3  resize from W3|          --> ok: no-op (disabled by Resize Rule 1)
        // - W1 W2 W3  resize from W1| or W2|   --> FIXME
        // - W1 W2 W3  resize from W3|          --> ok: no-op (disabled by Resize Rule 1)
        // - W1 F2 F3  resize from F3|          --> ok: no-op (disabled by Resize Rule 1)
        // - W1 F2     resize from F2|          --> ok: no-op (disabled by Resize Rule 1)
        // - W1 W2 F3  resize from W1| or W2|   --> ok
        // - W1 F2 W3  resize from W1| or F2|   --> FIXME
        // - F1 W2 F3  resize from W2|          --> ok
        // - W1 F2 F3  resize from W1|          --> ok: equivalent to resizing |F2. F3 will not move. (forwarded by Resize Rule 2)
        // - W1 F2 F3  resize from F2|          --> FIXME should resize F2, F3 and not have effect on W1 (Stretch columns are _before_ the Fixed column).

        // Rules:
        // - [Resize Rule 1] Can't resize from right of right-most visible column if there is any Stretch column. Implemented in TableUpdateLayout().
        // - [Resize Rule 2] Resizing from right-side of a Stretch column before a fixed column froward sizing to left-side of fixed column.
        // - [Resize Rule 3] If we are are followed by a fixed column and we have a Stretch column before, we need to ensure that our left border won't move.

        if (column0.flags has Tcf.WidthFixed) {
            // [Resize Rule 3] If we are are followed by a fixed column and we have a Stretch column before, we need to ensure
            // that our left border won't move, which we can do by making sure column_a/column_b resizes cancels each others.
            if (column1?.flags?.has(Tcf.WidthFixed) == true)
                if (table.leftMostStretchedColumnDisplayOrder != -1 && table.leftMostStretchedColumnDisplayOrder < column0.displayOrder) {
                    // (old_a + old_b == new_a + new_b) --> (new_a == old_a + old_b - new_b)
                    val column1Width = (column1.widthRequest - (column0Width - column0.widthRequest)) max minWidth
                    column0Width = column0.widthRequest + column1.widthRequest - column1Width
                    column1.widthRequest = column1Width
                }

            // Apply
            //IMGUI_DEBUG_LOG("TableSetColumnWidth(%d, %.1f->%.1f)\n", column_0_idx, column_0->WidthRequested, column_0_width);
            column0.widthRequest = column0Width
        } else if (column0.flags has Tcf.WidthStretch) {
            // [Resize Rule 2]
            if (column1?.flags?.has(Tcf.WidthFixed) == true) {
                val off = column0.widthGiven - column0Width
                val column1Width = column1.widthGiven + off
                column1.widthRequest = minWidth max column1Width
                return
            }

            // (old_a + old_b == new_a + new_b) --> (new_a == old_a + old_b - new_b)
            val column1Width = (column1!!.widthRequest - (column0Width - column0.widthRequest)) max minWidth
            column0Width = column0.widthRequest + column1.widthRequest - column1Width
            column1.widthRequest = column1Width
            column0.widthRequest = column0Width
            tableUpdateColumnsWeightFromWidth(table)
        }
        table.isSettingsDirty = true
    }

    /** FIXME-TABLE: This is a mess, need to redesign how we render borders. */
    fun tableDrawBorders(table: Table) {

        val innerWindow = table.innerWindow!!
        val outerWindow = table.outerWindow!!
        table.drawSplitter.setCurrentChannel(innerWindow.drawList, 0)
        if (innerWindow.hidden || !table.hostClipRect.overlaps(table.innerClipRect))
            return
        val innerDrawlist = innerWindow.drawList
        val outerDrawlist = outerWindow.drawList

        // Draw inner border and resizing feedback
        val drawY1 = table.outerRect.min.y
        var drawY2Base = table.lastFirstRowHeight + if (table.freezeRowsCount >= 1) table.outerRect.min.y else table.workRect.min.y
        val drawY2Full = table.outerRect.max.y
        val borderBaseCol = getColorU32(when {
            !table.isUsingHeaders || table.flags has Tf.BordersVFullHeight -> {
                drawY2Base = drawY2Full
                table.borderColorLight
            }
            else -> table.borderColorStrong
        })

        if (table.flags has Tf.BordersVOuter && table.innerWindow === table.outerWindow)
            innerDrawlist.addLine(Vec2(table.outerRect.min.x, drawY1), Vec2(table.outerRect.min.x, drawY2Base), borderBaseCol, 1f)

        if (table.flags has Tf.BordersVInner)
            for (orderN in 0 until table.columnsCount) {
                if (table.visibleMaskByDisplayOrder hasnt (1L shl orderN))
                    continue

                val columnN = table.displayOrderToIndex[orderN].i
                val column = table.columns[columnN]!!
                val isHovered = table.hoveredColumnBorder == columnN
                val isResized = table.resizedColumn == columnN && table.instanceInteracted == table.instanceCurrent
                val isResizable = column.flags hasnt (Tcf.NoResize or Tcf.NoDirectResize_)
                var drawRightBorder = column.maxX <= table.innerClipRect.max.x || (isResized || isHovered)
                if (column.nextVisibleColumn == -1 && !isResizable)
                    drawRightBorder = false
                if (drawRightBorder && column.maxX > column.clipRect.min.x) { // FIXME-TABLE FIXME-STYLE: Assume BorderSize==1, this is problematic if we want to increase the border size..
                    // Draw in outer window so right-most column won't be clipped
                    // Always draw full height border when:
                    // - not using headers
                    // - user specify ImGuiTableFlags_BordersFullHeight
                    // - being interacted with
                    // - on the delimitation of frozen column scrolling
                    val col = if (isResized) Col.SeparatorActive.u32 else if (isHovered) Col.SeparatorHovered.u32 else borderBaseCol
                    var drawY2 = drawY2Base
                    if (isHovered || isResized || (table.freezeColumnsCount != -1 && table.freezeColumnsCount == orderN + 1))
                        drawY2 = drawY2Full
                    innerDrawlist.addLine(Vec2(column.maxX, drawY1), Vec2(column.maxX, drawY2), col, 1f)
                }
            }

        // Draw outer border
        if (table.flags has Tf.BordersOuter) {
            // Display outer border offset by 1 which is a simple way to display it without adding an extra draw call
            // (Without the offset, in outer_window it would be rendered behind cells, because child windows are above their
            // parent. In inner_window, it won't reach out over scrollbars. Another weird solution would be to display part
            // of it in inner window, and the part that's over scrollbars in the outer window..)
            // Either solution currently won't allow us to use a larger border size: the border would clipped.
            val outerBorder = Rect(table.outerRect)
            val outerCol = table.borderColorStrong
            if (innerWindow != outerWindow)
                outerBorder expand 1f
            when {
                table.flags and Tf.BordersOuter == Tf.BordersOuter.i -> outerDrawlist.addRect(outerBorder.min, outerBorder.max, outerCol)
                table.flags has Tf.BordersVOuter -> {
                    outerDrawlist.addLine(outerBorder.min, Vec2(outerBorder.min.x, outerBorder.max.y), outerCol)
                    outerDrawlist.addLine(Vec2(outerBorder.max.x, outerBorder.min.y), outerBorder.max, outerCol)
                }
                table.flags has Tf.BordersHOuter -> {
                    outerDrawlist.addLine(outerBorder.min, Vec2(outerBorder.max.x, outerBorder.min.y), outerCol)
                    outerDrawlist.addLine(Vec2(outerBorder.min.x, outerBorder.max.y), outerBorder.max, outerCol)
                }
            }
        }
        if (table.flags has Tf.BordersHInner && table.rowPosY2 < table.outerRect.max.y) {
            // Draw bottom-most row border
            val borderY = table.rowPosY2
            if (borderY >= table.backgroundClipRect.min.y && borderY < table.backgroundClipRect.max.y)
                innerDrawlist.addLine(Vec2(table.borderX1, borderY), Vec2(table.borderX2, borderY), table.borderColorLight)
        }
    }

    /** Columns where the contents didn't stray off their local clip rectangle can be merged into a same draw command.
     *  To achieve this we merge their clip rect and make them contiguous in the channel list so they can be merged.
     *  So here we'll reorder the draw cmd which can be merged, by arranging them into a maximum of 4 distinct groups:
     *
     *    1 group:               2 groups:              2 groups:              4 groups:
     *    [ 0. ] no freeze       [ 0. ] row freeze      [ 01 ] col freeze      [ 01 ] row+col freeze
     *    [ .. ]  or no scroll   [ 1. ]  and v-scroll   [ .. ]  and h-scroll   [ 23 ]  and v+h-scroll
     *
     *  Each column itself can use 1 channel (row freeze disabled) or 2 channels (row freeze enabled).
     *  When the contents of a column didn't stray off its limit, we move its channels into the corresponding group
     *  based on its position (within frozen rows/columns groups or not).
     *  At the end of the operation our 1-4 groups will each have a ImDrawCmd using the same ClipRect, and they will be
     *  merged by the DrawSplitter.Merge() call.
     *
     *  Column channels will not be merged into one of the 1-4 groups in the following cases:
     *  - The contents stray off its clipping rectangle (we only compare the MaxX value, not the MinX value).
     *    Direct ImDrawList calls won't be taken into account by default, if you use them make sure the ImGui:: bounds
     *    matches, by e.g. calling SetCursorScreenPos().
     *  - The channel uses more than one draw command itself. We drop all our merging stuff here.. we could do better
     *    but it's going to be rare.
     *
     *  This function is particularly tricky to understand.. take a breath. */
    fun tableDrawMergeChannels(table: Table) {

        val splitter = table.drawSplitter
        val isFrozenV = table.freezeRowsCount > 0
        val isFrozenH = table.freezeColumnsCount > 0

        // Track which groups we are going to attempt to merge, and which channels goes into each group.
        class MergeGroup {
            val clipRect = Rect()
            var channelsCount = 0
            val channelsMask = BitArray(TABLE_MAX_DRAW_CHANNELS)
        }

        var mergeGroupMask = 0x00
        val mergeGroups = Array(4) { MergeGroup() }
        val mergeGroupsAllFitWithinInnerRect = table.flags hasnt Tf.NoHostExtendY

        // 1. Scan channels and take note of those which can be merged
        for (orderN in 0 until table.columnsCount) {
            if (table.visibleMaskByDisplayOrder hasnt (1L shl orderN))
                continue
            val columnN = table.displayOrderToIndex[orderN].i
            val column = table.columns[columnN]!!

            val mergeGroupSubCount = if (isFrozenV) 2 else 1
            for (mergeGroupSubN in 0 until mergeGroupSubCount) {

                val channelNo = if (mergeGroupSubN == 0) column.drawChannelRowsBeforeFreeze else column.drawChannelRowsAfterFreeze

                // Don't attempt to merge if there are multiple draw calls within the column
                val srcChannel = splitter._channels[channelNo]
                if (srcChannel._cmdBuffer.isNotEmpty() && srcChannel._cmdBuffer.last().elemCount == 0)
                    srcChannel._cmdBuffer.pop()
                if (srcChannel._cmdBuffer.size != 1)
                    continue

                // Find out the width of this merge group and check if it will fit in our column.
                val widthContents = when {
                    // No row freeze (same as testing !is_frozen_v)
                    mergeGroupSubCount == 1 -> column.contentWidthRowsUnfrozen max column.contentWidthHeadersUsed
                    // Row freeze: use width before freeze
                    mergeGroupSubN == 0 -> column.contentWidthRowsFrozen max column.contentWidthHeadersUsed
                    // Row freeze: use width after freeze
                    else -> column.contentWidthRowsUnfrozen
                }
                if (widthContents > column.widthGiven && column.flags hasnt Tcf.NoClipX)
                    continue

                val mergeGroupDstN = (if (isFrozenH && columnN < table.freezeColumnsCount) 0 else 2) + if (isFrozenV) mergeGroupSubN else 1
                assert(channelNo < TABLE_MAX_DRAW_CHANNELS)
                val mergeGroup = mergeGroups[mergeGroupDstN]
                if (mergeGroup.channelsCount == 0)
                    mergeGroup.clipRect.put(+Float.MAX_VALUE, +Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
                mergeGroup.channelsMask setBit channelNo
                mergeGroup.channelsCount++
                mergeGroup.clipRect add Rect(srcChannel._cmdBuffer[0].clipRect)
                mergeGroupMask = mergeGroupMask or (1 shl mergeGroupDstN)

                // If we end with a single group and hosted by the outer window, we'll attempt to merge our draw command
                // with the existing outer window command. But we can only do so if our columns all fit within the expected
                // clip rect, otherwise clipping will be incorrect when ScrollX is disabled.
                // FIXME-TABLE FIXME-WORKRECT: We are wasting a merge opportunity on tables without scrolling if column don't fit within host clip rect, solely because of the half-padding difference between window->WorkRect and window->InnerClipRect

                // 2019/10/22: (1) This is breaking table_2_draw_calls but I cannot seem to repro what it is attempting to
                // fix... cf git fce2e8dc "Fixed issue with clipping when outerwindow==innerwindow / support ScrollH without ScrollV."
                // 2019/10/22: (2) Clamping code in TableUpdateLayout() seemingly made this not necessary...
//                #if 0
//                if (column->MinX < table->InnerClipRect.Min.x || column->MaxX > table->InnerClipRect.Max.x)
//                mergeGroupsAllFitWithinInnerRect = false
//                #endif
            }

            // Invalidate current draw channel
            // (we don't clear DrawChannelBeforeRowFreeze/DrawChannelAfterRowFreeze solely to facilitate debugging)
            column.drawChannelCurrent = -1
        }

        // 2. Rewrite channel list in our preferred order
        if (mergeGroupMask != 0) {
            // Use shared temporary storage so the allocation gets amortized
//            g.drawChannelsTempMergeBuffer.resize(splitter->_Count-1)
            for (i in splitter._count - 1 until g.drawChannelsTempMergeBuffer.size) {
//                g.drawChannelsTempMergeBuffer.last().free()
                g.drawChannelsTempMergeBuffer.removeAt(g.drawChannelsTempMergeBuffer.lastIndex)
            }
            for (i in g.drawChannelsTempMergeBuffer.size until splitter._count - 1)
                g.drawChannelsTempMergeBuffer += DrawChannel()

            val dstTmp = g.drawChannelsTempMergeBuffer
            var d = 0
            val remainingMask = BitArray(TABLE_MAX_DRAW_CHANNELS)
            remainingMask.clearBits()
            remainingMask.setBitRange(1, splitter._count - 1) // Background channel 0 not part of the merge (see channel allocation in TableUpdateDrawChannels)
            var remainingCount = splitter._count - 1
            val mayExtendClipRectToHostRect = mergeGroupMask.isPowerOfTwo
            for (mergeGroupN in 0..3) {
                var mergeChannelsCount = mergeGroups[mergeGroupN].channelsCount
                if (mergeChannelsCount != 0) {
                    val mergeGroup = mergeGroups[mergeGroupN]
                    val mergeClipRect = Rect(mergeGroup.clipRect)
                    if (mayExtendClipRectToHostRect) {
                        //GetOverlayDrawList()->AddRect(table->HostClipRect.Min, table->HostClipRect.Max, IM_COL32(255, 0, 0, 200), 0.0f, ~0, 3.0f);
                        //GetOverlayDrawList()->AddRect(table->InnerClipRect.Min, table->InnerClipRect.Max, IM_COL32(0, 255, 0, 200), 0.0f, ~0, 1.0f);
                        //GetOverlayDrawList()->AddRect(merge_clip_rect.Min, merge_clip_rect.Max, IM_COL32(255, 0, 0, 200), 0.0f, ~0, 2.0f);
                        mergeClipRect add if (mergeGroupsAllFitWithinInnerRect) table.hostClipRect else table.innerClipRect
                        //GetOverlayDrawList()->AddRect(merge_clip_rect.Min, merge_clip_rect.Max, IM_COL32(0, 255, 0, 200));
                    }
                    remainingCount -= mergeGroup.channelsCount
                    for (n in remainingMask.storage.indices)
                        remainingMask.storage[n] = remainingMask.storage[n] wo mergeGroup.channelsMask.storage[n]
                    var n = -1 // [JVM] trick
                    while (++n < splitter._count && mergeChannelsCount != 0) {
                        // Copy + overwrite new clip rect
                        if (!mergeGroup.channelsMask.testBit(n))
                            continue
                        mergeGroup.channelsMask clearBit n
                        mergeChannelsCount--

                        val channel = splitter._channels[n]
                        assert(channel._cmdBuffer.size == 1 && Rect(channel._cmdBuffer[0].clipRect) in mergeClipRect)
                        channel._cmdBuffer[0].clipRect put mergeClipRect.toVec4()
                        dstTmp[d++] = channel
                    }
                }
            }

            // Append unmergeable channels that we didn't reorder at the end of the list
            var n = -1 // [JVM] trick
            while (++n < splitter._count && remainingCount != 0) {
                if (!remainingMask.testBit(n))
                    continue
                val channel = splitter._channels[n]
                dstTmp[d++] = channel
                remainingCount--
            }
            assert(dstTmp.size == g.drawChannelsTempMergeBuffer.size)
            for (i in 0 until splitter._count - 1)
                splitter._channels[1 + i] = g.drawChannelsTempMergeBuffer[i]
        }

        // 3. Actually merge (channels using the same clip rect will be contiguous and naturally merged)
        splitter.merge(table.innerWindow!!.drawList)
    }

    /** Output context menu into current window (generally a popup)
     *  FIXME-TABLE: Ideally this should be writable by the user. Full programmatic access to that data? */
    fun tableDrawContextMenu(table: Table, selectedColumnN_: Int) {

        var selectedColumnN = selectedColumnN_

        val window = g.currentWindow!!
        if (window.skipItems)
            return

        var wantSeparator = false
        selectedColumnN = clamp(selectedColumnN, -1, table.columnsCount - 1)

        // Sizing
        if (table.flags has Tf.Resizable) {
            table.columns.getOrNull(selectedColumnN)?.let { selectedColumn ->
                val canResize = selectedColumn.flags hasnt (Tcf.NoResize or Tcf.WidthStretch) && selectedColumn.isVisible
                if (menuItem("Size column to fit", "", false, canResize))
                    tableSetColumnAutofit(table, selectedColumnN)
            }

            if (menuItem("Size all columns to fit", ""))
                for (columnN in 0 until table.columnsCount) {
                    val column = table.columns[columnN]!!
                    if (column.isVisible)
                        tableSetColumnAutofit(table, columnN)
                }
            wantSeparator = true
        }

        // Ordering
        if (table.flags has Tf.Reorderable) {
            if (menuItem("Reset order", "", false, !table.isDefaultDisplayOrder))
                table.isResetDisplayOrderRequest = true
            wantSeparator = true
        }

        // Hiding / Visibility
        if (table.flags has Tf.Hideable) {
            if (wantSeparator)
                separator()
            wantSeparator = false

            pushItemFlag(ItemFlag.SelectableDontClosePopup.i, true)
            for (columnN in 0 until table.columnsCount) {
                val column = table.columns[columnN]!!
                val name = tableGetColumnName(table, columnN) ?: "<Unknown>"

                // Make sure we can't hide the last active column
                var menuItemActive = column.flags hasnt Tcf.NoHide
                if (column.isVisible && table.columnsVisibleCount <= 1)
                    menuItemActive = false
                if (menuItem(name, "", column.isVisible, menuItemActive))
                    column.isVisibleNextFrame = !column.isVisible
            }
            popItemFlag()
        }
    }

    fun tableSortSpecsClickColumn(table: Table, clickedColumn: TableColumn, addToExistingSortOrders_: Boolean) {

        var addToExistingSortOrders = addToExistingSortOrders_

        if (table.flags hasnt Tf.MultiSortable)
            addToExistingSortOrders = false

        var sortOrderMax = 0
        if (addToExistingSortOrders)
            for (columnN in 0 until table.columnsCount)
                sortOrderMax = sortOrderMax max table.columns[columnN]!!.sortOrder

        for (columnN in 0 until table.columnsCount) {
            val column = table.columns[columnN]!!
            if (column === clickedColumn) {
                // Set new sort direction and sort order
                // - If the PreferSortDescending flag is set, we will default to a Descending direction on the first click.
                // - Note that the PreferSortAscending flag is never checked, it is essentially the default and therefore a no-op.
                // - Note that the NoSortAscending/NoSortDescending flags are processed in TableSortSpecsSanitize(), and they may change/revert
                //   the value of SortDirection. We could technically also do it here but it would be unnecessary and duplicate code.
                column.sortDirection = when (column.sortOrder) {
                    -1 -> when {
                        column.flags has Tcf.PreferSortDescending -> SortDirection.Descending
                        else -> SortDirection.Ascending
                    }
                    else -> when (column.sortDirection) {
                        SortDirection.Ascending -> SortDirection.Descending
                        else -> SortDirection.Ascending
                    }
                }
                if (column.sortOrder == -1 || !addToExistingSortOrders)
                    column.sortOrder = if (addToExistingSortOrders) sortOrderMax + 1 else 0
            } else
                if (!addToExistingSortOrders)
                    column.sortOrder = -1
            tableFixColumnSortDirection(column)
        }
        table.isSettingsDirty = true
        table.isSortSpecsDirty = true
    }

    fun tableSortSpecsSanitize(table: Table) {
        // Clear SortOrder from hidden column and verify that there's no gap or duplicate.
        var sortOrderCount = 0
        var sortOrderMask = 0x00L
        for (columnN in 0 until table.columnsCount) {
            val column = table.columns[columnN]!!
            if (column.sortOrder != -1 && !column.isVisible)
                column.sortOrder = -1
            if (column.sortOrder == -1)
                continue
            sortOrderCount++
            sortOrderMask = sortOrderMask or (1L shl column.sortOrder)
            assert(sortOrderCount < Long.BYTES * 8)
        }

        val needFixLinearize = 1L shl sortOrderCount != sortOrderMask + 1
        val needFixSingleSortOrder = sortOrderCount > 1 && table.flags hasnt Tf.MultiSortable
        if (needFixLinearize || needFixSingleSortOrder) {
            var fixedMask = 0x00L
            for (sortN in 0 until sortOrderCount) {
                // Fix: Rewrite sort order fields if needed so they have no gap or duplicate.
                // (e.g. SortOrder 0 disappeared, SortOrder 1..2 exists --> rewrite then as SortOrder 0..1)
                var columnWithSmallestSortOrder = -1
                for (columnN in 0 until table.columnsCount)
                    if (fixedMask and (1L shl columnN) == 0L && table.columns[columnN]!!.sortOrder != -1)
                        if (columnWithSmallestSortOrder == -1 || table.columns[columnN]!!.sortOrder < table.columns[columnWithSmallestSortOrder]!!.sortOrder)
                            columnWithSmallestSortOrder = columnN
                assert(columnWithSmallestSortOrder != -1)
                fixedMask = fixedMask or (1L shl columnWithSmallestSortOrder)
                table.columns[columnWithSmallestSortOrder]!!.sortOrder = sortN

                // Fix: Make sure only one column has a SortOrder if ImGuiTableFlags_MultiSortable is not set.
                if (needFixSingleSortOrder) {
                    for (columnN in 0 until table.columnsCount)
                        if (columnN != columnWithSmallestSortOrder)
                            table.columns[columnN]!!.sortOrder = -1
                    break
                }
            }
        }

        // Fallback default sort order (if no column has the ImGuiTableColumnFlags_DefaultSort flag)
        if (sortOrderCount == 0 && table.isInitializing)
            for (columnN in 0 until table.columnsCount) {
                val column = table.columns[columnN]!!
                if (column.flags hasnt Tcf.NoSort && column.isVisible) {
                    sortOrderCount = 1
                    column.sortOrder = 0
                    break
                }
            }

        table.sortSpecsCount = sortOrderCount
    }

    /** [Internal] */
    fun tableBeginRow(table: Table) {

        val window = table.innerWindow!!
        assert(!table.isInsideRow)

        // New row
        table.currentRow++
        table.currentColumn = -1
        table.rowBgColor = COL32_DISABLE
        table.isInsideRow = true

        // Begin frozen rows
        var nextY1 = table.rowPosY2
        if (table.currentRow == 0 && table.freezeRowsCount > 0) {
            nextY1 = table.outerRect.min.y
            window.dc.cursorPos.y = nextY1
        }

        table.rowPosY1 = nextY1
        table.rowPosY2 = nextY1
        table.rowTextBaseline = 0f
        table.rowIndentOffsetX = window.dc.indent - table.hostIndentX // Lock indent
        window.dc.prevLineTextBaseOffset = 0.0f
        window.dc.cursorMaxPos.y = nextY1

        // Making the header BG color non-transparent will allow us to overlay it multiple times when handling smooth dragging.
        if (table.rowFlags has Trf.Headers) {
            table.rowBgColor = Col.TableHeaderBg.u32
            if (table.currentRow == 0)
                table.isUsingHeaders = true
        }
    }

    /** [Internal] */
    fun tableEndRow(table: Table) {

        val window = g.currentWindow!!
        assert(window === table.innerWindow)
        assert(table.isInsideRow)

        tableEndCell(table)

        // Position cursor at the bottom of our row so it can be used for e.g. clipping calculation. However it is
        // likely that the next call to TableBeginCell() will reposition the cursor to take account of vertical padding.
        window.dc.cursorPos.y = table.rowPosY2

        // Row background fill
        val bgY1 = table.rowPosY1
        val bgY2 = table.rowPosY2

        if (table.currentRow == 0)
            table.lastFirstRowHeight = bgY2 - bgY1

        if (table.currentRow >= 0 && bgY2 >= table.innerClipRect.min.y && bgY1 <= table.innerClipRect.max.y) {
            // Decide of background color for the row
            val bgCol = when {
                table.rowBgColor != COL32_DISABLE -> table.rowBgColor
                table.flags has Tf.RowBg -> if (table.rowBgColorCounter has 1) Col.TableRowBgAlt.u32 else Col.TableRowBg.u32
                else -> 0
            }
            // Decide of top border color
            var borderCol = 0
            if (table.currentRow != 0 || table.innerWindow === table.outerWindow)
                if (table.flags has Tf.BordersHInner) {
                    //if (table->CurrentRow == 0 && table->InnerWindow == table->OuterWindow)
                    //    border_col = table->BorderOuterColor;
                    //else
                    if (table.currentRow > 0)// && !(table->LastRowFlags & ImGuiTableRowFlags_Headers))
                        borderCol = if (table.lastRowFlags has Trf.Headers) table.borderColorStrong else table.borderColorLight
                } else {
                    //if (table->RowFlags & ImGuiTableRowFlags_Headers)
                    //    border_col = table->BorderOuterColor;
                }

            if (bgCol != 0 || borderCol != 0)
                table.drawSplitter.setCurrentChannel(window.drawList, 0)

            // Draw background
            // We soft/cpu clip this so all backgrounds and borders can share the same clipping rectangle
            if (bgCol != 0) {
                val bgRect = Rect(table.workRect.min.x, bgY1, table.workRect.max.x, bgY2)
                bgRect clipWith table.backgroundClipRect
                if (bgRect.min.y < bgRect.max.y)
                    window.drawList.addRectFilledMultiColor(bgRect.min, bgRect.max, bgCol, bgCol, bgCol, bgCol)
            }

            // Draw top border
            val borderY = bgY1
            if (borderCol != 0 && borderY >= table.backgroundClipRect.min.y && borderY < table.backgroundClipRect.max.y)
                window.drawList.addLine(Vec2(table.borderX1, borderY), Vec2(table.borderX2, borderY), borderCol)
        }

        val unfreezeRows = table.currentRow + 1 == table.freezeRowsCount && table.freezeRowsCount > 0

        // Draw bottom border (always strong)
        val drawSeparatingBorder = unfreezeRows// || (table->RowFlags & ImGuiTableRowFlags_Headers);
        if (drawSeparatingBorder)
            if (bgY2 >= table.backgroundClipRect.min.y && bgY2 < table.backgroundClipRect.max.y)
                window.drawList.addLine(Vec2(table.borderX1, bgY2), Vec2(table.borderX2, bgY2), table.borderColorStrong)

        // End frozen rows (when we are past the last frozen row line, teleport cursor and alter clipping rectangle)
        // We need to do that in TableEndRow() instead of TableBeginRow() so the list clipper can mark end of row and
        // get the new cursor position.
        if (unfreezeRows) {
            assert(!table.isFreezeRowsPassed)
            table.isFreezeRowsPassed = true
            table.drawSplitter.setCurrentChannel(window.drawList, 0)

            val r = Rect(
                    x1 = table.innerClipRect.min.x,
                    y1 = (table.rowPosY2 + 1) max window.innerClipRect.min.y,
                    x2 = table.innerClipRect.max.x,
                    y2 = window.innerClipRect.max.y)
            table.backgroundClipRect put r

            val rowHeight = table.rowPosY2 - table.rowPosY1
            table.rowPosY2 = table.workRect.min.y + table.rowPosY2 - table.outerRect.min.y
            window.dc.cursorPos.y = table.rowPosY2
            table.rowPosY1 = table.rowPosY2 - rowHeight
            for (columnN in 0 until table.columnsCount) {
                val column = table.columns[columnN]!!
                column.drawChannelCurrent = column.drawChannelRowsAfterFreeze
                column.clipRect.min.y = r.min.y
            }
        }

        if (table.rowFlags hasnt Trf.Headers)
            table.rowBgColorCounter++
        table.isInsideRow = false
    }

    /** [Internal] Called by TableNextCell()!
     *  This is called very frequently, so we need to be mindful of unnecessary overhead.
     *  FIXME-TABLE FIXME-OPT: Could probably shortcut some things for non-active or clipped columns. */
    fun tableBeginCell(table: Table, columnN: Int) {

        table.currentColumn = columnN
        val column = table.columns[columnN]!!
        val window = table.innerWindow!!

        // Start position is roughly ~~ CellRect.Min + CellPadding + Indent
        var startX = if (table.rowFlags has Trf.Headers) column.startXHeaders else column.startXRows
        if (column.flags has Tcf.IndentEnable)
            startX += table.rowIndentOffsetX // ~~ += window.DC.Indent.x - table->HostIndentX, except we locked it for the row.

        window.dc.cursorPos.x = startX
        window.dc.cursorPos.y = table.rowPosY1 + table.cellPaddingY
        window.dc.cursorMaxPos.x = window.dc.cursorPos.x
        window.dc.columnsOffset = startX - window.pos.x - window.dc.indent // FIXME-WORKRECT
        window.dc.currLineTextBaseOffset = table.rowTextBaseline
        window.dc.lastItemId = 0

        window.workRect.min.y = window.dc.cursorPos.y
        window.workRect.min.x = column.minX + table.cellPaddingX1
        window.workRect.max.x = column.maxX - table.cellPaddingX2

        // To allow ImGuiListClipper to function we propagate our row height
        if (!column.isVisible)
            window.dc.cursorPos.y = window.dc.cursorPos.y max table.rowPosY2

        window.skipItems = column.skipItems
        if (table.flags has Tf.NoClipX)
            table.drawSplitter.setCurrentChannel(window.drawList, 1)
        else {
            table.drawSplitter.setCurrentChannel(window.drawList, column.drawChannelCurrent)
            //window->ClipRect = column->ClipRect;
            //IM_ASSERT(column->ClipRect.Max.x > column->ClipRect.Min.x && column->ClipRect.Max.y > column->ClipRect.Min.y);
            //window->DrawList->_ClipRectStack.back() = ImVec4(column->ClipRect.Min.x, column->ClipRect.Min.y, column->ClipRect.Max.x, column->ClipRect.Max.y);
            //window->DrawList->UpdateClipRect();
            window.drawList.popClipRect()
            window.drawList.pushClipRect(column.clipRect.min, column.clipRect.max, false)
            //IMGUI_DEBUG_LOG("%d (%.0f,%.0f)(%.0f,%.0f)\n", column_n, column->ClipRect.Min.x, column->ClipRect.Min.y, column->ClipRect.Max.x, column->ClipRect.Max.y);
            window.clipRect put window.drawList._clipRectStack.last()
        }
    }

    /** [Internal] Called by TableNextRow()/TableNextCell()! */
    fun tableEndCell(table: Table) {

        val column = table.columns[table.currentColumn]!!
        val window = table.innerWindow!!

        // Report maximum position so we can infer content size per column.
        val pMaxPosX = when {
            table.rowFlags has Trf.Headers -> column::contentMaxPosHeadersUsed  // Useful in case user submit contents in header row that is not a TableHeader() call
            else -> if (table.isFreezeRowsPassed) column::contentMaxPosRowsUnfrozen else column::contentMaxPosRowsFrozen
        }
        pMaxPosX.set(pMaxPosX() max window.dc.cursorMaxPos.x)
        table.rowPosY2 = table.rowPosY2 max (window.dc.cursorMaxPos.y + table.cellPaddingY)

        // Propagate text baseline for the entire row
        // FIXME-TABLE: Here we propagate text baseline from the last line of the cell.. instead of the first one.
        table.rowTextBaseline = table.rowTextBaseline max window.dc.prevLineTextBaseOffset
    }

    /** Return the cell rectangle based on currently known height.
     *  Important: we generally don't know our row height until the end of the row, so Max.y will be incorrect in many situations.
     *  The only case where this is correct is if we provided a min_row_height to TableNextRow() and don't go below it. */
    fun tableGetCellRect(): Rect {
        val table = g.currentTable!!
        val column = table.columns[table.currentColumn]!!
        return Rect(column.minX, table.rowPosY1, column.maxX, table.rowPosY2)
    }

    fun tableGetColumnName(table: Table, columnN: Int): String? {
        val column = table.columns[columnN]!!
        return table.columnsNames.getOrNull(column.nameOffset)
    }

    /** Return the resizing ID for the right-side of the given column. */
    fun tableGetColumnResizeID(table: Table, columnN: Int, instanceNo: Int = 0): ID {
        assert(columnN < table.columnsCount)
        val id = table.id + (instanceNo * table.columnsCount) + columnN
        return id
    }

    fun tableSetColumnAutofit(table: Table, columnN: Int) {
        // Disable clipping then auto-fit, will take 2 frames
        // (we don't take a shortcut for unclipped columns to reduce inconsistencies when e.g. resizing multiple columns)
        table.columns[columnN]!!.apply {
            cannotSkipItemsQueue = 1 shl 0
            autoFitQueue = 1 shl 1
        }
    }

    fun pushTableBackground() {
        val window = g.currentWindow!!
        val table = g.currentTable!!
        table.drawSplitter.setCurrentChannel(window.drawList, 0)
        pushClipRect(table.hostClipRect.min, table.hostClipRect.max, false)
    }

    fun popTableBackground() {
        val window = g.currentWindow!!
        val table = g.currentTable!!
        val column = table.columns[table.currentColumn]!!
        table.drawSplitter.setCurrentChannel(window.drawList, column.drawChannelCurrent)
        popClipRect()
    }

    //-------------------------------------------------------------------------
    // TABLE - .ini settings
    //-------------------------------------------------------------------------
    // [Init] 1: TableSettingsHandler_ReadXXXX()   Load and parse .ini file into TableSettings.
    // [Main] 2: TableLoadSettings()               When table is created, bind Table to TableSettings, serialize TableSettings data into Table.
    // [Main] 3: TableSaveSettings()               When table properties are modified, serialize Table data into bound or new TableSettings, mark .ini as dirty.
    // [Main] 4: TableSettingsHandler_WriteAll()   When .ini file is dirty (which can come from other source), save TableSettings into .ini file.
    //-------------------------------------------------------------------------

    fun tableLoadSettings(table: Table) {

        table.isSettingsRequestLoad = false
        if (table.flags has Tf.NoSavedSettings)
            return

        // Bind settings
        val settings: TableSettings
        if (table.settingsOffset == -1) {
            settings = findTableSettingsByID(table.id) ?: return
            table.settingsOffset = g.settingsTables.indexOf(settings)
        } else settings = tableGetBoundSettings(table)!!
        table.settingsLoadedFlags = settings.saveFlags
        assert(settings.columnsCount == table.columnsCount)

        // Serialize ImGuiTableSettings/ImGuiTableColumnSettings into ImGuiTable/ImGuiTableColumn
        for (dataN in 0 until settings.columnsCount) {
            val columnSettings = settings.columnSettings[dataN]
            val columnN = columnSettings.index
            if (columnN < 0 || columnN >= table.columnsCount)
                continue

            val column = table.columns[columnN]!!
            if (settings.saveFlags has Tf.Resizable) {
                if (columnSettings.isWeighted)
                    column.widthStretchWeight = columnSettings.widthOrWeight
                else
                    column.widthRequest = columnSettings.widthOrWeight
                column.autoFitQueue = 0x00
            }
            column.displayOrder = when {
                settings.saveFlags has Tf.Reorderable -> columnSettings.displayOrder
                else -> columnN
            }
            column.isVisible = columnSettings.isVisible
            column.isVisibleNextFrame = column.isVisible
            column.sortOrder = columnSettings.sortOrder
            column.sortDirection = columnSettings.sortDirection
        }

        // FIXME-TABLE: Need to validate .ini data
        for (columnN in 0 until table.columnsCount)
            table.displayOrderToIndex[table.columns[columnN]!!.displayOrder] = columnN.b
    }

    companion object {

        fun createTableSettings(id: ID, columnsCount: Int): TableSettings {
            val settings = TableSettings(id, columnsCount, columnsCount)
            g.settingsTables += settings
            return settings
        }

        /** Find existing settings
         *  FIXME-OPT: Might want to store a lookup map for this? */
        fun findTableSettingsByID(id: ID): TableSettings? = g.settingsTables.find { it.id == id }

        fun tableFixColumnSortDirection(column: TableColumn) {
            // Handle NoSortAscending/NoSortDescending
            if (column.sortDirection == SortDirection.Ascending && column.flags has Tcf.NoSortAscending)
                column.sortDirection = SortDirection.Descending
            else if (column.sortDirection == SortDirection.Descending && column.flags has Tcf.NoSortDescending)
                column.sortDirection = SortDirection.Ascending
        }

        fun tableUpdateColumnsWeightFromWidth(table: Table) {

            assert(table.leftMostStretchedColumnDisplayOrder != -1)

            // Measure existing quantity
            var visibleWeight = 0f
            var visibleWidth = 0f
            for (columnN in 0 until table.columnsCount) {
                val column = table.columns[columnN]!!
                if (!column.isVisible || column.flags hasnt Tcf.WidthStretch)
                    continue
                visibleWeight += column.widthStretchWeight
                visibleWidth += column.widthRequest
            }
            assert(visibleWeight > 0f && visibleWidth > 0f)

            // Apply new weights
            for (columnN in 0 until table.columnsCount) {
                val column = table.columns[columnN]!!
                if (!column.isVisible || column.flags hasnt Tcf.WidthStretch)
                    continue
                column.widthStretchWeight = (column.widthRequest / visibleWidth) * visibleWeight
            }
        }

        fun tableSettingsHandler_ClearAll(ctx: Context, settingsHandler: SettingsHandler) {
            val g = ctx
            for (i in 0 until g.tables.size)
                g.tables[PoolIdx(i)].settingsOffset = -1
            g.settingsTables.clear()
        }

        /** Apply to existing windows (if any) */
        fun tableSettingsHandler_ApplyAll(ctx: Context, settingsHandler: SettingsHandler) {
            val g = ctx
            for (i in 0 until g.tables.size) {
                val table = g.tables[PoolIdx(i)]
                table.isSettingsRequestLoad = true
                table.settingsOffset = -1
            }
        }

        fun tableSettingsHandler_ReadOpen(ctx: Context, settingsHandler: SettingsHandler, name: String): TableSettings? {
            if (name.startsWith("0x")) {
                val chunks = name.split(',')
                if (chunks.size == 2) {
                    val id = chunks[0].substring(2).toLong(16).i
                    val columnsCount = chunks[1].i

                    findTableSettingsByID(id)?.let { settings ->
                        if (settings.columnsCountMax >= columnsCount)
                            return settings.init(id, columnsCount, settings.columnsCountMax) // Recycle
                        settings.id = 0 // Invalidate storage if we won't fit because of a count change
                    }

                    return createTableSettings(id, columnsCount)
                }
            }
            return null
        }

        fun tableSettingsHandler_ReadLine(ctx: Context, settingsHandler: SettingsHandler, entry: Any?, line: String) {
            // "Column 0  UserID=0x42AD2D21 Width=100 Visible=1 Order=0 Sort=0v"
            val settings = entry as TableSettings
            if (line.startsWith("Column")) {
                val chunks = line.split(Regex("\\s+"))
                var r = 1
                val columnN = chunks[r++].i
                if (columnN < 0 || columnN >= settings.columnsCount)
                    return

//        char c = 0
                val column = settings.columnSettings[columnN]
                column.index = columnN
                if (chunks[r].startsWith("UserID=0x")) column.userID = chunks[r++].substring(6 + 1 + 2).toInt(16)
                if (chunks[r].startsWith("Width=")) {
                    column.widthOrWeight = chunks[r++].substring(5 + 1).i.f
                    column.isWeighted = false
                    settings.saveFlags = settings.saveFlags or Tf.Resizable
                }
                if (chunks[r].startsWith("Weight=")) {
                    column.widthOrWeight = chunks[r++].substring(6 + 1).f
                    column.isWeighted = true
                    settings.saveFlags = settings.saveFlags or Tf.Resizable
                }
                if (chunks[r].startsWith("Visible=")) {
                    column.isVisible = chunks[r++].substring(7 + 1).i.bool
                    settings.saveFlags = settings.saveFlags or Tf.Hideable
                }
                if (chunks[r].startsWith("Order=")) {
                    column.displayOrder = chunks[r++].substring(5 + 1).i
                    settings.saveFlags = settings.saveFlags or Tf.Reorderable
                }
                if (chunks[r].startsWith("Sort=")) {
                    val chunk = chunks[r++]
                    column.sortOrder = chunk[4 + 1].i
                    val c = chunk[4 + 1 + 1]
                    column.sortDirection = if (c == '^') SortDirection.Descending else SortDirection.Ascending
                    settings.saveFlags = settings.saveFlags or Tf.Sortable
                }
            }
        }

        fun tableSettingsHandler_WriteAll(ctx: Context, handler: SettingsHandler, buf: StringBuilder) {

            for (settings in g.settingsTables) {

                if (settings.id == 0) // Skip ditched settings
                    continue

                // TableSaveSettings() may clear some of those flags when we establish that the data can be stripped
                // (e.g. Order was unchanged)
                val saveSize = settings.saveFlags has Tf.Resizable
                val saveVisible = settings.saveFlags has Tf.Hideable
                val saveOrder = settings.saveFlags has Tf.Reorderable
                val saveSort = settings.saveFlags has Tf.Sortable
                if (!saveSize && !saveVisible && !saveOrder && !saveSort)
                    continue

                buf.ensureCapacity(buf.length + 30 + settings.columnsCount * 50) // ballpark reserve
                buf += "[${handler.typeName}][0x%08X,${settings.columnsCount}]\n".format(settings.id) // format because we want to keep leading 0s
                for (columnN in 0 until settings.columnsCount) {
                    val column = settings.columnSettings[columnN]
                    // "Column 0  UserID=0x42AD2D21 Width=100 Visible=1 Order=0 Sort=0v"
                    buf += "Column %-2d".format(columnN)
                    if (column.userID != 0) buf += " UserID=%08X".format(column.userID)
                    if (saveSize  && column.isWeighted) buf += " Weight=%.4f".format(column.widthOrWeight)
                    if (saveSize && !column.isWeighted) buf += " Width=${column.widthOrWeight}"
                    if (saveVisible) buf += " Visible=${column.isVisible.i}"
                    if (saveOrder) buf += " Order=${column.displayOrder}"
                    if (saveSort && column.sortOrder != -1) buf += " Sort=${column.sortOrder}${if (column.sortDirection == SortDirection.Ascending) 'v' else '^'}"
                    buf += '\n'
                }
                buf += '\n'
            }
        }
    }

    fun tableSaveSettings(table: Table) {

        table.isSettingsDirty = false
        if (table.flags has Tf.NoSavedSettings)
            return

        // Bind or create settings data
        val settings = tableGetBoundSettings(table)
                ?: createTableSettings(table.id, table.columnsCount).also { settings ->
                    table.settingsOffset = g.settingsTables.indexOf(settings)
                }
        settings.columnsCount = table.columnsCount

        // Serialize ImGuiTable/ImGuiTableColumn into ImGuiTableSettings/ImGuiTableColumnSettings
        assert(settings.id == table.id)
        assert(settings.columnsCount == table.columnsCount && settings.columnsCountMax >= settings.columnsCount)

        // FIXME-TABLE: Logic to avoid saving default widths?
        settings.saveFlags = Tf.Resizable.i
        for (n in 0 until table.columnsCount) {
            val column = table.columns[n]!!
            val columnSettings = settings.columnSettings[n]

            columnSettings.widthOrWeight = if(column.flags has Tcf.WidthStretch) column.widthStretchWeight else column.widthRequest
            columnSettings.index = n
            columnSettings.displayOrder = column.displayOrder
            columnSettings.sortOrder = column.sortOrder
            columnSettings.sortDirection = column.sortDirection
            columnSettings.isVisible = column.isVisible
            columnSettings.isWeighted = column.flags has Tcf.WidthStretch

            // We skip saving some data in the .ini file when they are unnecessary to restore our state
            // FIXME-TABLE: We don't have logic to easily compare SortOrder to DefaultSortOrder yet so it's always saved when present.
            if (column.displayOrder != n)
                settings.saveFlags = settings.saveFlags or Tf.Reorderable
            if (column.sortOrder != -1)
                settings.saveFlags = settings.saveFlags or Tf.Sortable
            if (column.isVisible != column.flags hasnt Tcf.DefaultHide)
                settings.saveFlags = settings.saveFlags or Tf.Hideable
        }
        settings.saveFlags = settings.saveFlags and table.flags

        markIniSettingsDirty()
    }

    /** Get settings for a given table, NULL if none */
    fun tableGetBoundSettings(table: Table): TableSettings? {
        if (table.settingsOffset != -1) {
            val settings = g.settingsTables[table.settingsOffset]
            assert(settings.id == table.id)
            if (settings.columnsCountMax >= table.columnsCount)
                return settings // OK
            settings.id = 0 // Invalidate storage, we won't fit because of a count change
        }
        return null
    }

    fun tableInstallSettingsHandler(context: Context) {
        g.settingsHandlers += SettingsHandler().apply {
            typeName = "Table"
            typeHash = hash("Table")
            clearAllFn = ::tableSettingsHandler_ClearAll
            readOpenFn = ::tableSettingsHandler_ReadOpen
            readLineFn = ::tableSettingsHandler_ReadLine
            applyAllFn = ::tableSettingsHandler_ApplyAll
            writeAllFn = ::tableSettingsHandler_WriteAll
        }
    }
}