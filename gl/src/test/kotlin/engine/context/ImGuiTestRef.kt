package engine.context

import engine.engine.TestItemInfo
import imgui.ID

//-------------------------------------------------------------------------
// ImGuiTestRef
//-------------------------------------------------------------------------

// Weak reference to an Item/Window given an ID or ID path.
class TestRef(
        var id: ID = 0,
        var path: String? = null) {

    constructor(path: String) : this(0, path)

    val isEmpty: Boolean
        get() = id == 0 && (path == null || path!!.isEmpty())
}

// Helper to output a string showing the Path, ID or Debug Label based on what is available (some items only have ID as we couldn't find/store a Path)
class TestRefDesc(val ref: TestRef, val item: TestItemInfo? = null) {
    override fun toString(): String = ref.path?.let { "'$it' > %08X".format(ref.id) }
            ?: "%08X > '${item?.debugLabel}'".format(ref.id)
}