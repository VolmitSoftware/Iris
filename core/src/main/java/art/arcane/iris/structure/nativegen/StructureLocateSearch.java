package art.arcane.iris.structure.nativegen;

import art.arcane.volmlib.nativelib.terrain.structure.StructureLocateCandidate;
import art.arcane.volmlib.nativelib.terrain.structure.StructureLocateSearchAccess;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.volmlib.nativelib.terrain.StructureLocateProbe;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class StructureLocateSearch<S> implements StructureLocateSearchAccess<S, NativeStructureOwnershipRecord> {

    private final Options<S> options;
    private final Set<Long> rejectedChunks = new HashSet<>();

    public StructureLocateSearch(Options<S> options) {
        this.options = Objects.requireNonNull(options, "Structure locate options must not be null");
    }

    public IrisStructureLocator.LocateResult predict() {
        if (rejectedChunks.size() >= MAX_SELECTED_CANDIDATE_RETRIES) {
            return new IrisStructureLocator.LocateResult(
                    IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED, 0, 0, 0);
        }
        return IrisStructureLocator.locateInPlacementRings(
                options.engine(), options.structureKey(), options.blockX(), options.blockZ(), options.radius(),
                (chunkX, chunkZ) -> !rejectedChunks.contains(chunkKey(chunkX, chunkZ))
                        && options.probe().accepts(chunkX, chunkZ));
    }

    public VerifiedStart<S> verify(StructureLocateCandidate result) {
        S start = options.probe().verifySelected(result.originX() >> 4, result.originZ() >> 4);
        if (start == null) {
            return null;
        }
        NativeStructureOwnershipRecord ownership = options.ownership().apply(start);
        return ownership == null ? null : new VerifiedStart<>(start, ownership);
    }

    public void reject(StructureLocateCandidate result) {
        rejectedChunks.add(chunkKey(result.originX() >> 4, result.originZ() >> 4));
    }

    public void reference(StructureLocateSearchAccess.Verified<S, NativeStructureOwnershipRecord> verified) {
        options.probe().reference(verified.start());
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkZ << 32) | (chunkX & 0xffffffffL);
    }

    public record Options<S>(Engine engine, String structureKey, int blockX, int blockZ, int radius,
                             StructureLocateProbe<S> probe,
                             Function<S, NativeStructureOwnershipRecord> ownership) {
    }

    public record VerifiedStart<S>(S start, NativeStructureOwnershipRecord ownership)
            implements StructureLocateSearchAccess.Verified<S, NativeStructureOwnershipRecord> {
    }
}
