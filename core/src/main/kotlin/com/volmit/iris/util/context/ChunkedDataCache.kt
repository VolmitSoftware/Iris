package com.volmit.iris.util.context

import com.volmit.iris.util.documentation.BlockCoordinates
import com.volmit.iris.util.stream.ProceduralStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.coroutines.CoroutineContext

class ChunkedDataCache<T> private constructor(
    private val x: Int,
    private val z: Int,
    private val stream: ProceduralStream<T?>,
    private val cache: Boolean
) {
    private val data = arrayOfNulls<Any>(if (cache) 256 else 0)

    @JvmOverloads
    @BlockCoordinates
    constructor(stream: ProceduralStream<T?>, x: Int, z: Int, cache: Boolean = true) : this(x, z, stream, cache)

    suspend fun fill(context: CoroutineContext = Dispatchers.Default) {
        if (!cache) return

        supervisorScope {
            for (i in 0 until 16) {
                for (j in 0 until 16) {
                    launch(context) {
                        val t = stream.get((x + i).toDouble(), (z + j).toDouble())
                        data[(j * 16) + i] = t
                    }
                }
            }
        }
    }

    @BlockCoordinates
    fun get(x: Int, z: Int): T? {
        if (!cache) {
            return stream.get((this.x + x).toDouble(), (this.z + z).toDouble())
        }

        val t = data[(z * 16) + x] as? T
        return t ?: stream.get((this.x + x).toDouble(), (this.z + z).toDouble())
    }
}
