package engine.context

import engine.core.*
import engine.hashDecoratedPath
import glm_.b
import imgui.ID

// [JVM]
fun TestContext.itemLocate(ref: String, flags: TestOpFlags = TestOpFlag.None.i): TestItemInfo? = itemLocate(TestRef(path = ref), flags)

fun TestContext.itemLocate(ref: TestRef, flags: TestOpFlags = TestOpFlag.None.i): TestItemInfo? {

    if (isError) return null

    var fullId: ID = 0
    val path = ref.path
    if (path != null && path.startsWith("**/")) {
        // Wildcard matching
        val task = engine!!.testFindLabelTask
        task.inBaseId = refID
        task.inLabel = path.substring(3)
        task.outItemId = 0

        var retries = 0
        while (retries < 2 && fullId == 0) {
            yield()
            fullId = task.outItemId
            retries++
        }

        // FIXME: InFilterItemFlags is not unset here intentionally, because it is set in ItemAction() and reused in later calls to ItemLocate() to resolve ambiguities.
        task.inBaseId = 0
        task.inLabel = null
        task.outItemId = 0
    } else // Normal matching
        when {
            ref.id != 0 -> ref.id
            else -> hashDecoratedPath(ref.path!!, refID)
        }

    // If ui_ctx->TestEngineHooksEnabled is not already on (first ItemLocate task in a while) we'll probably need an extra frame to warmup
    return REGISTER_DEPTH {
        var retries = 0
        while (fullId != 0 && retries < 2) {
            val item = engine!!.itemLocate(fullId, ref.path)
            item?.let { return it }
            engine!!.yield()
            retries++
        }

        if (flags hasnt TestOpFlag.NoError) {
            // Prefixing the string with / ignore the reference/current ID
            val path = ref.path
            if (path?.get(0) == '/' && refStr[0] != 0.b)
                ERRORF_NOHDR("Unable to locate item: '$path'")
            else if (path != null)
                ERRORF_NOHDR("Unable to locate item: '$refStr/$path' (0x%08X)", fullId)
            else
                ERRORF_NOHDR("Unable to locate item: 0x%08X", ref.id)
        }
        null
    }
}

fun TestContext.gatherItems(outList: TestItemList?, parent: TestRef, depth_: Int = -1) {

    var depth = depth_
    assert(outList != null)
    assert(depth > 0 || depth == -1)
    val gatherTask = gatherTask!!
    assert(gatherTask.parentID == 0)
    assert(gatherTask.lastItemInfo == null)

    if (isError) return

    // Register gather tasks
    if (depth == -1)
        depth = 99
    if (parent.id == 0)
        parent.id = getID(parent)
    gatherTask.parentID = parent.id
    gatherTask.depth = depth
    gatherTask.outList = outList!!

    // Keep running while gathering
    val beginGatherSize = outList.size
    while (true) {
        val beginGatherSizeForFrame = outList.size
        yield()
        val endGatherSizeForFrame = outList.size
        if (beginGatherSizeForFrame == endGatherSizeForFrame)
            break
    }
    val endGatherSize = outList.size

    val parentItem = itemLocate(parent, TestOpFlag.NoError.i)
    logDebug("GatherItems from ${TestRefDesc(parent, parentItem)}, $depth deep: found ${endGatherSize - beginGatherSize} items.")

    gatherTask.also {
        it.parentID = 0
        it.depth = 0
        it.outList = null
        it.lastItemInfo = null
    }
}