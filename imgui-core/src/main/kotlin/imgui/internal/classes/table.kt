package imgui.internal.classes

import glm_.L
import glm_.b
import glm_.i
import glm_.s
import glm_.vec2.Vec2
import imgui.*
import imgui.classes.TableSortSpecs
import imgui.classes.TableSortSpecsColumn
import imgui.internal.DrawListSplitter

/** Special sentinel code */
val COL32_DISABLE = COL32(0, 0, 0, 1)

/** sizeof(ImU64) * 8. This is solely because we frequently encode columns set in a ImU64. */
val TABLE_MAX_COLUMNS = 64

/** Storage for a table */
class Table {

    var id: ID = 0
    var flags = TableFlag.None.i
//    val rawData = BitSet()

    /** Point within RawData[] */
    var columns = Array<TableColumn?>(0) { null }

    /** Point within RawData[]. Store display order of columns (when not reordered, the values are 0...Count-1) */
    var displayOrderToIndex = ByteArray(0)

    /** Column Index -> IsVisible map (== not hidden by user/api) in a format adequate for iterating column without touching cold data */
    var visibleMaskByIndex = 0L

    /** Column DisplayOrder -> IsVisible map */
    var visibleMaskByDisplayOrder = 0L

    /** Visible and not Clipped, aka "actually visible" "not hidden by some scrolling" */
    var visibleUnclippedMaskByIndex = 0L

    /** Which data were loaded from the .ini file (e.g. when order is not altered we won't save order) */
    var settingsLoadedFlags = TableFlag.None.i

    /** Offset in g.SettingsTables */
    var settingsOffset = 0
    var lastFrameActive = 0

    /** Number of columns declared in BeginTable() */
    var columnsCount = 0

    /** Number of non-hidden columns (<= ColumnsCount) */
    var columnsVisibleCount = 0

    var currentColumn = 0

    var currentRow = 0

    /*
    [0] =   ImS16                   InstanceCurrent
            ImS16                   InstanceInteracted
            S16                     RowFlags
            S16                     LastRowFlags

    [1] =   ImS8                    SortSpecsCount
            ImS8                    DeclColumnsCount
            ImS8                    HoveredColumnBody
            ImS8                    HoveredColumnBorder
            ImS8                    ResizedColumn
            ImS8                    LastResizedColumn
            ImS8                    HeldHeaderColumn
            ImS8                    ReorderColumn

    [2] =   ImS8                        ReorderColumnDir
            ImS8                        RightMostVisibleColumn
            ImS8                        LeftMostStretchedColumnDisplayOrder
            ImS8                        ContextPopupColumn
            ImS8                        DummyDrawChannel
            ImS8                        FreezeRowsRequest
            ImS8                        FreezeRowsCount
            ImS8                        FreezeColumnsRequest

    [3] =   ImS8                        FreezeColumnsCount
            bool                        IsLayoutLocked
            bool                        IsInsideRow
            bool                        IsInitializing
            bool                        IsSortSpecsDirty
            bool                        IsUsingHeaders
            bool                        IsContextPopupOpen
            bool                        IsSettingsRequestLoad
            bool                        IsSettingsDirty
            bool                        IsDefaultDisplayOrder
            bool                        IsResetDisplayOrderRequest
            bool                        IsFreezeRowsPassed
            bool                        HostSkipItems
     */
    private val longs = LongArray(4)

    /** Count of BeginTable() calls with same ID in the same frame (generally 0). This is a little bit similar to BeginCount for a window, but multiple table with same ID look are multiple tables, they are just synched. */
    var instanceCurrent: Int
        get() = (longs[0] shr 48).s.i
        set(value) {
            longs[0] = (longs[0] and 0x0000_ffff_ffff_ffff) or (value.L shl 48)
        }

    /** Mark which instance (generally 0) of the same ID is being interacted with */
    var instanceInteracted: Int
        get() = (longs[0] shr 32).s.i
        set(value) {
            longs[0] = (longs[0] and 0xffff_0000_ffff_ffffUL) or ((value.L and 0xffff) shl 32)
        }
    var rowPosY1 = 0f
    var rowPosY2 = 0f

