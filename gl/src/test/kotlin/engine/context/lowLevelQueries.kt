package engine.context

import engine.engine.*
import engine.hashDecoratedPath
import glm_.b
import imgui.ID

// [JVM]
fun TestContext.itemInfo(ref: String, flags: TestOpFlags = TestOpFlag.None.i): TestItemInfo? = itemInfo(TestRef(path = ref), flags)

fun TestContext.itemInfo(ref: TestRef, flags: TestOpFlags = TestOpFlag.None.i): TestItemInfo? {

    if (isError) return null

    var fullId: ID = 0

    // Wildcard matching
    // FIXME-TESTS: Need to verify that this is not inhibited by a \, so \**/ should not pass, but \\**/ should :)
    // We could add a simple helpers that would iterate the strings, handling inhibitors, and let you check if a given characters is inhibited or not.
    var wildcardPrefixStart: Int? = null
    var wildcardPrefixEnd: Int? = null
    var wildcardSuffixStart: Int? = null
    val path = ref.path
    if (path != null) {
        val p = path.indexOf("**/")
        if (p != -1) {
            wildcardPrefixStart = 0
            wildcardPrefixEnd = p
            wildcardSuffixStart = wildcardPrefixEnd + 3
        }
    }

    if (wildcardPrefixStart != null) {
        // Wildcard matching
        val task = engine!!.findByLabelTask
        task.inBaseId = when {
            wildcardPrefixStart < wildcardPrefixEnd!! -> hashDecoratedPath(path!!, wildcardPrefixEnd, refID)
            else -> refID
        }
        task.inLabel = path!!
        task.outItemId = 0
        logDebug("Wildcard matching..")

        var retries = 0
        while (retries < 2 && task.outItemId == 0) {
            yield()
            retries++
        }
        fullId = task.outItemId

        // FIXME: InFilterItemStatusFlags is not clear here intentionally, because it is set in ItemAction() and reused in later calls to ItemInfo() to resolve ambiguities.
        task.inBaseId = 0
        task.inLabel = null
        task.outItemId = 0
    } else // Normal matching
        when {
            ref.id != 0 -> ref.id
            else -> hashDecoratedPath(ref.path!!, null, refID)
        }

    // If ui_ctx->TestEngineHooksEnabled is not already on (first ItemItem task in a while) we'll probably need an extra frame to warmup
    return REGISTER_DEPTH {
        var retries = 0
        while (fullId != 0 && retries < 2) {
            val item = engine!!.findItemInfo(fullId, ref.path)
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

// [JVM]
fun TestContext.gatherItems(outList: TestItemList?, parent: String, depth: Int = -1) = gatherItems(outList, TestRef(path = parent), depth)

fun TestContext.gatherItems(outList: TestItemList?, parent: TestRef, depth_: Int = -1) {

    var depth = depth_
    assert(outList != null)
    assert(depth > 0 || depth == -1)
    val gatherTask = gatherTask!!
    assert(gatherTask.inParentID == 0)
    assert(gatherTask.lastItemInfo == null)

    if (isError) return

    // Register gather tasks
    if (depth == -1)
        depth = 99
    if (parent.id == 0)
        parent.id = getID(parent)
    gatherTask.inParentID = parent.id
    gatherTask.inDepth = depth
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

    // FIXME-TESTS: To support filter we'd need to process the list here,
    // Because ImGuiTestItemList is a pool (ImVector + map ID->index) we'll need to filter, rewrite, rebuild map

    val parentItem = itemInfo(parent, TestOpFlag.NoError.i)
    logDebug("GatherItems from ${TestRefDesc(parent, parentItem)}, $depth deep: found ${endGatherSize - beginGatherSize} items.")

    gatherTask.also {
        it.inParentID = 0
        it.inDepth = 0
        it.outList = null
        it.lastItemInfo = null
    }
}