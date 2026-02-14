package art.arcane.iris.util.context

import art.arcane.iris.engine.IrisComplex
import art.arcane.iris.engine.`object`.IrisBiome
import art.arcane.iris.engine.`object`.IrisRegion
import art.arcane.iris.util.parallel.MultiBurst
import kotlinx.coroutines.*
import org.bukkit.block.data.BlockData

class ChunkContext @JvmOverloads constructor(
    val x: Int,
    val z: Int,
    c: IrisComplex,
    cache: Boolean = true,
) {
    val height: ChunkedDataCache<Double> = ChunkedDataCache(c.heightStream, x, z, cache)
    val biome: ChunkedDataCache<IrisBiome> = ChunkedDataCache(c.trueBiomeStream, x, z, cache)
    val cave: ChunkedDataCache<IrisBiome> = ChunkedDataCache(c.caveBiomeStream, x, z, cache)
    val rock: ChunkedDataCache<BlockData> = ChunkedDataCache(c.rockStream, x, z, cache)
    val fluid: ChunkedDataCache<BlockData> = ChunkedDataCache(c.fluidStream, x, z, cache)
    val region: ChunkedDataCache<IrisRegion> = ChunkedDataCache(c.regionStream, x, z, cache)

    init {
        if (cache) runBlocking {
            val dispatcher = MultiBurst.burst.dispatcher

            launch { height.fill(dispatcher) }
            launch { biome.fill(dispatcher) }
            launch { cave.fill(dispatcher) }
            launch { rock.fill(dispatcher) }
            launch { fluid.fill(dispatcher) }
            launch { region.fill(dispatcher) }
        }
    }
}