    /** Height submitted to TableNextRow() */
    var rowMinHeight = 0f
    var rowTextBaseline = 0f
    var rowIndentOffsetX = 0f

    /** Current row flags, see ImGuiTableRowFlags_ */
    var rowFlags: TableRowFlags
        get() = (longs[0] shr 16).s.i
        set(value) {
            longs[0] = (longs[0] and 0xffff_ffff_0000_ffffUL) or ((value.L and 0xffff) shl 16)
        }
    var lastRowFlags: TableRowFlags
        get() = longs[0].s.i
        set(value) {
            longs[0] = (longs[0] and 0xffff_ffff_ffff_0000UL) or (value.L and 0xffff)
        }

    /** Counter for alternating background colors (can be fast-forwarded by e.g clipper) */
    var rowBgColorCounter = 0

    /** Request for current row background color */
    var rowBgColor = 0//u
    var borderColorStrong = 0//u
    var borderColorLight = 0//u
    var borderX1 = 0f
    var borderX2 = 0f
    var hostIndentX = 0f

    /** Padding from each borders */

    var cellPaddingX1 = 0f
    var cellPaddingX2 = 0f
    var cellPaddingY = 0f

    /** Spacing between non-bordered cells */
    var cellSpacingX = 0f

    /** Outer height from last frame */
    var lastOuterHeight = 0f

    /** Height of first row from last frame */
    var lastFirstRowHeight = 0f

    /** User value passed to BeginTable(), see comments at the top of BeginTable() for details. */
    var innerWidth = 0f

    /** Sum of current column width */
    var columnsTotalWidth = 0f
    /** Sum of ideal column width in order nothing to be clipped, used for auto-fitting and content width submission in outer window */
    var columnsAutoFitWidth = 0f

    var resizedColumnNextWidth = 0f

    // Note: OuterRect.Max.y is often FLT_MAX until EndTable(), unless a height has been specified in BeginTable().
    val outerRect = Rect()
    val workRect = Rect()
    val innerClipRect = Rect()

    /**  We use this to cpu-clip cell background color fill */
    val backgroundClipRect = Rect()

    /**  This is used to check if we can eventually merge our columns draw calls into the current draw call of the current window. */
    val hostClipRect = Rect()

    /**  Backup of InnerWindow->WorkRect at the end of BeginTable() */
    val hostWorkRect = Rect()

    /** Backup of InnerWindow->DC.CursorMaxPos at the end of BeginTable() */
    val hostCursorMaxPos = Vec2()

    /** Parent window for the table */
    var outerWindow: Window? = null

    /** Window holding the table data (== OuterWindow or a child window) */
    var innerWindow: Window? = null

    /** Contiguous buffer holding columns names */
    val columnsNames = ArrayList<String>()

    /** We carry our own ImDrawList splitter to allow recursion (FIXME: could be stored outside, worst case we need 1 splitter per recursing table) */
    val drawSplitter = DrawListSplitter()

    /** FIXME-OPT: Fixed-size array / small-vector pattern, optimize for single sort spec */
    lateinit var sortSpecsData: Array<TableSortSpecsColumn>

    /** Public facing sorts specs, this is what we return in TableGetSortSpecs() */
    val sortSpecs = TableSortSpecs()

    var sortSpecsCount: Int
        get() = (longs[1] shr 56).b.i
        set(value) {
            longs[1] = (longs[1] and 0x00ff_ffff_ffff_ffff) or (value.L shl 56)
        }

    /** Count calls to TableSetupColumn() */
    var declColumnsCount: Int
        get() = (longs[1] shr 48).b.i
        set(value) {
            longs[1] = (longs[1] and 0xff00_ffff_ffff_ffffUL) or ((value.L and 0xff) shl 48)
        }

    /** [DEBUG] Unlike HoveredColumnBorder this doesn't fulfill all Hovering rules properly. Used for debugging/tools for now. */
    var hoveredColumnBody: Int
        get() = (longs[1] shr 40).b.i
        set(value) {
            longs[1] = (longs[1] and 0xffff_00ff_ffff_ffffUL) or ((value.L and 0xff) shl 40)
        }

