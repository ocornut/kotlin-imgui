package imgui.api

import gli_.has
import gli_.hasnt
import glm_.i
import glm_.max
import glm_.vec2.Vec2
import imgui.*
import imgui.ImGui.beginTableEx
import imgui.ImGui.calcTextSize
import imgui.ImGui.checkbox
import imgui.ImGui.endChild
import imgui.ImGui.findRenderedTextEnd
import imgui.ImGui.getColorU32
import imgui.ImGui.getID
import imgui.ImGui.invisibleButton
import imgui.ImGui.io
import imgui.ImGui.isItemActive
import imgui.ImGui.isItemHovered
import imgui.ImGui.isMouseDragging
import imgui.ImGui.isMouseReleased
import imgui.ImGui.itemSize
import imgui.ImGui.openPopup
import imgui.ImGui.popID
import imgui.ImGui.popStyleColor
import imgui.ImGui.popStyleVar
import imgui.ImGui.pushID
import imgui.ImGui.pushStyleColor
import imgui.ImGui.pushStyleVar
import imgui.ImGui.renderText
import imgui.ImGui.renderTextEllipsis
import imgui.ImGui.sameLine
import imgui.ImGui.selectable
import imgui.ImGui.style
import imgui.ImGui.tableBeginCell
import imgui.ImGui.tableBeginRow
import imgui.ImGui.tableDrawBorders
import imgui.ImGui.tableDrawMergeChannels
import imgui.ImGui.tableEndCell
import imgui.ImGui.tableEndRow
import imgui.ImGui.tableGetCellRect
import imgui.ImGui.tableGetColumnName
import imgui.ImGui.tableSaveSettings
import imgui.ImGui.tableSortSpecsClickColumn
import imgui.ImGui.tableSortSpecsSanitize
import imgui.ImGui.tableUpdateLayout
import imgui.ImGui.textLineHeight
import imgui.classes.TableSortSpecs
import imgui.classes.TableSortSpecsColumn
import imgui.internal.classes.Rect
import imgui.internal.classes.Table
import imgui.internal.floor
import imgui.internal.isPowerOfTwo
import imgui.TableColumnFlag as Tcf
import imgui.TableFlag as Tf
import imgui.TableRowFlag as Trf

// Tables
// [ALPHA API] API will evolve! (FIXME-TABLE)
// - Full-featured replacement for old Columns API
// - In most situations you can use TableNextRow() + TableSetColumnIndex() to populate a table.
// - If you are using tables as a sort of grid, populating every columns with the same type of contents,
//   you may prefer using TableNextCell() instead of TableNextRow() + TableSetColumnIndex().
// - See Demo->Tables for details.
// - See ImGuiTableFlags_ enums for a description of available flags.
interface tables {

    //-----------------------------------------------------------------------------
    // [SECTION] Widgets: BeginTable, EndTable, etc.
    //-----------------------------------------------------------------------------

    //-----------------------------------------------------------------------------
    // Typical call flow: (root level is public API):
    // - BeginTable()                               user begin into a table
    //    | BeginChild()                            - (if ScrollX/ScrollY is set)
    //    | TableBeginUpdateColumns()               - apply resize/order requests, lock columns active state, order
    //    | - TableSetColumnWidth()                 - apply resizing width (for mouse resize, often requested by previous frame)
    //    |    - TableUpdateColumnsWeightFromWidth()- recompute columns weights (of weighted columns) from their respective width
    // - TableSetupColumn()                         user submit columns details (optional)
    // - TableAutoHeaders() or TableHeader()        user submit a headers row (optional)
    //    | TableSortSpecsClickColumn()             - when clicked: alter sort order and sort direction
    // - TableGetSortSpecs()                        user queries updated sort specs (optional, generally after submitting headers)
    // - TableNextRow() / TableNextCell()           user begin into the first row, also automatically called by TableAutoHeaders()
    //    | TableUpdateLayout()                     - lock all widths and columns positions! called by the FIRST call to TableNextRow()!
    //    | - TableUpdateDrawChannels()               - setup ImDrawList channels
    //    | - TableUpdateBorders()                    - detect hovering columns for resize, ahead of contents submission
    //    | - TableDrawContextMenu()                  - draw right-click context menu
    //    | TableEndCell()                          - close existing cell if not the first time
    //    | TableBeginCell()                        - enter into current cell
    // - [...]                                      user emit contents
    // - EndTable()                                 user ends the table
    //    | TableDrawBorders()                      - draw outer borders, inner vertical borders
    //    | TableDrawMergeChannels()                - merge draw channels if clipping isn't required
    //    | EndChild()                              - (if ScrollX/ScrollY is set)
    //-----------------------------------------------------------------------------

