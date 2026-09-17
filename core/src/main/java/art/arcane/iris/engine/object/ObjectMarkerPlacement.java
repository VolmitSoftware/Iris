package art.arcane.iris.engine.object;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterMarker;

import java.util.ArrayList;
import java.util.List;

class ObjectMarkerPlacement {

    private IrisObjectPlacement config;
    private PlacementContext context;
    private final IObjectPlacer placer;
    private RNG markerRng;
    List<ObjectMarkerPlacement.MarkerPlacement> placements;

    public ObjectMarkerPlacement(PlacementContext context, PlacementState state) {
        this.config = state.config;
        this.placer = state.placer;
        this.context = context;
        this.markerRng = context.rng.nextParallelRNG(((((long) context.x) << 32) | (context.z & 0xFFFFFFFFL)) * 31L + context.yv);
        this.placements = new ArrayList<>();
    }

    public void loadPossibleMarkers() {
        // Marker selection persists into the mantle, so it must be seed-deterministic.
        // Derive a side stream keyed on the placement position instead of consuming the
        // caller's rng: consuming draws there would shift every later placement in the
        // chunk and invalidate existing worlds/goldenhashes.
        int index = 0;
        for (IrisObjectMarker markerConfig : config.getMarkers()) {
            IrisMarker marker = context.self.getLoader().getMarkerLoader().load(markerConfig.getMarker());
            int markerSalt = index++;
            if (marker != null) {
                placements.add(new ObjectMarkerPlacement.MarkerPlacement(markerConfig, marker,
                        markerConfig.getMark(context.rdata), markerSalt));
            }
        }
    }

    public List<ObjectMarkerPlacement.MarkerPlacement> matchMarkers(PlatformBlockState state) {
        List<ObjectMarkerPlacement.MarkerPlacement> matching = new ArrayList<>();
        for (ObjectMarkerPlacement.MarkerPlacement markerPlacement : placements) {
            if (markerPlacement.matches(state)) {
                matching.add(markerPlacement);
            }
        }
        return matching;
    }

    public void placeMarkers() {
        for (ObjectMarkerPlacement.MarkerPlacement markerPlacement : placements) {
            int max = markerPlacement.config.getMaximumMarkers();
            for (BlockPosition position : markerPlacement.candidates
                    .shuffleCopy(markerRng.nextParallelRNG(markerPlacement.salt))) {
                if (max <= 0) {
                    break;
                }

                boolean firstAboveAir = B.isAir(placer.get(position.getX(), position.getY() + 1, position.getZ()));
                boolean secondAboveAir = B.isAir(placer.get(position.getX(), position.getY() + 2, position.getZ()));
                if (!markerPlacement.marker.isEmptyAbove() || (firstAboveAir && secondAboveAir)) {
                    placer.setData(position.getX(), position.getY(), position.getZ(),
                            new MatterMarker(markerPlacement.config.getMarker()));
                    max--;
                }
            }
        }

    }

    public static final class MarkerPlacement {
        final IrisObjectMarker config;
        final IrisMarker marker;
        final Iterable<PlatformBlockState> targets;
        final int salt;
        final KList<BlockPosition> candidates = new KList<>();

        MarkerPlacement(IrisObjectMarker config, IrisMarker marker,
                        Iterable<PlatformBlockState> targets, int salt) {
            this.config = config;
            this.marker = marker;
            this.targets = targets;
            this.salt = salt;
        }

        boolean matches(PlatformBlockState state) {
            for (PlatformBlockState target : targets) {
                if (config.isExact()
                        ? target.matches(state)
                        : IrisObjectShaping.materialKey(target).equals(IrisObjectShaping.materialKey(state))) {
                    return true;
                }
            }
            return false;
        }
    }

}