    /** Index of column whose right-border is being hovered (for resizing). */
    var hoveredColumnBorder: Int
        get() = (longs[1] shr 32).b.i
        set(value) {
            longs[1] = (longs[1] and 0xffff_ff00_ffff_ffffUL) or ((value.L and 0xff) shl 32)
        }

    /** Index of column being resized. Reset when InstanceCurrent==0. */
    var resizedColumn: Int
        get() = (longs[1] shr 24).b.i
        set(value) {
            longs[1] = (longs[1] and 0xffff_ffff_00ff_ffffUL) or ((value.L and 0xff) shl 24)
        }

    /** Index of column being resized from previous frame. */
    var lastResizedColumn: Int
        get() = (longs[1] shr 16).b.i
        set(value) {
            longs[1] = (longs[1] and 0xffff_ffff_ff00_ffffUL) or ((value.L and 0xff) shl 16)
        }

    /** Index of column header being held. */
    var heldHeaderColumn: Int
        get() = (longs[1] shr 8).b.i
        set(value) {
            longs[1] = (longs[1] and 0xffff_ffff_ffff_00ffUL) or ((value.L and 0xff) shl 8)
        }

    // Index of column being reordered. (not cleared)
    var reorderColumn: Int
        get() = longs[1].b.i
        set(value) {
            longs[1] = (longs[1] and 0xffff_ffff_ffff_ff00UL) or (value.L and 0xff)
        }

    /** -1 or +1 */
    var reorderColumnDir: Int
        get() = (longs[2] shr 56).b.i
        set(value) {
            longs[2] = (longs[2] and 0x00ff_ffff_ffff_ffff) or (value.L shl 56)
        }

    /** Index of right-most non-hidden column. */
    var rightMostVisibleColumn: Int
        get() = (longs[2] shr 48).b.i
        set(value) {
            longs[2] = (longs[2] and 0xff00_ffff_ffff_ffffUL) or ((value.L and 0xff) shl 48)
        }

    /** Display order of left-most stretched column. */
    var leftMostStretchedColumnDisplayOrder: Int
        get() = (longs[2] shr 40).b.i
        set(value) {
            longs[2] = (longs[2] and 0xffff_00ff_ffff_ffffUL) or ((value.L and 0xff) shl 40)
        }

    /** Column right-clicked on, of -1 if opening context menu from a neutral/empty spot */
    var contextPopupColumn: Int
        get() = (longs[2] shr 32).b.i
        set(value) {
            longs[2] = (longs[2] and 0xffff_ff00_ffff_ffffUL) or ((value.L and 0xff) shl 32)
        }

    /** Redirect non-visible columns here. */
    var dummyDrawChannel: Int
        get() = (longs[2] shr 24).b.i
        set(value) {
            longs[2] = (longs[2] and 0xffff_ffff_00ff_ffffUL) or ((value.L and 0xff) shl 24)
        }

    /** Requested frozen rows count */
    var freezeRowsRequest: Int
        get() = (longs[2] shr 16).b.i
        set(value) {
            longs[2] = (longs[2] and 0xffff_ffff_ff00_ffffUL) or ((value.L and 0xff) shl 16)
        }

    /** Actual frozen row count (== FreezeRowsRequest, or == 0 when no scrolling offset) */
    var freezeRowsCount: Int
        get() = (longs[2] shr 8).b.i
        set(value) {
            longs[2] = (longs[2] and 0xffff_ffff_ffff_00ffUL) or ((value.L and 0xff) shl 8)
        }

    /** Requested frozen columns count */
    var freezeColumnsRequest: Int
        get() = longs[2].b.i
        set(value) {
            longs[2] = (longs[2] and 0xffff_ffff_ffff_ff00UL) or (value.L and 0xff)
        }

    /** Actual frozen columns count (== FreezeColumnsRequest, or == 0 when no scrolling offset) */
    var freezeColumnsCount: Int
        get() = (longs[3] shr 56).b.i
        set(value) {
            longs[3] = (longs[3] and 0x00ff_ffff_ffff_ffff) or (value.L shl 56)
        }

