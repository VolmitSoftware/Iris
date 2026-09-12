package art.arcane.iris.generation.block;

import art.arcane.iris.platform.bukkit.BukkitBlockState;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.platform.bukkit.BukkitBlockResolution;
import org.bukkit.block.data.BlockData;

import java.util.Objects;
import java.util.function.Function;

public final class BlockDataMergeSupport {
    private static volatile StateMerger PLATFORM_MERGER = null;

    private BlockDataMergeSupport() {
    }

    public interface StateMerger {
        PlatformBlockState merge(PlatformBlockState base, PlatformBlockState update);
    }

    public static synchronized StateMerger bindPlatformMerger(StateMerger merger) {
        StateMerger previous = PLATFORM_MERGER;
        PLATFORM_MERGER = Objects.requireNonNull(merger, "merger");
        return previous;
    }

    public static synchronized void restorePlatformMerger(StateMerger merger) {
        PLATFORM_MERGER = merger;
    }

    public static PlatformBlockState merge(PlatformBlockState base, PlatformBlockState update) {
        StateMerger merger = PLATFORM_MERGER;
        if (merger != null) {
            return merger.merge(base, update);
        }
        BlockData merged = merge((BlockData) base.nativeHandle(), (BlockData) update.nativeHandle(), BukkitBlockResolution::get);
        return merged == null ? null : BukkitBlockState.of(merged);
    }

    static BlockData merge(BlockData base, BlockData update, Function<String, BlockData> resolver) {
        try {
            return base.merge(update);
        } catch (IllegalArgumentException e) {
            BlockData normalizedBase = resolve(base, resolver);
            BlockData normalizedUpdate = resolve(update, resolver);

            if (normalizedBase != null && normalizedUpdate != null) {
                try {
                    return normalizedBase.merge(normalizedUpdate);
                } catch (IllegalArgumentException ignored) {
                    return normalizedUpdate;
                }
            }

            if (normalizedUpdate != null) {
                return normalizedUpdate;
            }

            return update;
        }
    }

    private static BlockData resolve(BlockData data, Function<String, BlockData> resolver) {
        if (data == null || resolver == null) {
            return null;
        }

        String serialized = data.getAsString(false);
        if (serialized == null || serialized.isBlank()) {
            return null;
        }

        return resolver.apply(serialized);
    }
}
