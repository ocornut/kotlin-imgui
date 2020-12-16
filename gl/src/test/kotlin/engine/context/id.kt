package engine.context

import engine.hashDecoratedPath
import imgui.ID
import imgui.internal.hash

// [JVM]
fun TestContext.getID(ref: ID): ID = getID(TestRef(ref))

// [JVM]
fun TestContext.getID(ref: String): ID = getID(TestRef(ref))

fun TestContext.getID(ref: TestRef): ID = when (ref.id) {
    0 -> hashDecoratedPath(ref.path!!, null, refID)
    else -> ref.id
}

// [JVM]
fun TestContext.getID(ref: String, seedRef: TestRef): ID = getID(TestRef(ref), seedRef)

// [JVM]
fun TestContext.getID(ref: String, seedRef: ID): ID = getID(TestRef(ref), TestRef(seedRef))

// [JVM]
fun TestContext.getID(ref: TestRef, seedRef: String): ID = getID(ref, TestRef(seedRef))

fun TestContext.getID(ref: TestRef, seedRef: TestRef): ID = when (ref.id) {
    0 -> hashDecoratedPath(ref.path!!, null, getID(seedRef))
    else -> ref.id // FIXME: What if seed_ref != 0
}

fun TestContext.getIDByInt(n: Int): ID = hash(n, getID(refID))

fun TestContext.getIDByInt(n: Int, seedRef: TestRef): ID = hash(n, getID(refID))

//    TODO
//    fun TestContext.getIDByPtr (p: Any): ID
//    ImGuiID GetIDByPtr (void * p, ImGuiTestRef seed_ref)