package art.arcane.iris.engine.object.marker;

import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.placement.PlacementContext;
import art.arcane.iris.engine.object.placement.PlacementState;
import art.arcane.iris.engine.object.prefab.IrisObjectShaping;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterMarker;

import java.util.ArrayList;
import java.util.List;

public class ObjectMarkerPlacement {

    private IrisObjectPlacement config;
    private PlacementContext context;
    private final IObjectPlacer placer;
    private RNG markerRng;
    List<ObjectMarkerPlacement.MarkerPlacement> placements;

    public ObjectMarkerPlacement(PlacementContext context, PlacementState state) {
        this.config = state.getConfig();
        this.placer = state.getPlacer();
        this.context = context;
        this.markerRng = context.getRng().nextParallelRNG(((((long) context.getX()) << 32) | (context.getZ() & 0xFFFFFFFFL)) * 31L + context.getYv());
        this.placements = new ArrayList<>();
    }

    public void loadPossibleMarkers() {
        // Marker selection persists into the mantle, so it must be seed-deterministic.
        // Derive a side stream keyed on the placement position instead of consuming the
        // caller's rng: consuming draws there would shift every later placement in the
        // chunk and invalidate existing worlds/goldenhashes.
        int index = 0;
        for (IrisObjectMarker markerConfig : config.getMarkers()) {
            IrisMarker marker = context.getSelf().getLoader().getMarkerLoader().load(markerConfig.getMarker());
            int markerSalt = index++;
            if (marker != null) {
                placements.add(new ObjectMarkerPlacement.MarkerPlacement(markerConfig, marker,
                        markerConfig.getMark(context.getRdata()), markerSalt));
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
        public final IrisObjectMarker config;
        public final IrisMarker marker;
        public final Iterable<PlatformBlockState> targets;
        public final int salt;
        public final KList<BlockPosition> candidates = new KList<>();

        public MarkerPlacement(IrisObjectMarker config, IrisMarker marker,
                        Iterable<PlatformBlockState> targets, int salt) {
            this.config = config;
            this.marker = marker;
            this.targets = targets;
            this.salt = salt;
        }

        public boolean matches(PlatformBlockState state) {
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
