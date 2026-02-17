package art.arcane.iris.engine.mantle

import art.arcane.iris.Iris
import art.arcane.iris.core.IrisSettings
import art.arcane.iris.core.nms.container.Pair
import art.arcane.iris.engine.framework.Engine
import art.arcane.iris.util.context.ChunkContext
import art.arcane.iris.util.misc.RegenRuntime
import art.arcane.iris.util.matter.TileWrapper
import art.arcane.volmlib.util.documentation.ChunkCoordinates
import art.arcane.iris.util.mantle.Mantle
import art.arcane.volmlib.util.mantle.flag.MantleFlag
import art.arcane.iris.util.parallel.MultiBurst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.block.data.BlockData
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.min

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
        val threadName = Thread.currentThread().name
        val regenThread = threadName.startsWith("Iris-Regen-")
        val traceRegen = regenThread && IrisSettings.get().general.isDebug
        val forceRegen = regenThread
        val regenPassKey = if (forceRegen) resolveRegenPassKey(threadName) else null
        val optimizedRegen = forceRegen && !IrisSettings.get().general.isDebug && regenPassKey != null
        val writeRadius = if (optimizedRegen) min(radius, realRadius) else radius
        val clearedChunks = if (optimizedRegen) getRegenPassSet(regenClearedChunksByPass, regenPassKey!!) else HashSet<Long>()
        val plannedChunks = if (optimizedRegen) getRegenPassSet(regenPlannedChunksByPass, regenPassKey!!) else null

        if (optimizedRegen) {
            touchRegenPass(regenPassKey!!)
        }

        if (traceRegen) {
            Iris.info("Regen matter start: center=$x,$z radius=$radius realRadius=$realRadius writeRadius=$writeRadius multicore=$multicore components=${components.size} optimized=$optimizedRegen passKey=${regenPassKey ?: "none"} thread=$threadName")
        }

        mantle.write(engine.mantle, x, z, writeRadius, multicore).use { writer ->
            for (pair in components) {
                val rawPassRadius = pair.b
                val passRadius = if (optimizedRegen) min(rawPassRadius, writeRadius) else rawPassRadius
                val passFlags = pair.a.joinToString(",") { it.flag.toString() }
                val passFlagKey = if (optimizedRegen) "$regenPassKey|$passFlags" else null
                val generatedChunks = if (passFlagKey != null) getRegenPassSet(regenGeneratedChunksByPass, passFlagKey) else null
                var visitedChunks = 0
                var clearedCount = 0
                var plannedSkipped = 0
                var componentSkipped = 0
                var componentForcedReset = 0
                var launchedLayers = 0
                var dedupSkipped = 0

                if (passFlagKey != null) {
                    touchRegenPass(passFlagKey)
                }
                if (traceRegen) {
                    Iris.info("Regen matter pass start: center=$x,$z passRadius=$passRadius rawPassRadius=$rawPassRadius flags=[$passFlags]")
                }

                runBlocking {
                    radius(x, z, passRadius) { passX, passZ ->
                        visitedChunks++
                        val passKey = chunkKey(passX, passZ)
                        if (generatedChunks != null && !generatedChunks.add(passKey)) {
                            dedupSkipped++
                            return@radius
                        }

                        val mc = writer.acquireChunk(passX, passZ)
                        if (forceRegen) {
                            if (clearedChunks.add(passKey)) {
                                mc.deleteSlices(BlockData::class.java)
                                mc.deleteSlices(String::class.java)
                                mc.deleteSlices(TileWrapper::class.java)
                                mc.flag(MantleFlag.PLANNED, false)
                                clearedCount++
                            }
                        }

                        if (!forceRegen && mc.isFlagged(MantleFlag.PLANNED)) {
                            plannedSkipped++
                            return@radius
                        }

                        for (c in pair.a) {
                            if (!forceRegen && mc.isFlagged(c.flag)) {
                                componentSkipped++
                                continue
                            }
                            if (forceRegen && mc.isFlagged(c.flag)) {
                                mc.flag(c.flag, false)
                                componentForcedReset++
                            }

                            launchedLayers++

                            launch(multicore) {
                                mc.raiseFlagSuspend(c.flag) {
                                    c.generateLayer(writer, passX, passZ, context)
                                }
                            }
                        }
                    }
                }

                if (traceRegen) {
                    Iris.info("Regen matter pass done: center=$x,$z passRadius=$passRadius rawPassRadius=$rawPassRadius visited=$visitedChunks cleared=$clearedCount dedupSkipped=$dedupSkipped plannedSkipped=$plannedSkipped componentSkipped=$componentSkipped componentForcedReset=$componentForcedReset launchedLayers=$launchedLayers flags=[$passFlags]")
                }
            }

            radius(x, z, realRadius) { realX, realZ ->
                val realKey = chunkKey(realX, realZ)
                if (plannedChunks != null && !plannedChunks.add(realKey)) {
                    return@radius
                }
                writer.acquireChunk(realX, realZ)
                    .flag(MantleFlag.PLANNED, true)
            }
        }

        if (traceRegen) {
            Iris.info("Regen matter done: center=$x,$z markedRealRadius=$realRadius forceRegen=$forceRegen")
        }
    }

    private inline fun radius(x: Int, z: Int, radius: Int, crossinline task: (Int, Int) -> Unit) {
        for (i in -radius..radius) {
            for (j in -radius..radius) {
                task(x + i, z + j)
            }
        }
    }

    companion object {
        private val dispatcher = MultiBurst.burst.dispatcher//.limitedParallelism(128, "Mantle")
        private const val regenPassCacheTtlMs = 600000L
        private val regenGeneratedChunksByPass = ConcurrentHashMap<String, MutableSet<Long>>()
        private val regenClearedChunksByPass = ConcurrentHashMap<String, MutableSet<Long>>()
        private val regenPlannedChunksByPass = ConcurrentHashMap<String, MutableSet<Long>>()
        private val regenPassTouchedMs = ConcurrentHashMap<String, Long>()

        private fun CoroutineScope.launch(multicore: Boolean, block: suspend CoroutineScope.() -> Unit) =
            launch(if (multicore) dispatcher else EmptyCoroutineContext, block = block)

        private fun chunkKey(x: Int, z: Int): Long {
            return (x.toLong() shl 32) xor (z.toLong() and 0xffffffffL)
        }

        private fun getRegenPassSet(store: ConcurrentHashMap<String, MutableSet<Long>>, passKey: String): MutableSet<Long> {
            return store.computeIfAbsent(passKey) { ConcurrentHashMap.newKeySet<Long>() }
        }

        private fun resolveRegenPassKey(threadName: String): String? {
            val runtimeKey = RegenRuntime.getRunId()
            if (!runtimeKey.isNullOrBlank()) {
                return runtimeKey
            }
            if (!threadName.startsWith("Iris-Regen-")) {
                return null
            }

            val suffix = threadName.substring("Iris-Regen-".length)
            val lastDash = suffix.lastIndexOf('-')
            if (lastDash <= 0) {
                return suffix
            }
            return suffix.substring(0, lastDash)
        }

        private fun touchRegenPass(passKey: String) {
            val now = System.currentTimeMillis()
            regenPassTouchedMs[passKey] = now
            if (regenPassTouchedMs.size <= 64) {
                return
            }

            val iterator = regenPassTouchedMs.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value <= regenPassCacheTtlMs) {
                    continue
                }
                val key = entry.key
                iterator.remove()
                regenGeneratedChunksByPass.remove(key)
                regenClearedChunksByPass.remove(key)
                regenPlannedChunksByPass.remove(key)
            }
        }
    }
}