    /** Set by TableUpdateLayout() which is called when beginning the first row. */
    var isLayoutLocked: Boolean
        get() {
            val b = 1L shl 55
            return (longs[3] and b) == b
        }
        set(value) {
            val b = 1L shl 55
            longs[3] = when {
                value -> longs[3] or b
                else -> longs[3] and b.inv()
            }
        }

    /** Set when inside TableBeginRow()/TableEndRow(). */
    var isInsideRow: Boolean
        get() {
            val b = 1L shl 54
            return (longs[3] and b) == b
        }
        set(value) {
            val b = 1L shl 54
            longs[3] = when {
                value -> longs[3] or b
                else -> longs[3] and b.inv()
            }
        }
    var isInitializing: Boolean
        get() {
            val b = 1L shl 53
            return (longs[3] and b) == b
        }
        set(value) {
            val b = 1L shl 53
            longs[3] = when {
                value -> longs[3] or b
                else -> longs[3] and b.inv()
            }
        }
    var isSortSpecsDirty: Boolean
        get() {
            val b = 1L shl 52
            return (longs[3] and b) == b
        }
        set(value) {
            val b = 1L shl 52
            longs[3] = when {
                value -> longs[3] or b
                else -> longs[3] and b.inv()
            }
        }

    /** Set when the first row had the ImGuiTableRowFlags_Headers flag. */
    var isUsingHeaders: Boolean
        get() {
            val b = 1L shl 51
            return (longs[3] and b) == b
        }
        set(value) {
            val b = 1L shl 51
            longs[3] = when {
                value -> longs[3] or b
                else -> longs[3] and b.inv()
            }
        }

    /** Set when default context menu is open (also see: ContextPopupColumn, InstanceInteracted). */
    var isContextPopupOpen: Boolean
        get() {
            val b = 1L shl 50
            return (longs[3] and b) == b
        }
        set(value) {
            val b = 1L shl 50
            longs[3] = when {
                value -> longs[3] or b
                else -> longs[3] and b.inv()
            }
        }

    var isSettingsRequestLoad: Boolean
        get() {
            val b = 1L shl 49
            return (longs[3] and b) == b
        }
        set(value) {
            val b = 1L shl 49
            longs[3] = when {
                value -> longs[3] or b
                else -> longs[3] and b.inv()
            }
        }

    /** Set when table settings have changed and needs to be reported into ImGuiTableSetttings data. */
    var isSettingsDirty: Boolean
        get() {
            val b = 1L shl 48
            return (longs[3] and b) == b
        }
        set(value) {
            val b = 1L shl 48
            longs[3] = when {
                value -> longs[3] or b
                else -> longs[3] and b.inv()
            }
        }

    /** Set when display order is unchanged from default (DisplayOrder contains 0...Count-1) */
    var isDefaultDisplayOrder: Boolean
        get() {
            val b = 1L shl 47
            return (longs[3] and b) == b
        }
        set(value) {
            val b = 1L shl 47
            longs[3] = when {
                value -> longs[3] or b
                else -> longs[3] and b.inv()
            }
        }

    var isResetDisplayOrderRequest: Boolean
        get() {
            val b = 1L shl 46
            return (longs[3] and b) == b
        }
        set(value) {
            val b = 1L shl 46
            longs[3] = when {
                value -> longs[3] or b
                else -> longs[3] and b.inv()
            }
        }

    /** Set when we got past the frozen row (the first one). */
    var isFreezeRowsPassed: Boolean
        get() {
            val b = 1L shl 45
            return (longs[3] and b) == b
        }
        set(value) {
            val b = 1L shl 45
            longs[3] = when {
                value -> longs[3] or b
                else -> longs[3] and b.inv()
            }
        }

    /** Backup of InnerWindow->SkipItem at the end of BeginTable(), because we will overwrite InnerWindow->SkipItem on a per-column basis */
    var hostSkipItems: Boolean
        get() {
            val b = 1L shl 44
            return (longs[3] and b) == b
        }
        set(value) {
            val b = 1L shl 44
            longs[3] = when {
                value -> longs[3] or b
                else -> longs[3] and b.inv()
            }
        }

