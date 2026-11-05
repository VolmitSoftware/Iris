package art.arcane.iris.modules.objects;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.config.FeatureSettings;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.noise.NoiseSampler;
import com.volmit.noise.NoiseStyle;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The chance and spacing of surface object placements in a world.
 *
 * <p>A separated placement rolls one candidate spot per chunk. The candidate stands only when no candidate of a lower rank,
 * from a placement with the same objects, lies closer than the separation. Spots and ranks come from the world seed, the
 * chunk and the objects alone, so every chunk agrees on them across chunk, biome and region borders, and objects never
 * stand closer than the separation, whatever order chunks generate in.
 */
public final class ObjectSeparation {
    private static final int CHUNK_WIDTH_BLOCKS = 16;
    private static final int CHUNK_SHIFT = 4;
    /** Placements read a chunk's settings at local 8, 8, as Iris always did. */
    private static final int SETTINGS_COLUMN_OFFSET_BLOCKS = 8;
    /** A chunk places the biome's objects before the region's, so the biome's placement speaks for the chunk. */
    private static final List<Level> PLACING_LEVELS = List.of(Level.BIOME, Level.REGION);

    private final PackWorld world;
    private final long seed;
    private final Map<NoiseStyle, NoiseSampler> chanceNoises = new ConcurrentHashMap<>();

    private ObjectSeparation(PackWorld world) {
        this.world = world;
        this.seed = world.seed(IrisSeedPurposes.OBJECT_SEPARATION);
    }

    /** The separation of {@code world}. */
    public static ObjectSeparation of(PackWorld world) {
        return world.state(ObjectSeparation.class, ObjectSeparation::new);
    }

    /** The chance to place at the block column: the placement's chance, multiplied by its chance noise when it has one. */
    public double chanceAt(ObjectPlacement placement, double x, double z) {
        Optional<NoiseStyle> style = placement.chanceStyle();
        if (style.isEmpty()) {
            return placement.chance();
        }
        double noise = chanceNoises.computeIfAbsent(style.get(), declared -> world.resources().noise().sampler(declared, seed)).sample(x, z);
        return placement.chance() * Math.max(0D, Math.min(1D, noise));
    }

    /**
     * The candidate the chunk places for the separated placement; empty when it rolled none or a lower ranked one is too
     * close. {@code settings} tells which placements the neighbouring chunks declare.
     */
    public Optional<Candidate> keptCandidate(int chunkX, int chunkZ, ObjectPlacement placement, FeatureSettings settings) {
        Optional<Candidate> own = candidateOf(chunkX, chunkZ, placement);
        if (own.isEmpty() || isOutranked(own.get(), chunkX, chunkZ, placement, settings)) {
            return Optional.empty();
        }
        return own;
    }

    private boolean isOutranked(Candidate own, int chunkX, int chunkZ, ObjectPlacement placement, FeatureSettings settings) {
        int reach = placement.separation();
        for (int x = Math.floorDiv(own.x() - reach, CHUNK_WIDTH_BLOCKS); x <= Math.floorDiv(own.x() + reach, CHUNK_WIDTH_BLOCKS); x++) {
            for (int z = Math.floorDiv(own.z() - reach, CHUNK_WIDTH_BLOCKS); z <= Math.floorDiv(own.z() + reach, CHUNK_WIDTH_BLOCKS); z++) {
                if (x == chunkX && z == chunkZ) {
                    continue;
                }
                int rivalX = x;
                int rivalZ = z;
                Optional<Candidate> rival = placementOfSameObjectsAt(rivalX, rivalZ, placement.place(), settings)
                        .flatMap(other -> candidateOf(rivalX, rivalZ, other));
                if (rival.isPresent() && rival.get().rank() < own.rank() && rival.get().isCloserThan(own, reach)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Optional<ObjectPlacement> placementOfSameObjectsAt(int chunkX, int chunkZ, List<String> objects, FeatureSettings settings) {
        int x = (chunkX << CHUNK_SHIFT) + SETTINGS_COLUMN_OFFSET_BLOCKS;
        int z = (chunkZ << CHUNK_SHIFT) + SETTINGS_COLUMN_OFFSET_BLOCKS;
        SettingsStack<ObjectSettings> declared = settings.at(ObjectSettings.class, x, z);
        for (Level level : PLACING_LEVELS) {
            Optional<ObjectPlacement> placing = declared.level(level).flatMap(placements -> separatedPlacing(placements, objects));
            if (placing.isPresent()) {
                return placing;
            }
        }
        return Optional.empty();
    }

    private static Optional<ObjectPlacement> separatedPlacing(ObjectSettings settings, List<String> objects) {
        return settings.objects().stream()
                .filter(placement -> placement.isSeparated() && placement.carvingSupport().supportsSurface() && placement.place().equals(objects))
                .findFirst();
    }

    private Optional<Candidate> candidateOf(int chunkX, int chunkZ, ObjectPlacement placement) {
        RNG rng = new RNG(chunkSeed(chunkX, chunkZ, placement.place().hashCode()));
        int x = (chunkX << CHUNK_SHIFT) + rng.nextInt(CHUNK_WIDTH_BLOCKS);
        int z = (chunkZ << CHUNK_SHIFT) + rng.nextInt(CHUNK_WIDTH_BLOCKS);
        if (!rng.chance(chanceAt(placement, x, z))) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(x, z, rng.nextLong(), rng));
    }

    // Each input is mixed in after the previous one: XORing scaled coordinates gave chunk x,z and -x,-z the same seed
    // whenever both were odd.
    private long chunkSeed(int chunkX, int chunkZ, int objectsHash) {
        return splitMix(splitMix(splitMix(seed + objectsHash) + chunkX) + chunkZ);
    }

    private static long splitMix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    /** Where a chunk would place, in block coordinates, with the random that picks and places the object there. */
    public record Candidate(int x, int z, long rank, RNG rng) {
        boolean isCloserThan(Candidate other, int blocks) {
            long dx = x - other.x;
            long dz = z - other.z;
            return dx * dx + dz * dz < (long) blocks * blocks;
        }
    }
}
