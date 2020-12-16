package engine.context

import engine.engine.TestItemList
import engine.engine.TestRef
import glm_.b
import glm_.vec2.Vec2
import imgui.WindowFlag
import imgui.hasnt
import imgui.internal.classes.Window
import imgui.internal.strchrRange
import imgui.strlen

// [JVM]
fun TestContext.menuAction(action: TestAction, ref: String) = menuAction(action, TestRef(path = ref))

fun TestContext.menuAction(action: TestAction, ref: TestRef) {

    if (isError) return

    REGISTER_DEPTH {

        logDebug("MenuAction '${ref.path!!}' %08X", ref.id)

//        assert(ref.path != null)

        var depth = 0
        val str = ref.path!!.toByteArray()
        var path = 0
        val pathEnd = str.strlen()
        while (path < pathEnd) {
            var p = strchrRange(str, path, pathEnd, '/')
            if (p == -1)
                p = pathEnd
            val isTargetItem = p == pathEnd
            val buf = when (depth) {
                0 -> { // Click menu in menu bar
                    assert(refStr[0] != 0.b) { "Unsupported: window needs to be in Ref" }
                    "##menubar/${String(str, path, p - path)}"
                }
                // Click sub menu in its own window
                else -> "/##Menu_%02d/${String(str, path, p - path)}".format(depth - 1)
            }

            // We cannot move diagonally to a menu item because depending on the angle and other items we cross on our path we could close our target menu.
            // First move horizontally into the menu, then vertically!
            if (depth > 0) {
                val item = itemInfo(buf)!!
//                IM_CHECK_SILENT(item != NULL)
                item.refCount++
                if (depth > 1 && (inputs!!.mousePosValue.x <= item.rectFull.min.x || inputs!!.mousePosValue.x >= item.rectFull.max.x))
                    mouseMoveToPos(Vec2(item.rectFull.center.x, inputs!!.mousePosValue.y))
                if (depth > 0 && (inputs!!.mousePosValue.y <= item.rectFull.min.y || inputs!!.mousePosValue.y >= item.rectFull.max.y))
                    mouseMoveToPos(Vec2(inputs!!.mousePosValue.x, item.rectFull.center.y))
                item.refCount--
            }

            if (isTargetItem)
            // Final item
                itemAction(action, buf)
            else // Then aim at the menu item
                itemAction(TestAction.Click, buf)

            path = p + 1
            depth++
        }
    }
}

fun TestContext.menuActionAll(action: TestAction, refParent: TestRef) {

    val items = TestItemList()
    menuAction(TestAction.Open, refParent)
    gatherItems(items, focusWindowRef, 1)
    for (item in items) {
        menuAction(TestAction.Open, refParent) // We assume that every interaction will close the menu again
        itemAction(action, item.id)
    }
}

// [JVM]
infix fun TestContext.menuClick(ref: String) = menuAction(TestAction.Click, TestRef(path = ref))

infix fun TestContext.menuClick(ref: TestRef) = menuAction(TestAction.Click, ref)

// [JVM]
infix fun TestContext.menuCheck(ref: String) = menuAction(TestAction.Check, TestRef(path = ref))
infix fun TestContext.menuCheck(ref: TestRef) = menuAction(TestAction.Check, ref)

infix fun TestContext.menuUncheck(ref: TestRef) = menuAction(TestAction.Uncheck, ref)

// [JVM]
infix fun TestContext.menuCheckAll(refParent: String) = menuActionAll(TestAction.Check, TestRef(path = refParent))
infix fun TestContext.menuCheckAll(refParent: TestRef) = menuActionAll(TestAction.Check, refParent)

// [JVM]
infix fun TestContext.menuUncheckAll(refParent: String) = menuActionAll(TestAction.Uncheck, TestRef(path = refParent))
infix fun TestContext.menuUncheckAll(refParent: TestRef) = menuActionAll(TestAction.Uncheck, refParent)

val Window.isACombo: Boolean
    get() = when {
        flags hasnt  WindowFlag._Popup -> false
        !name.startsWith("##Combo_") ->false
        else -> true
    }

// Combo
infix fun TestContext.comboClick(ref: TestRef) {

    if (isError)
        return

    REGISTER_DEPTH {
        logDebug("ComboClick '${ref.path ?: "NULL"}' %08X", ref.id)

        val path = ref.path
        check(path != null)

        var pathPtr = 0
        val pathEnd = path.length

        TODO()
//        const char* p = ImStrchrRangeWithEscaping(path, path_end, '/')
//        Str128f combo_popup_buf = Str128f("%.*s", (int)(p-path), path)
//        ItemClick(combo_popup_buf.c_str())
//
//        ImGuiTestRef popup_ref = GetFocusWindowRef();
//        ImGuiWindow* popup = GetWindowByRef(popup_ref);
//        IM_CHECK_SILENT(popup && IsWindowACombo(popup));
//
//        Str128f combo_item_buf = Str128f("/%s/**/%s", popup->Name, p + 1);
//        ItemClick(combo_item_buf.c_str())
    }
}

// [JVM]
infix fun TestContext.comboClickAll(refParent: String) = comboClickAll(TestRef(path = refParent))

infix fun TestContext.comboClickAll(refParent: TestRef) {

    itemClick(refParent)

    val popupRef = focusWindowRef
    val items = TestItemList()
    val popup = getWindowByRef(popupRef)
    assert(popup != null && popup.isACombo)

    gatherItems(items, popupRef)
    for (item in items) {
        itemClick(refParent) // We assume that every interaction will close the combo again
        itemClick(item.id)
    }
}