    init {
        settingsOffset = -1
        instanceInteracted = -1
        lastFrameActive = -1
        lastResizedColumn = -1
        contextPopupColumn = -1
        reorderColumn = -1
        resizedColumn = -1
    }

    override fun toString(): String = """
        instanceCurrent=$instanceCurrent
        instanceInteracted=$instanceInteracted
        rowFlags=$rowFlags
        lastRowFlags=$lastRowFlags
        sortSpecsCount=$sortSpecsCount
        declColumnsCount=$declColumnsCount
        hoveredColumnBody=$hoveredColumnBody
        hoveredColumnBorder=$hoveredColumnBorder
        resizedColumn=$resizedColumn
        lastResizedColumn=$lastResizedColumn
        heldHeaderColumn=$heldHeaderColumn
        reorderColumn=$reorderColumn
        reorderColumnDir=$reorderColumnDir
        rightMostVisibleColumn=$rightMostVisibleColumn
        leftMostStretchedColumnDisplayOrder=$leftMostStretchedColumnDisplayOrder
        contextPopupColumn=$contextPopupColumn
        dummyDrawChannel=$dummyDrawChannel
        freezeRowsRequest=$freezeRowsRequest
        freezeRowsCount=$freezeRowsCount
        freezeColumnsRequest=$freezeColumnsRequest
        freezeColumnsCount=$freezeColumnsCount
        isLayoutLocked=$isLayoutLocked
        isInsideRow=$isInsideRow
        isInitializing=$isInitializing
        isSortSpecsDirty=$isSortSpecsDirty
        isUsingHeaders=$isUsingHeaders
        isContextPopupOpen=$isContextPopupOpen
        isSettingsRequestLoad=$isSettingsRequestLoad
        isSettingsDirty=$isSettingsDirty
        isDefaultDisplayOrder=$isDefaultDisplayOrder
        isResetDisplayOrderRequest=$isResetDisplayOrderRequest
        isFreezeRowsPassed=$isFreezeRowsPassed
        hostSkipItems =$hostSkipItems""".trimIndent()
}

/** sizeof() ~ 12 */
class TableColumnSettings {
    var widthOrWeight = 0f
    var userID: ID = 0

    /*
        ImS8    Index
        ImS8    DisplayOrder
        ImS8    SortOrder
        ImU8    SortDirection : 2
        ImU8    Visible : 1
     */
    private var int = 0
    var index: Int
        get() = (int shr 24).b.i
        set(value) {
            int = (int and 0x00ff_ffff) or (value shl 24)
        }
    var displayOrder: Int
        get() = (int shr 16).b.i
        set(value) {
            int = (int and 0xff00_ffff.i) or ((value and 0xff) shl 16)
        }
    var sortOrder: Int
        get() = (int shr 8).b.i
        set(value) {
            int = (int and 0xffff_00ff.i) or ((value and 0xff) shl 8)
        }
    var sortDirection: SortDirection
        get() = SortDirection.values()[((int shr 6) and 0b0000_0011).b.i]
        set(value) {
            int = (int and 0b11111111_11111111_11111111_00000001.i) or ((value.ordinal shl 6) and 0b1100_0000)
        }

    var visible: Boolean
        get() = (int and 1) == 1
        set(value) {
            int = when {
                value -> int or 1
                else -> int and 0b1111_1111_1111_1110
            }
        }

    init {
        index = -1
        displayOrder = -1
        sortOrder = -1
        sortDirection = SortDirection.None
        visible = true
    }

    override fun toString(): String = """
        index=$index
        displayOrder=$displayOrder
        sortOrder=$sortOrder
        sortDirection=$sortDirection
        visible=$visible
    """.trimIndent()
}

/** See TableUpdateDrawChannels() */
val TABLE_MAX_DRAW_CHANNELS = 2 + 64 * 2


/** Storage for one column of a table
 *
 *  [Internal] sizeof() ~ 100
 *  We use the terminology "Visible" to refer to a column that is not Hidden by user or settings. However it may still be out of view and clipped (see IsClipped). */
class TableColumn {