    // Configuration
    companion object {
        /** Extend outside inner borders. */
        val TABLE_RESIZE_SEPARATOR_HALF_THICKNESS = 4f

        /** Delay/timer before making the hover feedback (color+cursor) visible because tables/columns tends to be more cramped. */
        val TABLE_RESIZE_SEPARATOR_FEEDBACK_TIMER = 0.06f

        val b = BooleanArray(2)

        val tableGetMinColumnWidth: Float
            get() = // g.Style.ColumnsMinSpacing;
                style.framePadding.x * 3f

        /** Adjust flags: default width mode + weighted columns are not allowed when auto extending */
        fun tableFixColumnFlags(table: Table, flags_: TableColumnFlags): TableColumnFlags {

            var flags = flags_
            // Sizing Policy
            if (flags hasnt Tcf.WidthMask_)
                flags = when {
                    // FIXME-TABLE: Inconsistent to promote columns to WidthAlwaysAutoResize
                    table.flags has Tf.SizingPolicyFixedX -> flags or if (table.flags has Tf.Resizable && flags hasnt Tcf.NoResize) Tcf.WidthFixed else Tcf.WidthAlwaysAutoResize
                    else -> flags or Tcf.WidthStretch
                }
            assert((flags and Tcf.WidthMask_).isPowerOfTwo) { "Check that only 1 of each set is used." }
            if (flags has Tcf.WidthAlwaysAutoResize)// || ((flags & ImGuiTableColumnFlags_WidthStretch) && (table->Flags & ImGuiTableFlags_SizingPolicyStretchX)))
                flags = flags or Tcf.NoResize
            //if ((flags & ImGuiTableColumnFlags_WidthStretch) && (table->Flags & ImGuiTableFlags_SizingPolicyFixedX))
            //    flags = (flags & ~ImGuiTableColumnFlags_WidthMask_) | ImGuiTableColumnFlags_WidthFixed;

            // Sorting
            if (flags has Tcf.NoSortAscending && flags has Tcf.NoSortDescending)
                flags = flags or Tcf.NoSort

            // Alignment
            //if ((flags & ImGuiTableColumnFlags_AlignMask_) == 0)
            //    flags |= ImGuiTableColumnFlags_AlignCenter;
            //IM_ASSERT(ImIsPowerOfTwo(flags & ImGuiTableColumnFlags_AlignMask_)); // Check that only 1 of each set is used.

            return flags
        }
    }

    /** Helper */
    fun tableFixFlags(flags_: TableFlags): TableFlags {
        var flags = flags_
        // Adjust flags: set default sizing policy
        if (flags hasnt Tf.SizingPolicyMaskX_)
            flags = flags or if (flags has Tf.ScrollX) Tf.SizingPolicyFixedX else Tf.SizingPolicyStretchX

        // Adjust flags: MultiSortable automatically enable Sortable
        if (flags has Tf.MultiSortable)
            flags = flags or Tf.Sortable

        // Adjust flags: disable saved settings if there's nothing to save
        if (flags hasnt (Tf.Resizable or Tf.Hideable or Tf.Reorderable or Tf.Sortable))
            flags = flags or Tf.NoSavedSettings

        // Adjust flags: enforce borders when resizable
        if (flags has Tf.Resizable)
            flags = flags or Tf.BordersVInner

        // Adjust flags: disable top rows freezing if there's no scrolling.
        // we could want to assert if ScrollFreeze was set without the corresponding scroll flag, but that would hinder demos.
        if (flags hasnt Tf.ScrollX)
            flags = flags wo Tf.ScrollFreezeColumnsMask_
        if (flags hasnt Tf.ScrollY)
            flags = flags wo Tf.ScrollFreezeRowsMask_

        // Adjust flags: disable NoHostExtendY if we have any scrolling going on
        if (flags has Tf.NoHostExtendY && flags has (Tf.ScrollX or Tf.ScrollY))
            flags = flags wo Tf.NoHostExtendY

        // Adjust flags: we don't support NoClipX with (FreezeColumns > 0)
        // We could with some work but it doesn't appear to be worth the effort
        if (flags has Tf.ScrollFreezeColumnsMask_)
            flags = flags wo Tf.NoClipX

        return flags
    }

    /** (Read carefully because this is subtle but it does make sense!)
     *  About 'outer_size', its meaning needs to differ slightly depending of if we are using ScrollX/ScrollY flags:
     *    X:
     *    - outer_size.x < 0.0f  ->  right align from window/work-rect maximum x edge.
     *    - outer_size.x = 0.0f  ->  auto enlarge, use all available space.
     *    - outer_size.x > 0.0f  ->  fixed width
     *    Y with ScrollX/ScrollY: using a child window for scrolling:
     *    - outer_size.y < 0.0f  ->  bottom align
     *    - outer_size.y = 0.0f  ->  bottom align, consistent with BeginChild(). not recommended unless table is last item in parent window.
     *    - outer_size.y > 0.0f  ->  fixed child height. recommended when using Scrolling on any axis.
     *    Y without scrolling, we output table directly in parent window:
     *    - outer_size.y < 0.0f  ->  bottom align (will auto extend, unless NoHostExtendV is set)
     *    - outer_size.y = 0.0f  ->  zero minimum height (will auto extend, unless NoHostExtendV is set)
     *    - outer_size.y > 0.0f  ->  minimum height (will auto extend, unless NoHostExtendV is set)
     *  About 'inner_width':
     *    With ScrollX:
     *    - inner_width  < 0.0f  ->  *illegal* fit in known width (right align from outer_size.x) <-- weird
     *    - inner_width  = 0.0f  ->  fit in outer_width: Fixed size columns will take space they need (if avail, otherwise shrink down), Stretch columns becomes Fixed columns.
     *    - inner_width  > 0.0f  ->  override scrolling width, generally to be larger than outer_size.x. Fixed column take space they need (if avail, otherwise shrink down), Stretch columns share remaining space!
     *    Without ScrollX:
     *    - inner_width          ->  *ignored*
     *  Details:
     *  - If you want to use Stretch columns with ScrollX, you generally need to specify 'inner_width' otherwise the concept
     *    of "available space" doesn't make sense.
     *  - Even if not really useful, we allow 'inner_width < outer_size.x' for consistency and to facilitate understanding
     *    of what the value does. */
    fun beginTable(strId: String, columnsCount: Int, flags: TableFlags = Tf.None.i, outerSize: Vec2 = Vec2(), innerWidth: Float = 0f): Boolean {
        val id = getID(strId)
        return beginTableEx(strId, id, columnsCount, flags, outerSize, innerWidth)
    }

