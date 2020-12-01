import imgui.strlen
import kool.pos
import java.nio.ByteBuffer



fun ByteBuffer.sliceAt(offset: Int): ByteBuffer {
    val backupPos = pos
    pos = offset
    val res = slice()
    pos = backupPos
    return res
}