package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.compile.decoration.Placements;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

final class HeightAnchor {
    private static final int SCAN_STEPS = 32;
    private static final int PROBE_LIMIT = 16;

    private HeightAnchor() {
    }

    static Sampling sampling(ObjectPlaceMode mode) {
        return switch (mode) {
            case MIN_HEIGHT, FAST_MIN_HEIGHT, MIN_STILT, FAST_MIN_STILT -> Sampling.MIN;
            case MAX_HEIGHT, FAST_MAX_HEIGHT, STILT, FAST_STILT, ERODE_STILT -> Sampling.MAX;
            default -> Sampling.CENTER;
        };
    }

    static boolean fast(ObjectPlaceMode mode) {
        return switch (mode) {
            case FAST_MIN_HEIGHT, FAST_MAX_HEIGHT, FAST_STILT, FAST_MIN_STILT -> true;
            default -> false;
        };
    }

    static JsonObject scan(Sampling sampling, boolean fast, boolean underwater, TemplateVolume volume, List<Integer> quarters,
                           TemplateColumn shift) {
        boolean lowest = sampling == Sampling.MIN;
        List<Box> boxes = new ArrayList<>();
        for (int quarter : quarters) {
            Box box = Box.of(volume, quarter, shift);
            if (fast) {
                boxes.addAll(box.fastSamples());
            } else {
                boxes.add(box);
            }
        }
        JsonObject top = top(underwater);
        JsonObject match = lowest ? top : Placements.not(top);
        int probeY = lowest ? -1 : 0;
        List<JsonObject> targets = new ArrayList<>(boxes.size());
        for (Box box : outermost(boxes)) {
            targets.add(box.matching(probeY, match.deepCopy()));
        }
        JsonObject scan = Placements.typed("minecraft:environment_scan");
        scan.addProperty("direction_of_search", lowest ? "down" : "up");
        scan.addProperty("max_steps", SCAN_STEPS);
        scan.add("target_condition", Placements.allOf(targets));
        return scan;
    }

    private static JsonObject top(boolean underwater) {
        JsonObject terrain = Placements.allOf(List.of(Placements.solid(0, 0, 0),
                Placements.not(Placements.blockTag("minecraft:leaves", 0, 0, 0)),
                Placements.not(Placements.blockTag("minecraft:logs", 0, 0, 0))));
        return underwater ? terrain : Placements.anyOf(List.of(terrain, Placements.fluids(PlacementCompiler.FLUIDS, 0, 0, 0)));
    }

    private static List<Box> outermost(List<Box> boxes) {
        List<Box> kept = new ArrayList<>(boxes.size());
        for (int index = 0; index < boxes.size(); index++) {
            Box box = boxes.get(index);
            boolean covered = false;
            for (int other = 0; other < boxes.size() && !covered; other++) {
                Box candidate = boxes.get(other);
                covered = other != index && candidate.contains(box) && (!box.contains(candidate) || other < index);
            }
            if (!covered) {
                kept.add(box);
            }
        }
        return kept;
    }

    private record Box(int minX, int maxX, int minZ, int maxZ) {
        static Box of(TemplateVolume volume, int quarter, TemplateColumn shift) {
            TemplateColumn first = TemplateColumn.of(volume, 0, 0, quarter);
            TemplateColumn last = TemplateColumn.of(volume, volume.sizeX() - 1, volume.sizeZ() - 1, quarter);
            return new Box(clamp(Math.min(first.x(), last.x()) + shift.x()), clamp(Math.max(first.x(), last.x()) + shift.x()),
                    clamp(Math.min(first.z(), last.z()) + shift.z()), clamp(Math.max(first.z(), last.z()) + shift.z()));
        }

        List<Box> fastSamples() {
            int stepX = (maxX - minX) / 2 + 1;
            int stepZ = (maxZ - minZ) / 2 + 1;
            List<Box> samples = new ArrayList<>(4);
            for (int x = minX; x <= maxX; x += stepX) {
                for (int z = minZ; z <= maxZ; z += stepZ) {
                    samples.add(new Box(x, x, z, z));
                }
            }
            return samples;
        }

        boolean contains(Box other) {
            return minX <= other.minX && maxX >= other.maxX && minZ <= other.minZ && maxZ >= other.maxZ;
        }

        JsonObject matching(int y, JsonObject match) {
            JsonObject volume = Placements.typed("minecraft:volume_match");
            volume.add("min", FeatureJson.vector(minX, y, minZ));
            volume.add("max", FeatureJson.vector(maxX, y, maxZ));
            volume.add("match", match);
            return volume;
        }

        private static int clamp(int value) {
            return Math.max(-PROBE_LIMIT, Math.min(PROBE_LIMIT, value));
        }
    }

    enum Sampling {
        CENTER,
        MIN,
        MAX
    }
}