    /** only call EndTable() if BeginTable() returns true! */
    fun endTable() {

        val table = g.currentTable ?: error("Only call EndTable() if BeginTable() returns true!")

        // This assert would be very useful to catch a common error... unfortunately it would probably trigger in some
        // cases, and for consistency user may sometimes output empty tables (and still benefit from e.g. outer border)
        //IM_ASSERT(table->IsLayoutLocked && "Table unused: never called TableNextRow(), is that the intent?");

        // If the user never got to call TableNextRow() or TableNextCell(), we call layout ourselves to ensure all our
        // code paths are consistent (instead of just hoping that TableBegin/TableEnd will work), get borders drawn, etc.
        if (!table.isLayoutLocked)
            tableUpdateLayout(table)

        val flags = table.flags
        val innerWindow = table.innerWindow!!
        val outerWindow = table.outerWindow!!
        assert(innerWindow === g.currentWindow)
        assert(outerWindow === innerWindow || outerWindow === innerWindow.parentWindow)

        if (table.isInsideRow)
            tableEndRow(table)

        // Finalize table height
        innerWindow.skipItems = table.hostSkipItems
        innerWindow.dc.cursorMaxPos put table.hostCursorMaxPos
        if (innerWindow != outerWindow) {
            table.outerRect.max.y = table.outerRect.max.y max (innerWindow.pos.y + innerWindow.size.y)
            innerWindow.dc.cursorMaxPos.y = table.rowPosY2
        } else if (flags hasnt Tf.NoHostExtendY) {
            table.outerRect.max.y = table.outerRect.max.y max (innerWindow.dc.cursorPos.y)
            innerWindow.dc.cursorMaxPos.y = table.rowPosY2
        }
        table.workRect.max.y = table.workRect.max.y max table.outerRect.max.y
        table.lastOuterHeight = table.outerRect.height

        if (flags hasnt Tf.NoClipX)
            innerWindow.drawList.popClipRect()
        innerWindow.clipRect put innerWindow.drawList._clipRectStack.last()

        // Draw borders
        if (flags has Tf.Borders)
            tableDrawBorders(table)

        // Flatten channels and merge draw calls
        table.drawSplitter.setCurrentChannel(innerWindow.drawList, 0)
        tableDrawMergeChannels(table)

        // When releasing a column being resized, scroll to keep the resulting column in sight
        val minColumnWidth = tableGetMinColumnWidth
        if (table.flags hasnt Tf.ScrollX && innerWindow !== outerWindow)
            innerWindow.scroll.x = 0f
        else if (table.lastResizedColumn != -1 && table.resizedColumn == -1 && innerWindow.scrollbar.x && table.instanceInteracted == table.instanceCurrent) {
            val column = table.columns[table.lastResizedColumn]!!
            val width = if (column.maxX < table.innerClipRect.min.x) -minColumnWidth else +minColumnWidth
            innerWindow.setScrollFromPosX(column.maxX - innerWindow.pos.x + width, 1f)
        }

        // Apply resizing/dragging at the end of the frame
        if (table.resizedColumn != -1) {
            val column = table.columns[table.resizedColumn]!!
            val newX2 = io.mousePos.x - g.activeIdClickOffset.x + TABLE_RESIZE_SEPARATOR_HALF_THICKNESS
            val newWidth = floor(newX2 - column.minX)
            table.resizedColumnNextWidth = newWidth
        }

        // Layout in outer window
        val backupOuterCursorPosX = outerWindow.dc.cursorPos.x
        val backupOuterMaxPosX = outerWindow.dc.cursorMaxPos.x
        val backupInnerMaxPosX = innerWindow.dc.cursorMaxPos.x
        innerWindow.workRect put table.hostWorkRect
        innerWindow.skipItems = table.hostSkipItems
        outerWindow.dc.cursorPos put table.outerRect.min
        outerWindow.dc.columnsOffset = 0f
        if (innerWindow !== outerWindow)
            endChild()
        else {
            popID()
            val itemSz = table.outerRect.size
            itemSz.x = table.columnsTotalWidth
            itemSize(itemSz)
        }

        // Store content width reference for each column
        var maxPosX = backupInnerMaxPosX
        for (columnN in 0 until table.columnsCount) {

            val column = table.columns[columnN]!!

            // Store content width (for both Headers and Rows)
            //float ref_x = column->MinX;
            val refXRows = column.startXRows - table.cellPaddingX1
            val refXHeaders = column.startXHeaders - table.cellPaddingX1
            column.contentWidthRowsFrozen = 0 max (column.contentMaxPosRowsFrozen - refXRows).i
            column.contentWidthRowsUnfrozen = 0 max (column.contentMaxPosRowsUnfrozen - refXRows).i
            column.contentWidthHeadersUsed = 0 max (column.contentMaxPosHeadersUsed - refXHeaders).i
            column.contentWidthHeadersIdeal = 0 max (column.contentMaxPosHeadersIdeal - refXHeaders).i

            // Add an extra 1 pixel so we can see the last column vertical line if it lies on the right-most edge.
            if (table.visibleMaskByIndex has (1L shl columnN))
                maxPosX = maxPosX max (column.maxX + 1f)
        }

        // Override EndChild/ItemSize max extent with our own to enable auto-resize on the X axis when possible
        // FIXME-TABLE: This can be improved (e.g. for Fixed columns we don't want to auto AutoFitWidth? or propagate window auto-fit to table?)
        if (table.flags has Tf.ScrollX) {
            innerWindow.dc.cursorMaxPos.x = maxPosX // Set contents width for scrolling
            outerWindow.dc.cursorMaxPos.x = backupOuterMaxPosX max (backupOuterCursorPosX + table.columnsTotalWidth + 1f + innerWindow.scrollbarSizes.x) // For auto-fit
        } else
            outerWindow.dc.cursorMaxPos.x = backupOuterMaxPosX max (table.workRect.min.x + table.columnsAutoFitWidth) // For auto-fit

        // Save settings
        if (table.isSettingsDirty)
            tableSaveSettings(table)

        // Clear or restore current table, if any
        assert(g.currentWindow === outerWindow)
        assert(g.currentTable === table)
        outerWindow.dc.currentTable = null
        g.currentTableStack.removeAt(g.currentTableStack.lastIndex)
        g.currentTable = if (g.currentTableStack.isNotEmpty()) g.tables.getByIndex(g.currentTableStack.last().index) else null
    }

