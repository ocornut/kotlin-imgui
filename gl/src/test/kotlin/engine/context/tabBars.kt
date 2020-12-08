package engine.context

import engine.engine.TestRef

// Tab Bars

infix fun TestContext.tabClose(ref: TestRef) {

    if (isError)
        return

    REGISTER_DEPTH {
        logDebug("TabClose '${ref.path ?: "NULL"}' %08X", ref.id)

        // Move into first, then click close button as it appears
        mouseMove(ref)
        itemClick(getID("#CLOSE", ref))
    }
}