package art.arcane.iris.structure.object;

import art.arcane.iris.world.entity.IrisMarker;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.VectorMap;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;

import java.util.stream.StreamSupport;

/** Chooses marker positions from the object's own blocks, indexed like the placement bake. */
final class ObjectMarkerPlacement {
    private ObjectMarkerPlacement() {
    }

    /** Callers hold the object read lock, so the source cursor matches the bake order. */
    static String[] resolve(PlacementContext context, IrisObjectPlacement config, ObjectPlacementBake bake) {
        IrisObject self = context.self;
        VectorMap<NativeBlockState> blocks = self.blocks;
        KMap<IrisBlockVector, String> markers = new KMap<>();
        KList<IrisBlockVector> list = StreamSupport.stream(blocks.keys().spliterator(), false)
                .collect(KList.collector());
        // Marker selection persists into the mantle, so it must be seed-deterministic.
        // Derive a side stream keyed on the placement position instead of consuming the
        // caller's rng: consuming draws there would shift every later placement in the
        // chunk and invalidate existing worlds/goldenhashes.
        RNG markerRng = context.rng.nextParallelRNG(((((long) context.x) << 32) | (context.z & 0xFFFFFFFFL)) * 31L + context.yv);

        int markerIndex = 0;
        for (IrisObjectMarker j : config.getMarkers()) {
            IrisMarker marker = self.getLoader().getMarkerLoader().load(j.getMarker());
            int markerSalt = markerIndex++;

            if (marker == null || marker.isCompatExcluded()) {
                continue;
            }

            int max = j.getMaximumMarkers();
            for (IrisBlockVector i : list.shuffleCopy(markerRng.nextParallelRNG(markerSalt))) {
                if (max <= 0) {
                    break;
                }

                NativeBlockState data = blocks.get(i);
                if (data == null) {
                    continue;
                }

                for (NativeBlockState k : j.getMark(context.rdata)) {
                    if (max <= 0) {
                        break;
                    }

                    if (j.isExact() ? k.matches(data) : IrisObjectShaping.materialKey(k).equals(IrisObjectShaping.materialKey(data))) {
                        boolean firstAboveEmpty = !blocks.containsKey((IrisBlockVector) i.clone().add(new IrisBlockVector(0, 1, 0)));
                        boolean secondAboveEmpty = !blocks.containsKey((IrisBlockVector) i.clone().add(new IrisBlockVector(0, 2, 0)));

                        if (!marker.isEmptyAbove() || (firstAboveEmpty && secondAboveEmpty)) {
                            markers.put(i, j.getMarker());
                            max--;
                        }
                    }
                }
            }
        }

        String[] resolved = new String[bake.count()];
        VectorMap<NativeBlockState>.Cursor cursor = blocks.cursor();
        int index = 0;
        while (index < resolved.length && cursor.next()) {
            resolved[index++] = markers.get(cursor.key());
        }
        return resolved;
    }
}