    /** append into the first cell of a new row.
     *
     *  Starts into the first cell of a new row     */
    fun tableNextRow(rowFlags: TableRowFlags = Trf.None.i, rowMinHeight: Float = 0f) {

        val table = g.currentTable!!

        if (table.currentRow == -1)
            tableUpdateLayout(table)
        else if (table.isInsideRow)
            tableEndRow(table)

        table.lastRowFlags = table.rowFlags
        table.rowFlags = rowFlags
        table.rowMinHeight = rowMinHeight
        tableBeginRow(table)

        // We honor min_row_height requested by user, but cannot guarantee per-row maximum height,
        // because that would essentially require a unique clipping rectangle per-cell.
        table.rowPosY2 += table.cellPaddingY * 2f
        table.rowPosY2 = max(table.rowPosY2, table.rowPosY1 + rowMinHeight)

        tableBeginCell(table, 0)
    }

    /** append into the next column (next column, or next row if currently in last column). Return true if column is visible.
     *
     *  Append into the next cell
     *  FIXME-TABLE: Wrapping to next row should be optional?     */
    fun tableNextCell(): Boolean {

        val table = g.currentTable!!

        if (table.currentColumn != -1 && table.currentColumn + 1 < table.columnsCount) {
            tableEndCell(table)
            tableBeginCell(table, table.currentColumn + 1)
        } else
            tableNextRow()

        val columnN = table.currentColumn
        return table.visibleUnclippedMaskByIndex has (1L shl columnN)
    }

    /** append into the specified column. Return true if column is visible. */
    fun tableSetColumnIndex(columnIdx: Int): Boolean {

        val table = g.currentTable ?: return false

        if (table.currentColumn != columnIdx) {
            if (table.currentColumn != -1)
                tableEndCell(table)
            assert(columnIdx >= 0 && table.columnsCount != 0)
            tableBeginCell(table, columnIdx)
        }

        return table.visibleUnclippedMaskByIndex has (1L shl columnIdx)
    }

    // return current column index.
    fun tableGetColumnIndex(): Int = g.currentTable?.currentColumn ?: 0

    /** return NULL if column didn't have a name declared by TableSetupColumn(). Pass -1 to use current column. */
    fun tableGetColumnName(columnN: Int = -1): String? {
        val table = g.currentTable ?: return null
        return tableGetColumnName(table, if (columnN < 0) table.currentColumn else columnN)
    }

    /** return true if column is visible. Same value is also returned by TableNextCell() and TableSetColumnIndex(). Pass -1 to use current column. */
    fun tableGetColumnIsVisible(columnN_: Int): Boolean {
        val table = g.currentTable ?: return false
        val columnN = if (columnN_ < 0) table.currentColumn else columnN_
        return table.visibleUnclippedMaskByIndex has (1L shl columnN)
    }

