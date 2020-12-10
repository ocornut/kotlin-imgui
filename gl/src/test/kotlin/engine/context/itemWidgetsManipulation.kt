package engine.context

import engine.engine.*
import gli_.has
import glm_.vec2.Vec2
import imgui.ID
import imgui.Key
import imgui.KeyMod
import imgui.MouseButton
import imgui.internal.sections.InputSource
import imgui.internal.sections.has
import imgui.internal.sections.hasnt
import imgui.internal.sections.ItemStatusFlag as Isf

// [JVM]
fun TestContext.itemAction(action: TestAction, ref: String, actionArg: Int? = null, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(action, TestRef(path = ref), actionArg, flags)

// [JVM]
fun TestContext.itemAction(action: TestAction, ref: ID, actionArg: Int? = null, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(action, TestRef(ref), actionArg, flags)

fun TestContext.itemAction(action_: TestAction, ref: TestRef, actionArg: Int? = null, flags: TestOpFlags = TestOpFlag.None.i) {

    var action = action_
    if (isError) return

    REGISTER_DEPTH {

        // [DEBUG] Breakpoint
        //if (ref.ID == 0x0d4af068)
        //    printf("");

        // FIXME-TESTS: Fix that stuff
        val path = ref.path
        val isWildcard = path != null && path.indexOf("**/") != -1
        if (isWildcard) {
            // This is a fragile way to avoid some ambiguities, we're relying on expected action to further filter by status flags.
            // These flags are not cleared by ItemInfo() because ItemAction() may call ItemInfo() again to get same item and thus it
            // needs these flags to remain in place.
            if (action == TestAction.Check || action == TestAction.Uncheck)
                engine!!.findByLabelTask.inFilterItemStatusFlags = Isf.Checkable.i
            else if (action == TestAction.Open || action == TestAction.Close)
                engine!!.findByLabelTask.inFilterItemStatusFlags = Isf.Openable.i
        }

        val item = itemInfo(ref) ?: return
        val desc = TestRefDesc(ref, item)

        logDebug("Item${action.name} $desc${if (inputMode == InputSource.Mouse) "" else " (w/ Nav)"}")

        // Automatically uncollapse by default
        item.window?.let {
            if (opFlags hasnt TestOpFlag.NoAutoUncollapse)
                windowCollapse(it, false)
        }

        if (action == TestAction.Hover)
            mouseMove(ref, flags)
        if (action == TestAction.Click || action == TestAction.DoubleClick)
            if (inputMode == InputSource.Mouse) {
                val mouseButton = actionArg ?: 0
                assert(mouseButton >= 0 && mouseButton < MouseButton.COUNT)
                mouseMove(ref, flags)
                if (!engineIO!!.configRunFast)
                    sleep(0.05f)
                if (action == TestAction.DoubleClick)
                    mouseDoubleClick(mouseButton)
                else
                    mouseClick(mouseButton)
            } else action = TestAction.NavActivate

        if (action == TestAction.NavActivate) {
            assert(actionArg == null || actionArg == 0) // Unused
            navMoveTo(ref)
            navActivate()
            if (action == TestAction.DoubleClick)
                assert(false)
        } else if (action == TestAction.Input) {
            assert(actionArg == null) // Unused
            if (inputMode == InputSource.Mouse) {
                mouseMove(ref, flags)
                keyDownMap(Key.Count, KeyMod.Ctrl.i)
                mouseClick(0)
                keyUpMap(Key.Count, KeyMod.Ctrl.i)
            } else {
                navMoveTo(ref)
                navInput()
            }
        } else if (action == TestAction.Open) {
            assert(actionArg == null) // Unused
            if (item.statusFlags hasnt Isf.Opened) {
                item.refCount++
                mouseMove(ref, flags)

                // Some item may open just by hovering, give them that chance
                if (item.statusFlags hasnt Isf.Opened) {
                    itemClick(ref, 0, flags)
                    if (item.statusFlags hasnt Isf.Opened) {
                        itemDoubleClick(ref, flags) // Attempt a double-click // FIXME-TESTS: let's not start doing those fuzzy things..
                        if (item.statusFlags hasnt Isf.Opened)
                            ERRORF_NOHDR("Unable to Open item: ${TestRefDesc(ref, item)}")
                    }
                }
                item.refCount--
                yield()
            }
        } else if (action == TestAction.Close) {
            assert(actionArg == null) // Unused
            if (item.statusFlags has Isf.Opened) {
                item.refCount++
                itemClick(ref, 0, flags)
                if (item.statusFlags has Isf.Opened) {
                    itemDoubleClick(ref, flags) // Attempt a double-click
                    // FIXME-TESTS: let's not start doing those fuzzy things.. widget should give direction of how to close/open... e.g. do you we close a TabItem?
                    if (item.statusFlags has Isf.Opened)
                        ERRORF_NOHDR("Unable to Close item: ${TestRefDesc(ref, item)}")
                }
                item.refCount--
                yield()
            }
        } else if (action == TestAction.Check) {
            assert(actionArg == null) // Unused
            if (item.statusFlags has Isf.Checkable && item.statusFlags hasnt Isf.Checked) {
                itemClick(ref, 0, flags)
                yield()
            }
            itemVerifyCheckedIfAlive(ref, true) // We can't just IM_ASSERT(ItemIsChecked()) because the item may disappear and never update its StatusFlags any more!
        } else if (action == TestAction.Uncheck) {
            assert(actionArg == null) // Unused
            if (item.statusFlags has Isf.Checkable && item.statusFlags has Isf.Checked) {
                itemClick(ref, 0, flags)
                yield()
            }
            itemVerifyCheckedIfAlive(ref, false) // We can't just IM_ASSERT(ItemIsChecked()) because the item may disappear and never update its StatusFlags any more!
        }

        //if (is_wildcard)
        engine!!.findByLabelTask.inFilterItemStatusFlags = Isf.None.i
    }
}

// [JVM]
fun TestContext.itemClick(ref: String, button: Int = 0, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Click, TestRef(path = ref), button, flags)

// [JVM]
fun TestContext.itemClick(ref: ID, button: Int = 0, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Click, TestRef(ref), button, flags)

fun TestContext.itemClick(ref: TestRef, button: Int = 0, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Click, ref, button, flags)

// [JVM]
fun TestContext.itemDoubleClick(ref: String, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.DoubleClick, TestRef(path = ref), null, flags)

fun TestContext.itemDoubleClick(ref: TestRef, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.DoubleClick, ref, null, flags)

// [JVM]
fun TestContext.itemCheck(ref: String, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Check, TestRef(path = ref), null, flags)

fun TestContext.itemCheck(ref: TestRef, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Check, ref, null, flags)

// [JVM]
fun TestContext.itemUncheck(ref: String, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Uncheck, TestRef(path = ref), null, flags)

fun TestContext.itemUncheck(ref: TestRef, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Uncheck, ref, null, flags)

// [JVM]
fun TestContext.itemOpen(ref: String, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Open, TestRef(path = ref), null, flags)

// [JVM]
fun TestContext.itemOpen(ref: ID, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Open, TestRef(ref), null, flags)

fun TestContext.itemOpen(ref: TestRef, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Open, ref, null, flags)

// [JVM]
fun TestContext.itemClose(ref: ID, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Close, TestRef(ref), null, flags)

// [JVM]
fun TestContext.itemClose(ref: String, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Close, TestRef(path = ref), null, flags)

fun TestContext.itemClose(ref: TestRef, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Close, ref, null, flags)

// [JVM]
fun TestContext.itemInput(ref: String, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Input, TestRef(path = ref), null, flags)

fun TestContext.itemInput(ref: TestRef, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.Input, ref, null, flags)

fun TestContext.itemNavActivate(ref: TestRef, flags: TestOpFlags = TestOpFlag.None.i) =
        itemAction(TestAction.NavActivate, ref, null, flags)

// [JVM]
fun TestContext.itemActionAll(action: TestAction, refParent: String, filter: TestActionFilter) =
        itemActionAll(action, TestRef(path = refParent), filter)

// [JVM]
fun TestContext.itemActionAll(action: TestAction, refParent: ID, filter: TestActionFilter) =
        itemActionAll(action, TestRef(refParent), filter)

fun TestContext.itemActionAll(action: TestAction, refParent: TestRef, filter: TestActionFilter) {

    var maxDepth = filter.maxDepth
    if (maxDepth == -1)
        maxDepth = 99
    var maxPasses = filter.maxPasses
    if (maxPasses == -1)
        maxPasses = 99
    assert(maxDepth > 0 && maxPasses > 0)

    var actionedTotal = 0
    for (pass in 0 until maxPasses) {
//        println("pass $pass")
        val items = TestItemList()
        gatherItems(items, refParent, maxDepth)

        // Find deep most items
        val highestDepth = when (action) {
            TestAction.Close -> items.filter { it.statusFlags has Isf.Openable && it.statusFlags has Isf.Opened }
                    .maxByOrNull { it.depth } ?: -1
            else -> -1
        }

        val actionedTotalAtBeginningOfPass = actionedTotal

        // Process top-to-bottom in most cases
        var scanStart = 0
        var scanEnd = items.size
        var scanDir = +1
        if (action == TestAction.Close) {
            // Close bottom-to-top because
            // 1) it is more likely to handle same-depth parent/child relationship better (e.g. CollapsingHeader)
            // 2) it gives a nicer sense of symmetry with the corresponding open operation.
            scanStart = items.lastIndex
            scanEnd = -1
            scanDir = -1
        }

        val processedCountPerDepth = IntArray(8)

        var n = scanStart
        while (n != scanEnd) {
            if (isError) break

            val item = items[n]

            if (filter.requireAllStatusFlags != 0)
                if ((item.statusFlags and filter.requireAllStatusFlags) != filter.requireAllStatusFlags) {
                    n += scanDir
                    continue
                }

            if (filter.requireAnyStatusFlags != 0)
                if (item.statusFlags has filter.requireAnyStatusFlags) {
                    n += scanDir
                    continue
                }

            val maxItemCountPerDepth = filter.maxItemCountPerDepth
            if (maxItemCountPerDepth != null)
                if (item.depth < processedCountPerDepth.size) {
                    if (processedCountPerDepth[item.depth] >= maxItemCountPerDepth[item.depth]) {
                        n += scanDir
                        continue
                    }
                    processedCountPerDepth[item.depth]++
                }

            when (action) {
                TestAction.Hover -> {
                    itemAction(action, item.id)
                    actionedTotal++
                }
                TestAction.Click -> {
                    itemAction(action, item.id)
                    actionedTotal++
                }
                TestAction.Check ->
                    if (item.statusFlags has Isf.Checkable && item.statusFlags hasnt Isf.Checked) {
                        itemAction(action, item.id)
                        actionedTotal++
                    }
                TestAction.Uncheck ->
                    if (item.statusFlags has Isf.Checkable && item.statusFlags has Isf.Checked) {
                        itemAction(action, item.id)
                        actionedTotal++
                    }
                TestAction.Open ->
                    if (item.statusFlags has Isf.Openable && item.statusFlags hasnt Isf.Opened) {
                        itemAction(action, item.id)
                        actionedTotal++
                    }
                TestAction.Close ->
                    if (item.depth == highestDepth && item.statusFlags has Isf.Openable && item.statusFlags has Isf.Opened) {
                        itemClose(item.id)
                        actionedTotal++
                    }
                else -> assert(false)
            }
            n += scanDir
        }

        if (isError) break

        if (action == TestAction.Hover)
            break
        if (actionedTotalAtBeginningOfPass == actionedTotal)
            break
    }
    logDebug("$action $actionedTotal items in total!")
}

// [JVM]
fun TestContext.itemOpenAll(refParent: String, maxDepth: Int = -1, maxPasses: Int = -1) =
        itemOpenAll(TestRef(path = refParent), maxDepth, maxPasses)

// [JVM]
fun TestContext.itemOpenAll(refParent: ID, maxDepth: Int = -1, maxPasses: Int = -1) =
        itemOpenAll(TestRef(refParent), maxDepth, maxPasses)

fun TestContext.itemOpenAll(refParent: TestRef, maxDepth: Int = -1, maxPasses: Int = -1) {
    val filter = TestActionFilter().also {
        it.maxDepth = maxDepth
        it.maxPasses = maxPasses
    }
    itemActionAll(TestAction.Open, refParent, filter)
}

// [JVM]
fun TestContext.itemCloseAll(refParent: String, maxDepth: Int = -1, maxPasses: Int = -1) =
        itemCloseAll(TestRef(path = refParent), maxDepth, maxPasses)

// [JVM]
fun TestContext.itemCloseAll(refParent: ID, maxDepth: Int = -1, maxPasses: Int = -1) =
        itemCloseAll(TestRef(refParent), maxDepth, maxPasses)

fun TestContext.itemCloseAll(refParent: TestRef, maxDepth: Int = -1, maxPasses: Int = -1) {
    val filter = TestActionFilter().also {
        it.maxDepth = maxDepth
        it.maxPasses = maxPasses
    }
    itemActionAll(TestAction.Close, refParent, filter)
}

// [JVM]
fun TestContext.itemHold(ref: String, time: Float) = itemHold(TestRef(path = ref), time)

fun TestContext.itemHold(ref: TestRef, time: Float) {

    if (isError) return

    REGISTER_DEPTH {
        logDebug("ItemHold '${ref.path}' %08X", ref.id)

        mouseMove(ref)

        yield()
        inputs!!.mouseButtonsValue = 1 shl 0
        sleep(time)
        inputs!!.mouseButtonsValue = 0
        yield()
    }
}

// Used to test opening containers (TreeNode, Tabs) while dragging a payload
fun TestContext.itemDragOverAndHold(refSrc: TestRef, refDst: TestRef) {

    if (isError)
        return

    REGISTER_DEPTH {
        val itemSrc = itemInfo(refSrc)
        val itemDst = itemInfo(refDst)
        val descSrc = TestRefDesc(refSrc, itemSrc)
        val descDst = TestRefDesc(refDst, itemDst)
        logDebug("ItemDragOverAndHold $descSrc to $descDst")

        mouseMove(refSrc, TestOpFlag.NoCheckHoveredId.i)
        sleepShort()
        mouseDown(0)

        // Enforce lifting drag threshold even if both item are exactly at the same location.
        mouseLiftDragThreshold()

        mouseMove(refDst, TestOpFlag.NoCheckHoveredId.i)
        sleepNoSkip(1f, 1f / 10f)
        mouseUp(0)
    }
}

// [JVM]
fun TestContext.itemHoldForFrames(ref: String, frames: Int) = itemHoldForFrames(TestRef(path = ref), frames)

fun TestContext.itemHoldForFrames(ref: TestRef, frames: Int) {

    if (isError) return

    REGISTER_DEPTH {
        logDebug("ItemHoldForFrames '${ref.path}' %08X", ref.id)

        mouseMove(ref)
        yield()
        inputs!!.mouseButtonsValue = 1 shl 0
        yield(frames)
        inputs!!.mouseButtonsValue = 0
        yield()
    }
}

fun TestContext.itemDragWithDelta(refSrc: TestRef, posDelta: Vec2) {
    if (isError)
        return

    REGISTER_DEPTH {
        val itemSrc = itemInfo(refSrc)
        val descSrc = TestRefDesc(refSrc, itemSrc)
        logDebug("ItemDragWithDelta $descSrc to (${posDelta.x}, ${posDelta.y})")

        mouseMove(refSrc, TestOpFlag.NoCheckHoveredId.i)
        sleepShort()
        mouseDown(0)

        mouseMoveToPos(uiContext!!.io.mousePos + posDelta)
        sleepShort()
        mouseUp(0)
    }
}

// [JVM]
fun TestContext.itemDragAndDrop(refSrc: String, refDst: String) = itemDragAndDrop(TestRef(path = refSrc), TestRef(path = refDst))

fun TestContext.itemDragAndDrop(refSrc: TestRef, refDst: TestRef) {

    if (isError) return

    REGISTER_DEPTH {
        val itemSrc = itemInfo(refSrc)
        val itemDst = itemInfo(refDst)
        val descSrc = TestRefDesc(refSrc, itemSrc)
        val descDst = TestRefDesc(refDst, itemDst)
        logDebug("ItemDragAndDrop $descSrc to $descDst")

        mouseMove(refSrc, TestOpFlag.NoCheckHoveredId.i)
        sleepShort()
        mouseDown(0)

        // Enforce lifting drag threshold even if both item are exactly at the same location.
        mouseLiftDragThreshold()

        mouseMove(refDst, TestOpFlag.NoCheckHoveredId.i)
        sleepShort()
        mouseUp(0)
    }
}

fun TestContext.itemVerifyCheckedIfAlive(ref: TestRef, checked: Boolean) {

    yield()
    itemInfo(ref, TestOpFlag.NoError.i)?.let {
        if (it.timestampMain + 1 >= engine!!.frameCount &&
                it.timestampStatus == it.timestampMain &&
                it.statusFlags has Isf.Checked != checked)
            CHECK((it.statusFlags has Isf.Checked) == checked)
    }
}