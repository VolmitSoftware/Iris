package art.arcane.iris.generation.hydrology.config;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.studio.scene.SceneBlocks;
import art.arcane.iris.studio.scene.SceneEntry;
import art.arcane.iris.studio.scene.SceneForm;
import art.arcane.iris.studio.scene.SceneRequest;
import art.arcane.iris.studio.scene.SceneSizes;
import art.arcane.iris.studio.scene.SceneView;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/** The blocks of each named fluid of the dimension's hydrology, in a pool lined with its seam. */
public final class FluidProfileSceneView implements SceneView {
    private static final SceneSizes SIZES = new SceneSizes(List.of(16, 32, 64), 32);
    private static final int VARIANTS = 4;
    private static final int VARIANT_SPACING_BLOCKS = 1024;
    private static final int DEPTH_BLOCKS = 3;
    private static final String BASIN_BLOCK = "minecraft:stone";

    @Override
    public String id() {
        return "fluidProfiles";
    }

    @Override
    public String label() {
        return "Fluid profiles";
    }

    @Override
    public String description() {
        return "The blocks of each named fluid of the dimension's hydrology, in a pool lined with its seam";
    }

    @Override
    public SceneSizes sizes() {
        return SIZES;
    }

    @Override
    public List<SceneEntry> entries(Engine engine) {
        return fluids(engine).stream().map(fluid -> new SceneEntry(fluid.getName(), fluid.getName(), "Fluids", VARIANTS)).toList();
    }

    @Override
    public boolean hasForm() {
        return true;
    }

    @Override
    public SceneForm form(Engine engine, String key, Map<String, String> overrides) {
        IrisFluidProfile fluid = fluidOf(engine, key);
        IrisData data = engine.getData();
        List<String> notes = new ArrayList<>();
        notes.add("Blocks: " + String.join(", ", fluid.getPalette().getBlockData(data).stream().map(state -> state.key()).toList()));
        notes.add("Seam: " + (fluid.getSeam() == null ? "none" : fluid.getSeam()));
        return new SceneForm("Fluid", List.of(), notes);
    }

    @Override
    public SceneBlocks scene(Engine engine, SceneRequest request) {
        IrisData data = engine.getData();
        IrisFluidProfile fluid = fluidOf(engine, request.key());
        IrisFluidProfile seam = fluid.getSeam() == null ? null : fluidOf(engine, fluid.getSeam());
        int size = request.size();
        int offset = Math.floorMod(request.variant(), VARIANTS) * VARIANT_SPACING_BLOCKS;
        RNG rng = new RNG(offset + 1L);
        SceneBlocks scene = new SceneBlocks();
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                scene.add(x, 0, z, BASIN_BLOCK);
                boolean wall = x == 0 || z == 0 || x == size - 1 || z == size - 1;
                boolean seamRing = seam != null && (x == 1 || z == 1 || x == size - 2 || z == size - 2);
                for (int y = 1; y <= DEPTH_BLOCKS; y++) {
                    IrisFluidProfile filling = seamRing ? seam : fluid;
                    scene.add(x, y, z, wall ? BASIN_BLOCK : filling.getPalette().get(rng, x + offset, y, z, data).key());
                }
            }
        }
        return scene;
    }

    private static KList<IrisFluidProfile> fluids(Engine engine) {
        return engine.getDimension().getHydrology().getFluids();
    }

    private static IrisFluidProfile fluidOf(Engine engine, String key) {
        Optional<IrisFluidProfile> found = fluids(engine).stream().filter(fluid -> key.equals(fluid.getName())).findFirst();
        return found.orElseThrow(() -> new NoSuchElementException("No fluid profile " + key));
    }
}