    /** return true if column is included in the sort specs. Rarely used, can be useful to tell if a data change should trigger resort. Equivalent to test ImGuiTableSortSpecs's ->ColumnsMask & (1 << column_n). Pass -1 to use current column. */
    fun tableGetColumnIsSorted(columnN_: Int): Boolean {
        val table = g.currentTable ?: return false
        val columnN = if (columnN_ < 0) table.currentColumn else columnN_
        val column = table.columns[columnN]!!
        return column.sortOrder != -1
    }

    /** Tables: Headers & Columns declaration
     *  - Use TableSetupColumn() to specify label, resizing policy, default width, id, various other flags etc.
     *  - The name passed to TableSetupColumn() is used by TableAutoHeaders() and by the context-menu
     *  - Use TableAutoHeaders() to submit the whole header row, otherwise you may treat the header row as a regular row, manually call TableHeader() and other widgets.
     *  - Headers are required to perform some interactions: reordering, sorting, context menu // FIXME-TABLE: remove context from this list!
     *
     * We use a default parameter of 'init_width_or_weight == -1'
     *   ImGuiTableColumnFlags_WidthFixed,    width  <= 0 --> init width == auto
     *   ImGuiTableColumnFlags_WidthFixed,    width  >  0 --> init width == manual
     *   ImGuiTableColumnFlags_WidthStretch,  weight <  0 --> init weight == 1.0f
     *   ImGuiTableColumnFlags_WidthStretch,  weight >= 0 --> init weight == custom
     *  Use a different API? */
    fun tableSetupColumn(label: String?, flags_: TableColumnFlags = Tcf.None.i, initWidthOrWeight: Float = -1f, userId: ID = 0) {

        var flags = flags_

        val table = g.currentTable ?: error("Need to call TableSetupColumn() after BeginTable()!")
        assert(!table.isLayoutLocked) { "Need to call call TableSetupColumn() before first row!" }
        assert(table.declColumnsCount >= 0 && table.declColumnsCount < table.columnsCount) { "Called TableSetupColumn() too many times!" }

        val column = table.columns[table.declColumnsCount]!!
        table.declColumnsCount++

        // When passing a width automatically enforce WidthFixed policy
        // (vs TableFixColumnFlags would default to WidthAlwaysAutoResize)
        // (we write to FlagsIn which is a little misleading, another solution would be to pass init_width_or_weight to TableFixColumnFlags)
        if (flags hasnt Tcf.WidthMask_)
            if (table.flags has Tf.SizingPolicyFixedX && initWidthOrWeight > 0f)
                flags = flags or Tcf.WidthFixed

        column.userID = userId
        column.flagsIn = flags
        column.flags = tableFixColumnFlags(table, column.flagsIn)
        flags = column.flags

        // Initialize defaults
        // FIXME-TABLE: We don't restore widths/weight so let's avoid using IsSettingsLoaded for now
        if (table.isInitializing && column.widthRequest < 0f && column.widthStretchWeight < 0f) { // && !table->IsSettingsLoaded)
            // Init width or weight
            // Disable auto-fit if a default fixed width has been specified
            if (flags has Tcf.WidthFixed && initWidthOrWeight > 0f) {
                column.widthRequest = initWidthOrWeight
                column.autoFitQueue = 0x00
            }
            column.widthStretchWeight = when {
                flags has Tcf.WidthStretch -> {
                    assert(initWidthOrWeight < 0f || initWidthOrWeight > 0f)
                    if (initWidthOrWeight < 0f) 1f else initWidthOrWeight
                }
                else -> 1f
            }
        }
        if (table.isInitializing) {
            // Init default visibility/sort state
            if (flags has Tcf.DefaultHide && table.settingsLoadedFlags hasnt Tf.Hideable) {
                column.isVisible = false
                column.isVisibleNextFrame = false
            }
            if (flags has Tcf.DefaultSort && table.settingsLoadedFlags hasnt Tf.Sortable) {
                column.sortOrder = 0 // Multiple columns using _DefaultSort will be reordered when building the sort specs.
                column.sortDirection = when {
                    column.flags has Tcf.PreferSortDescending -> SortDirection.Descending
                    else -> SortDirection.Ascending
                }
            }
        }

        // Store name (append with zero-terminator in contiguous buffer)
        assert(column.nameOffset == -1)
        if (label != null) {
            column.nameOffset = table.columnsNames.size
            table.columnsNames += label
        }
    }

