package imgui.classes

import glm_.i
import glm_.max
import imgui.ImGui
import imgui.api.g

/** Helper: Manually clip large list of items.
 *  If you are submitting lots of evenly spaced items and you have a random access to the list, you can perform coarse
 *  clipping based on visibility to save yourself from processing those items at all.
 *  The clipper calculates the range of visible items and advance the cursor to compensate for the non-visible items we
 *  have skipped.
 *  (Dear ImGui already clip items based on their bounds but it needs to measure text size to do so, whereas manual
 *  coarse clipping before submission makes this cost and your own data fetching/submission cost almost null)
 *    ImGuiListClipper clipper;
 *    clipper.Begin(1000);         // We have 1000 elements, evenly spaced.
 *    while (clipper.Step())
 *        for (int i = clipper.DisplayStart; i < clipper.DisplayEnd; i++)
 *            ImGui::Text("line number %d", i);
 *  Generally what happens is:
 *  - Clipper lets you process the first element (DisplayStart = 0, DisplayEnd = 1) regardless of it being visible or not.
 *  - User code submit one element.
 *  - Clipper can measure the height of the first element
 *  - Clipper calculate the actual range of elements to display based on the current clipping rectangle, position the cursor before the first visible element.
 *  - User code submit visible elements. */
class ListClipper {

    var displayStart = 0
    var displayEnd = 0

    val display
        get() = displayStart until displayEnd

    // [Internal]
    var itemsCount = -1
    var stepNo = 0
    var itemsFrozen = 0
    var itemsHeight = 0f
    var startPosY = 0f

    fun dispose() = assert(itemsCount == -1) { "Forgot to call End(), or to Step() until false?" }

    /** Automatically called by constructor if you passed 'items_count' or by Step() in Step 1.
     *  Use case A: Begin() called from constructor with items_height<0, then called again from Sync() in StepNo 1
     *  Use case B: Begin() called from constructor with items_height>0
     *  FIXME-LEGACY: Ideally we should remove the Begin/End functions but they are part of the legacy API we still
     *  support. This is why some of the code in Step() calling Begin() and reassign some fields, spaghetti style.
     */
    fun begin(itemsCount: Int = -1, itemsHeight: Float = -1f) {

        val window = g.currentWindow!!

        g.currentTable?.let { table ->
            if (table.isInsideRow)
                table.endRow()
        }

        startPosY = window.dc.cursorPos.y
        this.itemsHeight = itemsHeight
        this.itemsCount = itemsCount
        itemsFrozen = 0
        stepNo = 0
        displayStart = -1
        displayEnd = 0
    }

    /** Automatically called on the last call of Step() that returns false. */
    fun end() {

        if (itemsCount < 0) // Already ended
            return

        // In theory here we should assert that ImGui::GetCursorPosY() == StartPosY + DisplayEnd * ItemsHeight, but it feels saner to just seek at the end and not assert/crash the user.
        if (itemsCount < Int.MAX_VALUE && displayStart >= 0)
            setCursorPosYAndSetupForPrevLine(startPosY + (itemsCount - itemsFrozen) * itemsHeight, itemsHeight)
        itemsCount = -1
        stepNo = 3
    }

