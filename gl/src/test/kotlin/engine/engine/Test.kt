package engine.engine

import engine.context.TestContext

//-------------------------------------------------------------------------
// ImGuiTest
//-------------------------------------------------------------------------

typealias TestRunFunc = (ctx: TestContext) -> Unit
typealias TestGuiFunc = (ctx: TestContext) -> Unit
typealias TestTestFunc = (ctx: TestContext) -> Unit

// Wraps a placement new of a given type (where 'buffer' is the allocated memory)
//typedef void    (*ImGuiTestUserDataConstructor)(void* buffer);
//typedef void    (*ImGuiTestUserDataDestructor)(void* ptr);

// Storage for one test
class Test {

    /** Coarse groups: 'Tests' or 'Perf' */
    var group = TestGroup.Unknown
    var nameOwned = false

    /** Literal, not owned */
    var category: String? = null

    /** Literal, generally not owned unless NameOwned=true */
    var name: String? = null

    /** __FILE__ */
    var sourceFile: String? = null

    /** Pointer within SourceFile, skips filename. */
    var sourceFileShort: String? = null

    /** __LINE__ */
    var sourceLine = 0
    var sourceLineEnd = 0

    /** User parameter, for use by GuiFunc/TestFunc. Generally we use it to run variations of a same test. */
    var argVariant = 0

    /** When SetUserDataType() is used, we create an instance of user structure so we can be used by GuiFunc/TestFunc. */
    var userDataSize = 0

    /** [JVM] */
    var userData: Any? = null

    //    ImGuiTestUserDataConstructor    UserDataConstructor
//    ImGuiTestUserDataDestructor     UserDataDestructor
    var status = TestStatus.Unknown

    /** See ImGuiTestFlags_ */
    var flags = TestFlag.None.i

    /** GUI functions can be reused */
    var guiFunc: TestGuiFunc? = null
    var guiFuncLastFrame = -1

    /** Test function */
    var testFunc: TestTestFunc? = null
    val testLog = TestLog()

    fun setOwnedName(name: String) {
        assert(!nameOwned)
        nameOwned = true
        this.name = name
    }

//    template <typename T>
//    void SetUserDataType()
//    {
//        UserDataSize = sizeof(T)
//        UserDataConstructor = [](void * ptr) { IM_PLACEMENT_NEW(ptr) T; }
//        UserDataDestructor = [](void * ptr) { IM_UNUSED(ptr); reinterpret_cast < T * >(ptr)->~T(); }
//    }
}