    /** submit all headers cells based on data provided to TableSetupColumn() + submit context menu
     *
     *  This is a helper to output TableHeader() calls based on the column names declared in TableSetupColumn().
     *  The intent is that advanced users willing to create customized headers would not need to use this helper and may
     *  create their own. However presently this function uses too many internal structures/calls.     */
    fun tableAutoHeaders() {

        val window = g.currentWindow!!

        val table = g.currentTable ?: error("Need to call TableAutoHeaders() after BeginTable()!")
        val columnsCount = table.columnsCount

        // Calculate row height (for the unlikely case that labels may be are multi-line)
        var rowHeight = textLineHeight
        for (columnN in 0 until columnsCount)
            if (tableGetColumnIsVisible(columnN))
                rowHeight = rowHeight max calcTextSize(tableGetColumnName(columnN)!!).y
        rowHeight += style.cellPadding.y * 2f

        // Open row
        tableNextRow(Trf.Headers.i, rowHeight)
        if (table.hostSkipItems) // Merely an optimization
            return

        // This for loop is constructed to not make use of internal functions,
        // as this is intended to be a base template to copy and build from.
        var openContextPopup = Int.MAX_VALUE
        for (columnN in 0 until columnsCount) {
            if (!tableSetColumnIndex(columnN))
                continue

            val name = tableGetColumnName(columnN) ?: ""

            // [DEBUG] Test custom user elements
            if (false && DEBUG && columnN < 2) {
                pushID(columnN)
                pushStyleVar(StyleVar.FramePadding, Vec2(0))
                checkbox("##", b, columnN)
                popStyleVar()
                popID()
                sameLine(0f, style.itemInnerSpacing.x)
            }

            // [DEBUG]
            //if (g.IO.KeyCtrl) { static char buf[32]; name = buf; ImGuiTableColumn* c = &table->Columns[column_n]; if (c->Flags & ImGuiTableColumnFlags_WidthStretch) ImFormatString(buf, 32, "%.3f>%.1f", c->ResizeWeight, c->WidthGiven); else ImFormatString(buf, 32, "%.1f", c->WidthGiven); }

            // Push an id to allow unnamed labels (generally accidental, but let's behave nicely with them)
            pushID(table.instanceCurrent * table.columnsCount + columnN)
            tableHeader(name)
            popID()

            // We don't use BeginPopupContextItem() because we want the popup to stay up even after the column is hidden
            if (isMouseReleased(MouseButton.Right) && isItemHovered(HoveredFlag.AllowWhenBlockedByPopup))
                openContextPopup = columnN
        }

        // FIXME-TABLE: This is not user-land code any more + need to explain WHY this is here!
        window.skipItems = table.hostSkipItems

        // Allow opening popup from the right-most section after the last column
        // FIXME-TABLE: This is not user-land code any more... perhaps instead we should expose hovered column.
        // and allow some sort of row-centric IsItemHovered() for full flexibility?
        var unusedX1 = table.workRect.min.x
        if (table.rightMostVisibleColumn != -1)
            unusedX1 = unusedX1 max table.columns[table.rightMostVisibleColumn]!!.maxX
        if (unusedX1 < table.workRect.max.x) {
            // FIXME: We inherit ClipRect/SkipItem from last submitted column (active or not), let's temporarily override it.
            // Because we don't perform any rendering here we just overwrite window->ClipRect used by logic.
            window.clipRect put table.innerClipRect

            val backupCursorMaxPos = Vec2(window.dc.cursorMaxPos)
            window.dc.cursorPos = Vec2(unusedX1, table.rowPosY1)
            val size = Vec2(table.workRect.max.x - window.dc.cursorPos.x, table.rowPosY2 - table.rowPosY1)
            if (size.x > 0f && size.y > 0f) { // TODO glm
                invisibleButton("##RemainingSpace", size)
                window.dc.cursorPos.y -= style.itemSpacing.y
                window.dc.cursorMaxPos = backupCursorMaxPos    // Don't feed back into the width of the Header row

                // We don't use BeginPopupContextItem() because we want the popup to stay up even after the column is hidden.
                if (isMouseReleased(MouseButton.Left) && isItemHovered(HoveredFlag.AllowWhenBlockedByPopup))
                    openContextPopup = -1
            }

            window.clipRect put window.drawList._clipRectStack.last()
        }

        // Open Context Menu
        if (openContextPopup != Int.MAX_VALUE)
            if (table.flags has (Tf.Resizable or Tf.Reorderable or Tf.Hideable)) {
                table.isContextPopupOpen = true
                table.contextPopupColumn = openContextPopup
                table.instanceInteracted = table.instanceCurrent
                openPopup("##TableContextMenu")
            }
    }