    /** Call until it returns false. The DisplayStart/DisplayEnd fields will be set and you can process/draw those
     *  items.  */
    fun step(): Boolean {

        val window = g.currentWindow!!

        val table = g.currentTable
        if (table != null && table.isInsideRow)
            table.endRow()

        // Reached end of list
        if (displayEnd >= itemsCount || skipItemForListClipping) {
            end()
            return false
        }
        // Step 0: Let you process the first element (regardless of it being visible or not, so we can measure the element height)
        if (stepNo == 0) {

            // While we are in frozen row state, keep displaying items one by one, unclipped
            // FIXME: Could be stored as a table-agnostic state.
            if (table != null && !table.isUnfrozen) {
                displayStart = itemsFrozen
                displayEnd = itemsFrozen + 1
                itemsFrozen++
                return true
            }

            startPosY = window.dc.cursorPos.y
            if (itemsHeight <= 0f) {
                // Submit the first item so we can measure its height (generally it is 0..1)
                displayStart = itemsFrozen
                displayEnd = itemsFrozen + 1
                stepNo = 1
                return true
            }

            // Already has item height (given by user in Begin): skip to calculating step
            displayStart = displayEnd
            stepNo = 2
        }

        // Step 1: the clipper infer height from first element
        if (stepNo == 1) {
            assert(itemsHeight <= 0f)
            if (table != null) {
                val posY1 = table.rowPosY1   // Using this instead of StartPosY to handle clipper straddling the frozen row
                val posY2 = table.rowPosY2   // Using this instead of CursorPos.y to take account of tallest cell.
                itemsHeight = posY2 - posY1
                window.dc.cursorPos.y = posY2
            } else
                itemsHeight = window.dc.cursorPos.y - startPosY
            assert(itemsHeight > 0f) { "Unable to calculate item height! First item hasn't moved the cursor vertically!" }
            stepNo = 2
        }

        // Step 2: calculate the actual range of elements to display, and position the cursor before the first element
        if (stepNo == 2) {
            assert(itemsHeight > 0f)

            val alreadySubmitted = displayEnd
            ImGui.calcListClipping(itemsCount - alreadySubmitted, itemsHeight)
            displayStart += alreadySubmitted
            displayEnd += alreadySubmitted

            // Seek cursor
            if (displayStart > alreadySubmitted)
                setCursorPosYAndSetupForPrevLine(startPosY + (displayStart - itemsFrozen) * itemsHeight, itemsHeight)

            stepNo = 3
            return true
        }

        // Step 3: the clipper validate that we have reached the expected Y position (corresponding to element DisplayEnd),
        // Advance the cursor to the end of the list and then returns 'false' to end the loop.
        if (stepNo == 3) {
            // Seek cursor
            if (itemsCount < Int.MAX_VALUE)
                setCursorPosYAndSetupForPrevLine(startPosY + (itemsCount - itemsFrozen) * itemsHeight, itemsHeight) // advance cursor
            itemsCount = -1
            return false
        }

        error("game over")
    }

    companion object {

        fun setCursorPosYAndSetupForPrevLine(posY: Float, lineHeight: Float) {
            // Set cursor position and a few other things so that SetScrollHereY() and Columns() can work when seeking cursor.
            // FIXME: It is problematic that we have to do that here, because custom/equivalent end-user code would stumble on the same issue.
            // The clipper should probably have a 4th step to display the last item in a regular manner.
            val window = g.currentWindow!!
            val offY = posY - window.dc.cursorPos.y
            window.dc.cursorPos.y = posY
            window.dc.cursorMaxPos.y = window.dc.cursorMaxPos.y max posY
            window.dc.cursorPosPrevLine.y = window.dc.cursorPos.y - lineHeight  // Setting those fields so that SetScrollHereY() can properly function after the end of our clipper usage.
            window.dc.prevLineSize.y = lineHeight - g.style.itemSpacing.y      // If we end up needing more accurate data (to e.g. use SameLine) we may as well make the clipper have a fourth step to let user process and display the last item in their list.
            window.dc.currentColumns?.let { columns ->
                columns.lineMinY = window.dc.cursorPos.y                         // Setting this so that cell Y position are set properly
            }
            g.currentTable?.let { table ->
                if (table.isInsideRow)
                    table.endRow()
                table.rowPosY2 = window.dc.cursorPos.y
                val rowIncrease = ((offY / lineHeight) + 0.5f).i
                //table->CurrentRow += row_increase; // Can't do without fixing TableEndRow()
                table.rowBgColorCounter += rowIncrease
            }
        }
    }
}

// FIXME-TABLE: This prevents us from using ImGuiListClipper _inside_ a table cell.
// The problem we have is that without a Begin/End scheme for rows using the clipper is ambiguous.
val skipItemForListClipping: Boolean
    get() = g.currentTable?.hostSkipItems ?: g.currentWindow!!.skipItems