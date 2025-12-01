package com.volmit.iris.core.pregenerator.cache

import com.volmit.iris.Iris
import com.volmit.iris.util.data.Varint
import com.volmit.iris.util.documentation.ChunkCoordinates
import com.volmit.iris.util.documentation.RegionCoordinates
import com.volmit.iris.util.io.IO
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.jpountz.lz4.LZ4BlockInputStream
import net.jpountz.lz4.LZ4BlockOutputStream
import java.io.*

class PregenCacheImpl(
    private val directory: File,
    private val maxSize: Int
) : PregenCache {
    private val cache = Object2ObjectLinkedOpenHashMap<Pair<Int, Int>, Plate>()

    @ChunkCoordinates
    override fun isChunkCached(x: Int, z: Int): Boolean {
        return this[x shr 10, z shr 10].isCached(
            (x shr 5) and 31,
            (z shr 5) and 31
        ) { isCached(x and 31, z and 31) }
    }

    @RegionCoordinates
    override fun isRegionCached(x: Int, z: Int): Boolean {
        return this[x shr 5, z shr 5].isCached(
            x and 31,
            z and 31,
            Region::isCached
        )
    }

    @ChunkCoordinates
    override fun cacheChunk(x: Int, z: Int) {
        this[x shr 10, z shr 10].cache(
            (x shr 5) and 31,
            (z shr 5) and 31
        ) { cache(x and 31, z and 31) }
    }

    @RegionCoordinates
    override fun cacheRegion(x: Int, z: Int) {
        this[x shr 5, z shr 5].cache(
            x and 31,
            z and 31,
            Region::cache
        )
    }

    override fun write() {
        if (cache.isEmpty()) return
        runBlocking {
            for (plate in cache.values) {
                if (!plate.dirty) continue
                launch(dispatcher) {
                    writePlate(plate)
                }
            }
        }
    }

    override fun trim(unloadDuration: Long) {
        if (cache.isEmpty()) return
        val threshold = System.currentTimeMillis() - unloadDuration
        runBlocking {
            val it = cache.values.iterator()
            while (it.hasNext()) {
                val plate = it.next()
                if (plate.lastAccess < threshold) it.remove()
                launch(dispatcher) {
                    writePlate(plate)
                }
            }
        }
    }

    private operator fun get(x: Int, z: Int): Plate {
        val key = x to z
        val plate = cache.getAndMoveToFirst(key)
        if (plate != null) return plate
        return readPlate(x, z).also {
            cache.putAndMoveToFirst(key, it)
            runBlocking {
                while (cache.size > maxSize) {
                    val plate = cache.removeLast()
                    launch(dispatcher) {
                        writePlate(plate)
                    }
                }
            }
        }
    }

    private fun readPlate(x: Int, z: Int): Plate {
        val file = fileForPlate(x, z)
        if (!file.exists()) return Plate(x, z)
        try {
            DataInputStream(LZ4BlockInputStream(file.inputStream())).use {
                return readPlate(x, z, it)
            }
        } catch (e: IOException) {
            Iris.error("Failed to read pregen cache $file")
            e.printStackTrace()
            Iris.reportError(e)
        }
        return Plate(x, z)
    }

    private fun writePlate(plate: Plate) {
        if (!plate.dirty) return
        val file = fileForPlate(plate.x, plate.z)
        try {
            IO.write(file, { DataOutputStream(LZ4BlockOutputStream(it)) }, plate::write)
            plate.dirty = false
        } catch (e: IOException) {
            Iris.error("Failed to write preen cache $file")
            e.printStackTrace()
            Iris.reportError(e)
        }
    }

    private fun fileForPlate(x: Int, z: Int): File {
        check(!(!directory.exists() && !directory.mkdirs())) { "Cannot create directory: " + directory.absolutePath }
        return File(directory, "c.$x.$z.lz4b")
    }

    private class Plate(
        val x: Int,
        val z: Int,
        private var count: Short = 0,
        private var regions: Array<Region?>? = arrayOfNulls(1024)
    ) {
        var dirty: Boolean = false
        var lastAccess: Long = System.currentTimeMillis()

        fun cache(x: Int, z: Int, predicate: Region.() -> Boolean): Boolean {
            lastAccess = System.currentTimeMillis()
            if (count == SIZE) return false
            val region = regions!!.run { this[x * 32 + z] ?: Region().also { this[x * 32 + z] = it } }
            if (!region.predicate()) return false
            if (++count == SIZE) regions = null
            dirty = true
            return true
        }

        fun isCached(x: Int, z: Int, predicate: Region.() -> Boolean): Boolean {
            lastAccess = System.currentTimeMillis()
            if (count == SIZE) return true
            val region = regions!![x * 32 + z] ?: return false
            return region.predicate()
        }

        fun write(dos: DataOutput) {
            Varint.writeSignedVarInt(count.toInt(), dos)
            regions?.forEach {
                dos.writeBoolean(it == null)
                it?.write(dos)
            }
        }
    }

    private class Region(
        private var count: Short = 0,
        private var words: LongArray? = LongArray(64)
    ) {
        fun cache(): Boolean {
            if (count == SIZE) return false
            count = SIZE
            words = null
            return true
        }

        fun cache(x: Int, z: Int): Boolean {
            if (count == SIZE) return false
            val words = words ?: return false
            val i = x * 32 + z
            val w = i shr 6
            val b = 1L shl (i and 63)

            val cur = (words[w] and b) != 0L
            if (cur) return false

            if (++count == SIZE) {
                this.words = null
                return true
            } else {
                words[w] = words[w] or b
                return false
            }
        }

        fun isCached(): Boolean = count == SIZE
        fun isCached(x: Int, z: Int): Boolean {
            val i = x * 32 + z
            return count == SIZE || (words!![i shr 6] and (1L shl (i and 63))) != 0L
        }

        @Throws(IOException::class)
        fun write(dos: DataOutput) {
            Varint.writeSignedVarInt(count.toInt(), dos)
            words?.forEach { Varint.writeUnsignedVarLong(it, dos) }
        }
    }

    companion object {
        private val dispatcher = Dispatchers.IO.limitedParallelism(4)
        private const val SIZE: Short = 1024

        @Throws(IOException::class)
        private fun readPlate(x: Int, z: Int, din: DataInput): Plate {
            val count = Varint.readSignedVarInt(din)
            if (count == 1024) return Plate(x, z, SIZE,  null)
            return Plate(x, z, count.toShort(), Array(1024) {
                if (din.readBoolean()) null
                else readRegion(din)
            })
        }

        @Throws(IOException::class)
        private fun readRegion(din: DataInput): Region {
            val count = Varint.readSignedVarInt(din)
            return if (count == 1024) Region(SIZE, null)
            else Region(count.toShort(), LongArray(64) { Varint.readUnsignedVarLong(din) })
        }
    }
}