    /** submit one header cell manually.
     *
     *  Emit a column header (text + optional sort order)
     *  We cpu-clip text here so that all columns headers can be merged into a same draw call.
     *  Note that because of how we cpu-clip and display sorting indicators, you _cannot_ use SameLine() after a TableHeader()
     *  FIXME-TABLE: Should hold a selection state.
     *  FIXME-TABLE: Style confusion between CellPadding.y and FramePadding.y */
    fun tableHeader(label_: String = "") {

        val window = g.currentWindow!!
        if (window.skipItems)
            return

        val table = g.currentTable ?: error("Need to call TableAutoHeaders() after BeginTable()!")
        val columnN = table.currentColumn
        assert(columnN != -1)
        val column = table.columns[columnN]!!

        // Label
        val label = label_.toByteArray()
        val labelEnd = findRenderedTextEnd(label)
        val labelSize = calcTextSize(label, 0, labelEnd, true)
        val labelPos = Vec2(window.dc.cursorPos)

        // If we already got a row height, there's use that.
        val cellR = tableGetCellRect()
        val labelHeight = labelSize.y max (table.rowMinHeight - style.cellPadding.y * 2f)

        //GetForegroundDrawList()->AddRect(cell_r.Min, cell_r.Max, IM_COL32(255, 0, 0, 255)); // [DEBUG]
        val workR = Rect(cellR)
        workR.min.x = window.dc.cursorPos.x
        workR.max.y = workR.min.y + labelHeight
        var ellipsisMax = workR.max.x

        // Selectable
        pushID(label_)

        // FIXME-TABLE: Fix when padding are disabled.
        //window->DC.CursorPos.x = column->MinX + table->CellPadding.x;

        // Keep header highlighted when context menu is open.
        // (FIXME-TABLE: however we cannot assume the ID of said popup if it has been created by the user...)
        val selected = table.isContextPopupOpen && table.contextPopupColumn == columnN && table.instanceInteracted == table.instanceCurrent
        val pressed = selectable("", selected, SelectableFlag._DrawHoveredWhenHeld or SelectableFlag.DontClosePopups, Vec2(0f, labelHeight))
        val held = isItemActive
        if (held)
            table.heldHeaderColumn = columnN
        window.dc.cursorPos.y -= style.itemSpacing.y * 0.5f

        // Drag and drop to re-order columns.
        // FIXME-TABLE: Scroll request while reordering a column and it lands out of the scrolling zone.
        if (held && table.flags has Tf.Reorderable && isMouseDragging(MouseButton.Left) && !g.dragDropActive) {
            // While moving a column it will jump on the other side of the mouse, so we also test for MouseDelta.x
            table.reorderColumn = columnN
            table.instanceInteracted = table.instanceCurrent

            // We don't reorder: through the frozen<>unfrozen line, or through a column that is marked with ImGuiTableColumnFlags_NoReorder.
            if (io.mouseDelta.x < 0f && io.mousePos.x < cellR.min.x)
                table.columns.getOrNull(column.prevVisibleColumn)?.let { prevColumn ->
                    if ((column.flags or prevColumn.flags) hasnt Tcf.NoReorder)
                        if (column.indexWithinVisibleSet < table.freezeColumnsRequest == prevColumn.indexWithinVisibleSet < table.freezeColumnsRequest)
                            table.reorderColumnDir = -1
                }
            if (io.mouseDelta.x > 0f && io.mousePos.x > cellR.max.x)
                table.columns.getOrNull(column.nextVisibleColumn)?.let { nextColumn ->
                    if ((column.flags or nextColumn.flags) hasnt Tcf.NoReorder)
                        if (column.indexWithinVisibleSet < table.freezeColumnsRequest == nextColumn.indexWithinVisibleSet < table.freezeColumnsRequest)
                            table.reorderColumnDir = +1
                }
        }

        // Sort order arrow
        var wArrow = 0f
        var wSortText = 0f
        if (table.flags has Tf.Sortable && column.flags hasnt Tcf.NoSort) {
            val ARROW_SCALE = 0.65f
            wArrow = floor(g.fontSize * ARROW_SCALE + style.framePadding.x)// table->CellPadding.x);
            if (column.sortOrder != -1) {

                wSortText = 0f

                var sortOrderSuf: String? = null
                if (column.sortOrder > 0) {
                    sortOrderSuf = "${column.sortOrder + 1}"
                    wSortText = style.itemInnerSpacing.x + calcTextSize(sortOrderSuf).x
                }

                var x = cellR.min.x max (workR.max.x - wArrow - wSortText)
                ellipsisMax -= wArrow + wSortText

                val y = labelPos.y
                val col = Col.Text.u32
                if (column.sortOrder > 0) {
                    pushStyleColor(Col.Text, getColorU32(Col.Text, 0.7f))
                    renderText(Vec2(x + style.itemInnerSpacing.x, y), sortOrderSuf!!)
                    popStyleColor()
                    x += wSortText
                }
                window.drawList.renderArrow(Vec2(x, y), col, if (column.sortDirection == SortDirection.Ascending) Dir.Up else Dir.Down, ARROW_SCALE)
            }

            if(pressed)
                println()
            // Handle clicking on column header to adjust Sort Order
            if (pressed && table.reorderColumn != columnN)
                tableSortSpecsClickColumn(table, column, io.keyShift)
        }

        // Render clipped label. Clipping here ensure that in the majority of situations, all our header cells will
        // be merged into a single draw call.
        //window->DrawList->AddCircleFilled(ImVec2(ellipsis_max, label_pos.y), 40, IM_COL32_WHITE);
        renderTextEllipsis(window.drawList, labelPos, Vec2(ellipsisMax, labelPos.y + labelHeight + style.framePadding.y), ellipsisMax, ellipsisMax, label, labelEnd, labelSize)

        // We feed our unclipped width to the column without writing on CursorMaxPos, so that column is still considering
        // for merging.
        // FIXME-TABLE: Clarify policies of how label width and potential decorations (arrows) fit into auto-resize of the column
        val maxPosX = labelPos.x + labelSize.x + wSortText + wArrow
        column.contentMaxPosHeadersUsed = column.contentMaxPosHeadersUsed max workR.max.x// ImMin(max_pos_x, work_r.Max.x));
        column.contentMaxPosHeadersIdeal = column.contentMaxPosHeadersIdeal max maxPosX

        popID()
    }


