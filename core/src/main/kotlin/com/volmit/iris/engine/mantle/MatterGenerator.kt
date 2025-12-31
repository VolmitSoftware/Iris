package com.volmit.iris.engine.mantle

import com.volmit.iris.core.IrisSettings
import com.volmit.iris.core.nms.container.Pair
import com.volmit.iris.engine.framework.Engine
import com.volmit.iris.util.context.ChunkContext
import com.volmit.iris.util.documentation.ChunkCoordinates
import com.volmit.iris.util.mantle.Mantle
import com.volmit.iris.util.mantle.flag.MantleFlag
import com.volmit.iris.util.parallel.MultiBurst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext

interface MatterGenerator {
    val engine: Engine
    val mantle: Mantle
    val radius: Int
    val realRadius: Int
    val components: List<Pair<List<MantleComponent>, Int>>

    @ChunkCoordinates
    fun generateMatter(x: Int, z: Int, multicore: Boolean, context: ChunkContext) {
        if (!engine.dimension.isUseMantle) return
        val multicore = multicore || IrisSettings.get().generator.isUseMulticoreMantle

        mantle.write(engine.mantle, x, z, radius, multicore).use { writer ->
            for (pair in components) {
                radius(x, z, pair.b) { x, z ->
                    for (c in pair.a) {
                        launch(multicore) {
                            writer.acquireChunk(x, z)
                                .raiseFlagSuspend(MantleFlag.PLANNED, c.flag) {
                                    c.generateLayer(writer, x, z, context)
                                }
                        }
                    }
                }
            }

            radius(x, z, realRadius) { x, z ->
                writer.acquireChunk(x, z)
                    .flag(MantleFlag.PLANNED, true)
            }
        }
    }

    private inline fun radius(x: Int, z: Int, radius: Int, crossinline task: suspend CoroutineScope.(Int, Int) -> Unit) = runBlocking {
        for (i in -radius..radius) {
            for (j in -radius..radius) {
                task(x + i, z + j)
            }
        }
    }

    companion object {
        private val dispatcher = MultiBurst.burst.dispatcher//.limitedParallelism(128, "Mantle")
        private fun CoroutineScope.launch(multicore: Boolean, block: suspend CoroutineScope.() -> Unit) =
            launch(if (multicore) dispatcher else EmptyCoroutineContext, block = block)
    }
}