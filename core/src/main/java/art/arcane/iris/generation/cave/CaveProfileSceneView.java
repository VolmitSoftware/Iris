package art.arcane.iris.generation.cave;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.mantle.CaveProfilePreview;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.studio.scene.SceneBlocks;
import art.arcane.iris.studio.scene.SceneEntry;
import art.arcane.iris.studio.scene.SceneForm;
import art.arcane.iris.studio.scene.SceneRequest;
import art.arcane.iris.studio.scene.SceneSizes;
import art.arcane.iris.studio.scene.SceneView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** The carved space of each enabled cave profile, as blocks coloured by depth. */
public final class CaveProfileSceneView implements SceneView {
    private static final SceneSizes SIZES = new SceneSizes(List.of(64, 128, 256), 128);
    private static final int VARIANTS = 4;
    private static final int VARIANT_SPACING_BLOCKS = 4096;
    private static final int SURFACE_ABOVE_SEA_BLOCKS = 48;
    private static final int MAX_DEPTH_BLOCKS = 160;
    private static final String DIMENSION_KEY = "dimension";
    private static final String REGION_PREFIX = "region/";
    private static final String BIOME_PREFIX = "biome/";
    private static final String[] DEPTH_BANDS = {"minecraft:yellow_concrete", "minecraft:lime_concrete", "minecraft:light_blue_concrete",
            "minecraft:purple_concrete"};
    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    @Override
    public String id() {
        return "caveProfiles";
    }

    @Override
    public String label() {
        return "Cave profiles";
    }

    @Override
    public String description() {
        return "The carved space of each enabled cave profile of the dimension, its regions and its biomes";
    }

    @Override
    public SceneSizes sizes() {
        return SIZES;
    }

    @Override
    public List<SceneEntry> entries(Engine engine) {
        IrisData data = engine.getData();
        IrisDimension dimension = engine.getDimension();
        List<SceneEntry> entries = new ArrayList<>();
        if (dimension.getCaveProfile().isEnabled()) {
            entries.add(new SceneEntry(DIMENSION_KEY, dimension.getName(), "Dimension", VARIANTS));
        }
        for (IrisRegion region : data.getRegionLoader().loadAll(data.getRegionLoader().getPossibleKeys())) {
            if (region != null && region.getCaveProfile().isEnabled()) {
                entries.add(new SceneEntry(REGION_PREFIX + region.getLoadKey(), region.getName(), "Regions", VARIANTS));
            }
        }
        for (IrisBiome biome : data.getBiomeLoader().loadAll(data.getBiomeLoader().getPossibleKeys())) {
            if (biome != null && biome.getCaveProfile().isEnabled()) {
                entries.add(new SceneEntry(BIOME_PREFIX + biome.getLoadKey(), biome.getName(), "Biomes", VARIANTS));
            }
        }
        return entries;
    }

    @Override
    public boolean hasForm() {
        return true;
    }

    @Override
    public SceneForm form(Engine engine, String key, Map<String, String> overrides) {
        IrisCaveProfile profile = SceneForm.withOverrides(profileOf(engine, key), overrides);
        return SceneForm.of("Cave profile", profile, Set.of(), overrides, List.of("Values typed here change the preview only"));
    }

    @Override
    public SceneBlocks scene(Engine engine, SceneRequest request) throws IOException {
        IrisCaveProfile profile = SceneForm.withOverrides(profileOf(engine, request.key()), request.overrides());
        int size = request.size();
        int surfaceY = Math.min(engine.getHeight() - 1, engine.getDimension().getFluidHeight() + SURFACE_ABOVE_SEA_BLOCKS);
        int bottomY = Math.max(0, Math.max((int) Math.floor(profile.getVerticalRange().getMin()), surfaceY - MAX_DEPTH_BLOCKS));
        int originX = Math.floorMod(request.variant(), VARIANTS) * VARIANT_SPACING_BLOCKS;
        boolean[] carved = CaveProfilePreview.carve(engine, profile, originX, size, bottomY, surfaceY);
        return outsideOf(carved, size, surfaceY - bottomY + 1);
    }

    private static IrisCaveProfile profileOf(Engine engine, String key) {
        IrisData data = engine.getData();
        IrisCaveProfile profile = null;
        if (DIMENSION_KEY.equals(key)) {
            profile = engine.getDimension().getCaveProfile();
        } else if (key.startsWith(REGION_PREFIX)) {
            IrisRegion region = data.getRegionLoader().load(key.substring(REGION_PREFIX.length()));
            profile = region == null ? null : region.getCaveProfile();
        } else if (key.startsWith(BIOME_PREFIX)) {
            IrisBiome biome = data.getBiomeLoader().load(key.substring(BIOME_PREFIX.length()));
            profile = biome == null ? null : biome.getCaveProfile();
        }
        if (profile == null) {
            throw new NoSuchElementException("No cave profile " + key);
        }
        return profile;
    }

    // Only the carved blocks on the outside of the space are kept, as the inside could not be seen.
    private static SceneBlocks outsideOf(boolean[] carved, int size, int height) {
        SceneBlocks scene = new SceneBlocks();
        for (int y = 0; y < height; y++) {
            String band = DEPTH_BANDS[Math.min(DEPTH_BANDS.length - 1, (height - 1 - y) * DEPTH_BANDS.length / height)];
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    if (carved[cellOf(x, y, z, size)] && touchesRock(carved, x, y, z, size, height)) {
                        scene.add(x, y, z, band);
                    }
                }
            }
        }
        return scene;
    }

    private static boolean touchesRock(boolean[] carved, int x, int y, int z, int size, int height) {
        for (int[] step : NEIGHBOURS) {
            int nx = x + step[0];
            int ny = y + step[1];
            int nz = z + step[2];
            boolean inside = nx >= 0 && ny >= 0 && nz >= 0 && nx < size && ny < height && nz < size;
            if (!inside || !carved[cellOf(nx, ny, nz, size)]) {
                return true;
            }
        }
        return false;
    }

    private static int cellOf(int x, int y, int z, int size) {
        return (y * size + z) * size + x;
    }
}
