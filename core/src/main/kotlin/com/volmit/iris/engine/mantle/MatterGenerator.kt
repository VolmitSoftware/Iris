package com.volmit.iris.engine.mantle

import com.volmit.iris.core.IrisSettings
import com.volmit.iris.core.nms.container.Pair
import com.volmit.iris.engine.framework.Engine
import com.volmit.iris.util.context.ChunkContext
import com.volmit.iris.util.documentation.ChunkCoordinates
import com.volmit.iris.util.mantle.Mantle
import com.volmit.iris.util.mantle.MantleChunk
import com.volmit.iris.util.mantle.flag.MantleFlag
import com.volmit.iris.util.parallel.MultiBurst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext

interface MatterGenerator {
    val engine: Engine
    val mantle: Mantle
    val radius: Int
    val components: List<Pair<List<MantleComponent>, Int>>

    @ChunkCoordinates
    fun generateMatter(x: Int, z: Int, multicore: Boolean, context: ChunkContext) {
        if (!engine.dimension.isUseMantle || mantle.hasFlag(x, z, MantleFlag.PLANNED))
            return
        val multicore = multicore || IrisSettings.get().generator.isUseMulticoreMantle

        mantle.write(engine.mantle, x, z, radius * 2).use { writer ->
            for (pair in components) {
                radius(x, z, pair.b, { x, z ->
                    for (c in pair.a) {
                        emit(Triple(x, z, c))
                    }
                }, { (x, z, c) -> launch(multicore) {
                    acquireChunk(multicore, writer, x, z)
                        .raiseFlagSuspend(MantleFlag.PLANNED, c.flag) {
                            if (c.isEnabled) c.generateLayer(writer, x, z, context)
                        }
                }})
            }

            radius(x, z, radius, { x, z ->
                emit(Pair(x, z))
            }, {
                writer.acquireChunk(it.a, it.b)
                    .flag(MantleFlag.PLANNED, true)
            })
        }
    }

    private fun <T> radius(x: Int, z: Int, radius: Int, collector: suspend FlowCollector<T>.(Int, Int) -> Unit, task: suspend CoroutineScope.(T) -> Unit) = runBlocking {
        flow {
            for (i in -radius..radius) {
                for (j in -radius..radius) {
                    collector(x + i, z + j)
                }
            }
        }.collect { task(it) }
    }

    companion object {
        private val dispatcher = MultiBurst.burst.dispatcher
        private fun CoroutineScope.launch(multicore: Boolean, block: suspend CoroutineScope.() -> Unit) =
            launch(if (multicore) dispatcher else EmptyCoroutineContext, block = block)

        private suspend fun CoroutineScope.acquireChunk(multicore: Boolean, writer: MantleWriter, x: Int, z: Int): MantleChunk {
            return if (multicore) async(Dispatchers.IO) { writer.acquireChunk(x, z) }.await()
            else writer.acquireChunk(x, z)
        }
    }
}