    /** Clipping rectangle for the column */
    val clipRect = Rect()

    // Optional, value passed to TableSetupColumn()
    var userID: ID = 0

    /** Flags as they were provided by user. See ImGuiTableColumnFlags_ */
    var flagsIn = TableColumnFlag.None.i

    // Effective flags. See ImGuiTableColumnFlags_
    var flags = TableColumnFlag.None.i

    /** Absolute positions */
    var minX = 0f

    /** Absolute positions */
    var maxX = 0f

    /**  Master width weight when (Flags & _WidthStretch). Often around ~1.0f initially. */
    var widthStretchWeight = -1f

    /** Master width absolute value when !(Flags & _WidthStretch). When Stretch this is derived every frame from WidthStretchWeight in TableUpdateLayout() */
    var widthRequest = -1f

    /** Final/actual width visible == (MaxX - MinX), locked in TableUpdateLayout(). May be >WidthRequest to honor minimum width, may be <WidthRequest to honor shrinking columns down in tight space. */
    var widthGiven = -1f

    /** Start position for the frame, currently ~(MinX + CellPaddingX) */
    var startXRows = 0f

    var startXHeaders = 0f

    /** Submitted contents absolute maximum position, from which we can infer width. */
    var contentMaxPosRowsFrozen = 0f

    /** (kept as float because we need to manipulate those between each cell change)*/
    var contentMaxPosRowsUnfrozen = 0f
    var contentMaxPosHeadersUsed = 0f
    var contentMaxPosHeadersIdeal = 0f

    /*
    [0] =   ImS16                   ContentWidthRowsFrozen
            ImS16                   ContentWidthRowsUnfrozen
            ImS16                   ContentWidthHeadersUsed
            ImS16                   ContentWidthHeadersIdeal

    [1] =   ImS16                   NameOffset
            bool                    IsVisible
            bool                    IsVisibleNextFrame
            bool                    IsClipped
            bool                    SkipItems
            ImS8                    DisplayOrder
            ImS8                    IndexWithinVisibleSet
            ImS8                    DrawChannelCurrent
            ImS8                    DrawChannelRowsBeforeFreeze
            ImS8                    DrawChannelRowsAfterFreeze

    [2] =   ImS8                    PrevVisibleColumn
            ImS8                    NextVisibleColumn
            ImS8                    AutoFitQueue
            ImS8                    CannotSkipItemsQueue
            ImS8                    SortOrder
            ImS8                    SortDirection
     */
    private val longs = LongArray(3)

    /** Contents width. Because row freezing is not correlated with headers/not-headers we need all 4 variants (ImDrawCmd merging uses different data than alignment code). */
    var contentWidthRowsFrozen: Int
        get() = (longs[0] shr 48).s.i
        set(value) {
            longs[0] = (longs[0] and 0x0000_ffff_ffff_ffff) or (value.L shl 48)
        }

    /** (encoded as ImS16 because we actually rarely use those width) */
    var contentWidthRowsUnfrozen: Int
        get() = (longs[0] shr 32).s.i
        set(value) {
            longs[0] = (longs[0] and 0xffff_0000_ffff_ffffUL) or ((value.L and 0xffff) shl 32)
        }

    /** TableHeader() automatically softclip itself + report ideal desired size, to avoid creating extraneous draw calls */
    var contentWidthHeadersUsed: Int
        get() = (longs[0] shr 16).s.i
        set(value) {
            longs[0] = (longs[0] and 0xffff_ffff_0000_ffffUL) or ((value.L and 0xffff) shl 16)
        }

    var contentWidthHeadersIdeal: Int
        get() = longs[0].s.i
        set(value) {
            longs[0] = (longs[0] and 0xffff_ffff_ffff_0000UL) or (value.L and 0xffff)
        }

    /** Offset into parent ColumnsNames[] */
    var nameOffset: Int
        get() = (longs[1] shr 48).s.i
        set(value) {
            longs[1] = (longs[1] and 0x0000_ffff_ffff_ffff) or (value.L shl 48)
        }

