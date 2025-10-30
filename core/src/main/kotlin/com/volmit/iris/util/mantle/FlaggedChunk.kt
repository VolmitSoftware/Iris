package com.volmit.iris.util.mantle

import com.volmit.iris.util.data.Varint
import com.volmit.iris.util.mantle.flag.MantleFlag
import com.volmit.iris.util.parallel.AtomicBooleanArray
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.lang.Byte
import kotlin.Boolean
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.Throwable
import kotlin.Throws
import kotlin.Unit

abstract class FlaggedChunk() {
    private val flags = AtomicBooleanArray(MantleFlag.MAX_ORDINAL + 1)
    private val locks = 0.rangeUntil(flags.length()).map { Mutex() }.toTypedArray()

    abstract fun isClosed(): Boolean

    protected fun copyFlags(other: FlaggedChunk) {
        for (i in 0 until flags.length()) {
            flags.set(i, other.flags.get(i))
        }
    }

    fun isFlagged(flag: MantleFlag) = flags.get(flag.ordinal())
    fun flag(flag: MantleFlag, value: Boolean) {
        if (isClosed()) throw IllegalStateException("Chunk is closed!")
        flags.set(flag.ordinal(), value)
    }

    suspend fun raiseFlagSuspend(guard: MantleFlag?, flag: MantleFlag, task: suspend () -> Unit) {
        if (isClosed()) throw IllegalStateException("Chunk is closed!")
        if (guard != null && isFlagged(guard)) return

        locks[flag.ordinal()].withLock {
            if (flags.compareAndSet(flag.ordinal(), false, true)) {
                try {
                    task()
                } catch (e: Throwable) {
                    flags.set(flag.ordinal(), false)
                    throw e
                }
            }
        }
    }

    fun raiseFlagUnchecked(flag: MantleFlag, task: Runnable) {
        if (isClosed()) throw IllegalStateException("Chunk is closed!")
        if (flags.compareAndSet(flag.ordinal(), false, true)) {
            try {
                task.run()
            } catch (e: Throwable) {
                flags.set(flag.ordinal(), false)
                throw e
            }
        }
    }

    @Throws(IOException::class)
    protected fun readFlags(version: Int, din: DataInput) {
        val l = if (version < 0) 16 else Varint.readUnsignedVarInt(din)

        if (version >= 1) {
            var i = 0
            while (i < l) {
                val f = din.readByte()
                var j = 0
                while (j < Byte.SIZE && i < flags.length()) {
                    flags.set(i, (f.toInt() and (1 shl j)) != 0)
                    j++
                    i++
                }
            }
        } else {
            for (i in 0 until l) {
                flags.set(i, din.readBoolean())
            }
        }
    }

    @Throws(IOException::class)
    protected fun writeFlags(dos: DataOutput) {
        Varint.writeUnsignedVarInt(flags.length(), dos)
        val count = flags.length()
        var i = 0
        while (i < count) {
            var f = 0
            for (j in 0 until Byte.SIZE) {
                if (i >= count) break
                f = f or if (flags.get(i)) 1 shl j else 0
                i++
            }
            dos.write(f)
        }
    }
}