    /** Tables: Sorting
     *  - Call TableGetSortSpecs() to retrieve latest sort specs for the table. Return value will be NULL if no sorting.
     *  - You can sort your data again when 'SpecsChanged == true'. It will be true with sorting specs have changed since last call, or the first time.
     *  - Lifetime: don't hold on this pointer over multiple frames or past any subsequent call to BeginTable()!
     *
     *  Return NULL if no sort specs (most often when ImGuiTableFlags_Sortable is not set)
     *  You can sort your data again when 'SpecsChanged == true'. It will be true with sorting specs have changed since
     *  last call, or the first time.
     *  Lifetime: don't hold on this pointer over multiple frames or past any subsequent call to BeginTable()! */
    fun tableGetSortSpecs(): TableSortSpecs? {  // get latest sort specs for the table (NULL if not sorting).

        val table = g.currentTable!! // IM_ASSERT(table != NULL)

        if (table.flags hasnt Tf.Sortable)
            return null

        // Flatten sort specs into user facing data
        val wasDirty = table.isSortSpecsDirty
        if (wasDirty) {

            tableSortSpecsSanitize(table)

            // Write output
            table.sortSpecsData = Array(table.sortSpecsCount) { TableSortSpecsColumn() }
            table.sortSpecs.columnsMask = 0x00
            for (columnN in 0 until table.columnsCount) {
                val column = table.columns[columnN]!!
                if (column.sortOrder == -1)
                    continue
                val sortSpec = table.sortSpecsData[column.sortOrder]
                sortSpec.columnUserID = column.userID
                sortSpec.columnIndex = columnN
                sortSpec.sortOrder = column.sortOrder
                sortSpec.sortDirection = column.sortDirection
                table.sortSpecs.columnsMask = table.sortSpecs.columnsMask or (1L shl columnN)
            }
        }

        // User facing data
        table.sortSpecs.specs = table.sortSpecsData
        table.sortSpecs.specsChanged = wasDirty
        table.isSortSpecsDirty = false
        return table.sortSpecs
    }
}

// Helper: ImSpan<>
// Pointing to a span of data we don't own.
//template<typename T>
//struct ImSpan
//{
//    T*                  Data;
//    T*                  DataEnd;
//
//    // Constructors, destructor
//    inline ImSpan()                                 { Data = DataEnd = NULL; }
//    inline ImSpan(T* data, int size)                { Data = data; DataEnd = data + size; }
//    inline ImSpan(T* data, T* data_end)             { Data = data; DataEnd = data_end; }
//
//    inline void         set(T* data, int size)      { Data = data; DataEnd = data + size; }
//    inline void         set(T* data, T* data_end)   { Data = data; DataEnd = data_end; }
//    inline int          size() const                { return (int)(ptrdiff_t)(DataEnd - Data); }
//    inline T&           operator[](int i)           { T* p = Data + i; IM_ASSERT(p < DataEnd); return *p; }
//    inline const T&     operator[](int i) const     { const T* p = Data + i; IM_ASSERT(p < DataEnd); return *p; }
//
//    inline T*           begin()                     { return Data; }
//    inline const T*     begin() const               { return Data; }
//    inline T*           end()                       { return DataEnd; }
//    inline const T*     end() const                 { return DataEnd; }
//
//    // Utilities
//    inline int  index_from_ptr(const T* it) const   { IM_ASSERT(it >= Data && it < DataEnd); const ptrdiff_t off = it - Data; return (int)off; }
//};

/** Helper: ImSpanAllocator<>
 *  Facilitate storing multiple chunks into a single large block (the "arena") */
class SpanAllocator(val chunks: Int) {
    //    char*   BasePtr;
    var totalSize = 0
    var currSpan = 0
    val offsets = ArrayList<Any>(chunks)

    //    ImSpanAllocator()
//    { memset(this, 0, sizeof(*this)); }
    fun reserveBytes(n: Int, sz: Int) {
        assert(n == currSpan && n < chunks); offsets[currSpan++] = totalSize; totalSize += sz; }

    val arenaSizeInBytes get() = totalSize
//    inline void  SetArenaBasePtr(void* base_ptr)
//    { BasePtr = (char *) base_ptr; }
//    inline void* GetSpanPtrBegin(int n)
//    { IM_ASSERT(n >= 0 && n < CHUNKS && CurrSpan == CHUNKS); return (void *)(BasePtr + Offsets[n]); }
//    inline void* GetSpanPtrEnd(int n)
//    { IM_ASSERT(n >= 0 && n < CHUNKS && CurrSpan == CHUNKS); return (n + 1 < CHUNKS) ? BasePtr+Offsets[n+1] : (void*)(BasePtr+TotalSize); }
//    template<typename T>
//    inline void  GetSpan(int n, ImSpan<T>* span)
//    { span->set((T*)GetSpanPtrBegin(n), (T*)GetSpanPtrEnd(n)); }

    operator fun set(index: Int, any: Any) {
        offsets[index] = any
    }
}