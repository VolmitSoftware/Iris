package art.arcane.iris.generation.decoration.tree;

import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.BlockPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TreeGrounding(List<Root> roots, int maxExtension, Set<TreeBlockCanvas.Vec> canopy) {
    public TreeGrounding {
        roots = List.copyOf(roots);
        maxExtension = Math.max(0, Math.min(16, maxExtension));
        canopy = Set.copyOf(canopy);
    }

    public static TreeGrounding create(List<Root> roots, int maxExtension, Set<TreeBlockCanvas.Vec> canopy) {
        Map<Long, Root> columns = new HashMap<>();
        for (Root root : roots) {
            long key = ((long) root.x() << 32) | (root.z() & 0xffffffffL);
            Root previous = columns.get(key);
            if (previous == null || root.y() < previous.y()) {
                columns.put(key, root);
            }
        }
        List<Root> lowest = new ArrayList<>(columns.values());
        lowest.sort(Comparator.comparingInt(Root::x).thenComparingInt(Root::z));
        return new TreeGrounding(lowest, maxExtension, canopy);
    }

    public boolean isCanopy(int x, int y, int z) {
        return canopy.contains(new TreeBlockCanvas.Vec(x, y, z));
    }

    public KList<IrisBlockVector> supportOffsets() {
        KList<IrisBlockVector> offsets = new KList<>();
        for (Root root : roots) {
            offsets.add(new IrisBlockVector(root.x(), root.y(), root.z()));
        }
        return offsets;
    }

    public Plan plan(IObjectPlacer placer, Placement placement) {
        List<Extension> extensions = new ArrayList<>();
        IrisObjectPlacement config = placement.config();
        BlockPosition origin = placement.origin();
        BlockPosition spin = placement.spin();
        int solidDepth = Math.max(1, config.getSurfaceSupportDepth());
        for (Root root : roots) {
            IrisBlockVector offset = config.getRotation().rotate(
                    new IrisBlockVector(root.x(), root.y(), root.z()), spin.getX(), spin.getY(), spin.getZ());
            offset = config.getTranslate().translate(offset, config.getRotation(), spin.getX(), spin.getY(), spin.getZ());
            int x = origin.getX() + (int) Math.round(offset.getX());
            int y = origin.getY() + (int) Math.round(offset.getY());
            int z = origin.getZ() + (int) Math.round(offset.getZ());
            int surface = placer.getHighest(x, z, placement.data(), true);
            int contact = Math.min(surface, y - 1);
            if (y - contact - 1 > maxExtension) {
                return Plan.UNSUPPORTED;
            }
            for (int below = 0; below < solidDepth; below++) {
                if (placer.isCarved(x, contact - below, z) || !placer.isSurfaceSolid(x, contact - below, z)) {
                    return Plan.UNSUPPORTED;
                }
            }
            if (contact < y - 1) {
                NativeBlockState state = config.getRotation().rotate(root.state(), spin.getX(), spin.getY(), spin.getZ());
                if (state == null) {
                    return Plan.UNSUPPORTED;
                }
                Extension extension = new Extension(x, y - 1, contact, z, state);
                if (intersectsStructure(placer, extension)) {
                    return Plan.UNSUPPORTED;
                }
                extensions.add(extension);
            }
        }
        return new Plan(!roots.isEmpty(), List.copyOf(extensions));
    }

    private static boolean intersectsStructure(IObjectPlacer placer, Extension extension) {
        Engine engine = placer.getEngine();
        if (engine == null) {
            return false;
        }
        KList<NativeStructureVolume> volumes = engine.getNativeStructureVolumes(
                extension.x(), extension.z(), extension.x(), extension.z());
        if (volumes == null) {
            return false;
        }
        for (NativeStructureVolume volume : volumes) {
            if (volume.intersects(extension.x(), extension.bottomY() + engine.getMinHeight(), extension.z(),
                    extension.x(), extension.topY() + engine.getMinHeight(), extension.z())) {
                return true;
            }
        }
        return false;
    }

    public record Root(int x, int y, int z, NativeBlockState state) {
    }

    public record Placement(IrisData data, IrisObjectPlacement config, BlockPosition origin, BlockPosition spin) {
    }

    public record Extension(int x, int topY, int bottomY, int z, NativeBlockState state) {
    }

    public record Plan(boolean supported, List<Extension> extensions) {
        private static final Plan UNSUPPORTED = new Plan(false, List.of());
    }
}