    /** Is the column not marked Hidden by the user? (could be clipped by scrolling, etc). */
    var isVisible: Boolean
        get() {
            val b = 1L shl 47
            return (longs[1] and b) == b
        }
        set(value) {
            val b = 1L shl 47
            longs[1] = when {
                value -> longs[1] or b
                else -> longs[1] and b.inv()
            }
        }

    var isVisibleNextFrame: Boolean
        get() {
            val b = 1L shl 46
            return (longs[1] and b) == b
        }
        set(value) {
            val b = 1L shl 46
            longs[1] = when {
                value -> longs[1] or b
                else -> longs[1] and b.inv()
            }
        }

    /** Set when not overlapping the host window clipping rectangle. */
    var isClipped: Boolean
        get() {
            val b = 1L shl 45
            return (longs[1] and b) == b
        }
        set(value) {
            val b = 1L shl 45
            longs[1] = when {
                value -> longs[1] or b
                else -> longs[1] and b.inv()
            }
        }

    var skipItems: Boolean
        get() {
            val b = 1L shl 44
            return (longs[1] and b) == b
        }
        set(value) {
            val b = 1L shl 44
            longs[1] = when {
                value -> longs[1] or b
                else -> longs[1] and b.inv()
            }
        }

    /** Index within Table's IndexToDisplayOrder[] (column may be reordered by users) */
    var displayOrder: Int
        get() = (longs[1] shr 32).b.i
        set(value) {
            longs[1] = (longs[1] and 0xffff_ff00_ffff_ffffUL) or ((value.L and 0xff) shl 32)
        }

    /** Index within visible set (<= IndexToDisplayOrder) */
    var indexWithinVisibleSet: Int
        get() = (longs[1] shr 24).b.i
        set(value) {
            longs[1] = (longs[1] and 0xffff_ffff_00ff_ffffUL) or ((value.L and 0xff) shl 24)
        }

    /** Index within DrawSplitter.Channels[] */
    var drawChannelCurrent: Int
        get() = (longs[1] shr 16).b.i
        set(value) {
            longs[1] = (longs[1] and 0xffff_ffff_ff00_ffffUL) or ((value.L and 0xff) shl 16)
        }

    var drawChannelRowsBeforeFreeze: Int
        get() = (longs[1] shr 8).b.i
        set(value) {
            longs[1] = (longs[1] and 0xffff_ffff_ffff_00ffUL) or ((value.L and 0xff) shl 8)
        }
    var drawChannelRowsAfterFreeze: Int
        get() = longs[1].b.i
        set(value) {
            longs[1] = (longs[1] and 0xffff_ffff_ffff_ff00UL) or (value.L and 0xff)
        }

    /** Index of prev visible column within Columns[], -1 if first visible column */
    var prevVisibleColumn: Int
        get() = (longs[2] shr 56).b.i
        set(value) {
            longs[2] = (longs[2] and 0x00ff_ffff_ffff_ffff) or (value.L shl 56)
        }

    /** Index of next visible column within Columns[], -1 if last visible column */
    var nextVisibleColumn: Int
        get() = (longs[2] shr 48).b.i
        set(value) {
            longs[2] = (longs[2] and 0xff00_ffff_ffff_ffffUL) or ((value.L and 0xff) shl 48)
        }

    /** Queue of 8 values for the next 8 frames to request auto-fit */
    var autoFitQueue: Int
        get() = (longs[2] shr 40).b.i
        set(value) {
            longs[2] = (longs[2] and 0xffff_00ff_ffff_ffffUL) or ((value.L and 0xff) shl 40)
        }

    /** Queue of 8 values for the next 8 frames to disable Clipped/SkipItem */
    var cannotSkipItemsQueue: Int
        get() = (longs[2] shr 32).b.i
        set(value) {
            longs[2] = (longs[2] and 0xffff_ff00_ffff_ffffUL) or ((value.L and 0xff) shl 32)
        }

    /** -1: Not sorting on this column */
    var sortOrder: Int
        get() = (longs[2] shr 24).b.i
        set(value) {
            longs[2] = (longs[2] and 0xffff_ffff_00ff_ffffUL) or ((value.L and 0xff) shl 24)
        }

