package art.arcane.iris.engine.object;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterMarker;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class ObjectMarkerPlacement {

    private IrisObjectPlacement config;
    private PlacementContext context;


    public List<ObjectMarkerPlacement.MarkerPlacement> possibleMarkers() {
            List<ObjectMarkerPlacement.MarkerPlacement>  placements = new ArrayList<>();
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
            return placements;
    }

    public List<ObjectMarkerPlacement.MarkerPlacement> matchMarkers(List<ObjectMarkerPlacement.MarkerPlacement> placements, PlatformBlockState state) {
        List<ObjectMarkerPlacement.MarkerPlacement> matching = new ArrayList<>();
        for (ObjectMarkerPlacement.MarkerPlacement markerPlacement : placements) {
            if (markerPlacement.matches(state)) {
                matching.add(markerPlacement);
            }
        }
        return matching;
    }

    public void placeMarkers(List<ObjectMarkerPlacement.MarkerPlacement> placements, RNG rng) {
        for (ObjectMarkerPlacement.MarkerPlacement markerPlacement : placements) {
            int max = markerPlacement.config.getMaximumMarkers();
            for (BlockPosition position : markerPlacement.candidates
                    .shuffleCopy(rng.nextParallelRNG(markerPlacement.salt))) {
                if (max <= 0) {
                    break;
                }

                boolean firstAboveAir = B.isAir(context.placer.get(position.getX(), position.getY() + 1, position.getZ()));
                boolean secondAboveAir = B.isAir(context.placer.get(position.getX(), position.getY() + 2, position.getZ()));
                if (!markerPlacement.marker.isEmptyAbove() || (firstAboveAir && secondAboveAir)) {
                    context.placer.setData(position.getX(), position.getY(), position.getZ(),
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

