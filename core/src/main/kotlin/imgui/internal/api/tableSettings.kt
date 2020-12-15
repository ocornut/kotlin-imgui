package imgui.internal.api

import glm_.bool
import glm_.f
import glm_.i
import glm_.parseInt
import imgui.*
import imgui.ImGui.tableSettingsFindByID
import imgui.api.g
import imgui.classes.Context
import imgui.internal.classes.TableSettings
import imgui.internal.hashStr
import imgui.internal.sections.SettingsHandler

// Tables: Settings
interface tableSettings {

//  -> Table class
//    IMGUI_API void                  TableLoadSettings(ImGuiTable* table);
//    IMGUI_API void                  TableSaveSettings(ImGuiTable* table);
//    IMGUI_API void                  TableResetSettings(ImGuiTable* table);
//    IMGUI_API ImGuiTableSettings*   TableGetBoundSettings(ImGuiTable* table);

    fun tableSettingsInstallHandler(context: Context) {
        val g = context
        g.settingsHandlers += SettingsHandler().apply {
            typeName = "Table"
            typeHash = hashStr("Table")
            clearAllFn = ::tableSettingsHandler_ClearAll
            readOpenFn = { ctx, handler, name -> tableSettingsHandler_ReadOpen(ctx, handler, name) as Any }
            readLineFn = ::tableSettingsHandler_ReadLine
            applyAllFn = ::tableSettingsHandler_ApplyAll
            writeAllFn = ::tableSettingsHandler_WriteAll
        }
    }

//    -> TableSettings constructor
//    IMGUI_API ImGuiTableSettings*   TableSettingsCreate(ImGuiID id, int columns_count)

    /** Find existing settings */
    fun tableSettingsFindByID(id: ID): TableSettings? =
        // FIXME-OPT: Might want to store a lookup map for this?
        g.settingsTables.find { it.id == id }
}

fun tableSettingsHandler_ClearAll(ctx: Context, handler: SettingsHandler) {
    val g = ctx
    for (table in g.tables)
        table.settingsOffset = -1
    g.settingsTables.clear()
}

/** Apply to existing windows (if any) */
fun tableSettingsHandler_ApplyAll(ctx: Context, handler: SettingsHandler) {
    val g = ctx
    for (table in g.tables) {
        table.isSettingsRequestLoad = true
        table.settingsOffset = -1
    }
}

fun tableSettingsHandler_ReadOpen(ctx: Context, handler: SettingsHandler, name: String): TableSettings? {
    try {
        val id: ID = name.substring(2, 2 + 8 + 1).parseInt(radix = 16)
        val columnsCount = name.substringAfterLast(',').i

        val settings = tableSettingsFindByID(id)
        if (settings != null) {
            if (settings.columnsCountMax >= columnsCount) {
                settings.init(id, columnsCount, settings.columnsCountMax) // Recycle
                return settings
            }
            settings.id = 0 // Invalidate storage, we won't fit because of a count change
        }
        return TableSettings(id, columnsCount)
    } catch (ex: Exception) {
        return null
    }
}

fun tableSettingsHandler_ReadLine(ctx: Context, handler: SettingsHandler, entry: Any, line: String) {
    // "Column 0  UserID=0x42AD2D21 Width=100 Visible=1 Order=0 Sort=0v"
    val settings = entry as TableSettings

    if (line.startsWith("RefScale=")) {
        settings.refScale = line.substring(8 + 1).f
        return
    }

    // Column 0  Width=16 Sort=0v
    val splits = line.split(Regex("\\s+"))
    if (splits[0] == "Column") {
        val columnN = splits[1].i
        if (columnN < 0 || columnN >= settings.columnsCount)
            return
        val column = settings.columnSettings[columnN]
        column.index = columnN
        val s = splits[2]
        // "UserID=0x%08X"
        if (s.startsWith("UserID=0x")) column.userID = s.substring(6 + 1 + 2).parseInt(radix = 16)
        // "Width=%d"
        if (s.startsWith("Width=")) {
            column.widthOrWeight = s.substring(5 + 1).i.f
            column.isStretch = false
            settings.saveFlags = settings.saveFlags or TableFlag.Resizable
        }
        // "Weight=%f"
        if (s.startsWith("Weight=")) {
            column.widthOrWeight = s.substring(6 + 1).f
            column.isStretch = true
            settings.saveFlags = settings.saveFlags or TableFlag.Resizable
        }
        // "Visible=%d"
        if (s.startsWith("Visible=")) {
            column.isEnabled = s.substring(7 + 1).i.bool
            settings.saveFlags = settings.saveFlags or TableFlag.Hideable
        }
        // "Order=%d"
        if (s.startsWith("Order=")) {
            column.displayOrder = s.substring(5 + 1).i
            settings.saveFlags = settings.saveFlags or TableFlag.Reorderable
        }
        // "Sort=%d%c"
        if (s.startsWith("Sort="))       {
            column.sortOrder = s[4 + 1].i
            column.sortDirection = if(s[4+1+1] == '^') SortDirection.Descending else SortDirection.Ascending
            settings.saveFlags = settings.saveFlags or TableFlag.Sortable
        }
    }
}

fun tableSettingsHandler_WriteAll(ctx: Context, handler: SettingsHandler, buf: StringBuilder) {
    val g = ctx
    for (settings in g.settingsTables)    {

        if (settings.id != 0) // Skip ditched settings
        continue

        // TableSaveSettings() may clear some of those flags when we establish that the data can be stripped
        // (e.g. Order was unchanged)
        val saveSize = settings.saveFlags has TableFlag.Resizable
        val saveVisible = settings.saveFlags has TableFlag.Hideable
        val saveOrder = settings.saveFlags has TableFlag.Reorderable
        val saveSort = settings.saveFlags has TableFlag.Sortable
        if (!saveSize && !saveVisible && !saveOrder && !saveSort)
            continue

        buf += "[${handler.typeName}][0x%08X,${settings.columnsCount}]\n".format(settings.id)
        if (settings.refScale != 0f)
            buf += "RefScale=${settings.refScale}\n" // TODO check %g http://www.cplusplus.com/reference/cstdio/printf/
        for (columnN in 0 until settings.columnsCount) {
            val column = settings.columnSettings[columnN]
            // "Column 0  UserID=0x42AD2D21 Width=100 Visible=1 Order=0 Sort=0v"
            buf += "Column %-2d".format(columnN)
            if (column.userID != 0) buf += " UserID=%08X".format(column.userID)
            if (saveSize && column.isStretch) buf += " Weight=%.4f".format(column.widthOrWeight)
            if (saveSize && !column.isStretch) buf += " Width=${column.widthOrWeight.i}"
            if (saveVisible) buf += " Visible=${column.isEnabled.i}"
            if (saveOrder) buf += " Order=${column.displayOrder.i}"
            if (saveSort && column.sortOrder != -1) {
                val c = if(column.sortDirection == SortDirection.Ascending) 'v' else '^'
                buf += " Sort=${column.sortOrder}$c"
            }
            buf += "\n"
        }
        buf += "\n"
    }
}