    /** enum ImGuiSortDirection_ */
    var sortDirection: SortDirection
        get() = SortDirection.values()[(longs[2] shr 16).b.i]
        set(value) {
            longs[2] = (longs[2] and 0xffff_ffff_ff00_ffffUL) or ((value.ordinal.L and 0xff) shl 16)
        }

    init {
        nameOffset = -1
        isVisible = true
        isVisibleNextFrame = true
        displayOrder = -1
        indexWithinVisibleSet = -1
        drawChannelCurrent = -1
        drawChannelRowsBeforeFreeze = -1
        drawChannelRowsAfterFreeze = -1
        prevVisibleColumn = -1
        nextVisibleColumn = -1
        autoFitQueue = (1 shl 3) - 1 // Skip for three frames
        cannotSkipItemsQueue = (1 shl 3) - 1 // Skip for three frames
        sortOrder = -1
        sortDirection = SortDirection.Ascending
    }

    override fun toString(): String = """
        contentWidthRowsFrozen=$contentWidthRowsFrozen
        contentWidthRowsUnfrozen=$contentWidthRowsUnfrozen
        contentWidthHeadersUsed=$contentWidthHeadersUsed
        contentWidthHeadersIdeal=$contentWidthHeadersIdeal
        nameOffset=$nameOffset
        isVisible=$isVisible
        isVisibleNextFrame=$isVisibleNextFrame
        isClipped=$isClipped
        skipItems=$skipItems
        displayOrder=$displayOrder
        indexWithinVisibleSet=$indexWithinVisibleSet
        drawChannelCurrent=$drawChannelCurrent
        drawChannelRowsBeforeFreeze=$drawChannelRowsBeforeFreeze
        drawChannelRowsAfterFreeze=$drawChannelRowsAfterFreeze
        prevVisibleColumn=$prevVisibleColumn
        nextVisibleColumn=$nextVisibleColumn
        autoFitQueue=$autoFitQueue
        cannotSkipItemsQueue=$cannotSkipItemsQueue
        sortOrder=$sortOrder
        sortDirection=$sortDirection
        """.trimIndent()
}

/** Storage for a table .ini settings
 *
 *  This is designed to be stored in a single ImChunkStream (1 header followed by N ImGuiTableColumnSettings, etc.) */
class TableSettings
/** Clear and initialize empty settings instance
 *  ~initTableSettings */
constructor(
    /** Set to 0 to invalidate/delete the setting */
    var id: ID,
    columnsCount: Int, columnsCountMax: Int) {

    /** Indicate data we want to save using the Resizable/Reorderable/Sortable/Hideable flags (could be using its own flags..) */
    var saveFlags = TableFlag.None.i
    /*
        int =   ImS8                        ColumnsCount
                ImS8                        ColumnsCountMax
                bool                        WantApply
     */
    private var int = 0
    var columnsCount: Int
        get() = (int shr 24).b.i
        set(value) {
            int = (int and 0x00ff_ffff) or (value shl 24)
        }
    /** Maximum number of columns this settings instance can store, we can recycle a settings instance with lower number of columns but not higher */
    var columnsCountMax: Int
        get() = (int shr 16).b.i
        set(value) {
            int = (int and 0xff00_ffff.i) or ((value and 0xff) shl 16)
        }
    /** Set when loaded from .ini data (to enable merging/loading .ini data into an already running context) */
    var wantApply: Boolean
        get() = (int and 1) == 1
        set(value) {
            int = when {
                value -> int or 1
                else -> int and 0b11111111_11111110
            }
        }

    init {
        this.columnsCount = columnsCount
        this.columnsCountMax = columnsCountMax
        wantApply = true
    }

    fun init(id: ID, columnsCount: Int, columnsCountMax: Int): TableSettings {
        this.id = id
        this.columnsCount = columnsCount
        this.columnsCountMax = columnsCountMax
        return this
    }

    //    [JVM] we store here
    var columnSettings = Array(columnsCount) { TableColumnSettings() }

    override fun toString(): String = """
        columnsCount=$columnsCount
        columnsCountMax=$columnsCountMax
    """.trimIndent()
}