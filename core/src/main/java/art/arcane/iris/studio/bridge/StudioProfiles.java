/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.bridge;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.decoration.formation.IrisFormation;
import art.arcane.iris.generation.decoration.formation.IrisFormationProfile;
import art.arcane.iris.generation.decoration.tree.IrisProceduralTree;
import art.arcane.iris.generation.decoration.tree.IrisTreeProfile;
import art.arcane.iris.generation.hydrology.config.IrisFluidProfile;
import art.arcane.iris.generation.mantle.CaveProfilePreview;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.util.math.RNG;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * The views of the config profiles Iris v4 has: cave profiles, tree profiles, formation profiles and fluid profiles.
 * Each lists what the pack has of its kind, shows its settings as a form and draws it as blocks, so the studio's scene
 * viewer can show them. Values typed into a form only change the preview.
 */
final class StudioProfiles {
    static final String CAVES = "caveProfiles";
    static final String TREES = "treeProfiles";
    static final String FORMATIONS = "formationProfiles";
    static final String FLUIDS = "fluidProfiles";

    private static final List<ProfileView> VIEWS = List.of(new CaveView(), new TreeView(), new FormationView(), new FluidView());

    private StudioProfiles() {
    }

    static List<ProfileView> views() {
        return VIEWS;
    }

    static ProfileView view(String id) {
        return VIEWS.stream().filter(view -> view.id().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("No profile view " + id));
    }

    record Entry(String key, String name, String group, int variants) {
    }

    /** @param blocksAcross the sizes the scene can be built at, smallest first */
    record Sizes(List<Integer> blocksAcross, int opening) {
    }

    interface ProfileView {
        String id();

        String label();

        String description();

        /** Null when each entry draws one size. */
        Sizes sizes();

        List<Entry> entries(Engine engine);

        ProfileForm.Form form(Engine engine, String key, Map<String, String> overrides);

        SceneBlocks scene(Engine engine, String key, int variant, int size, Map<String, String> overrides) throws IOException;
    }

    private static String readable(String constantName) {
        String lower = constantName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** What the pack says about an enum constant, or its readable name when it says nothing. */
    private static String describe(Enum<?> constant) {
        try {
            Description description = constant.getDeclaringClass().getField(constant.name()).getAnnotation(Description.class);
            return description == null ? readable(constant.name()) : description.value();
        } catch (NoSuchFieldException undocumented) {
            return readable(constant.name());
        }
    }

    private static Entry presetEntry(String key, int variants) {
        return new Entry(key, readable(key), "Presets", variants);
    }

    private static SceneBlocks sceneOf(IrisObject object) {
        SceneBlocks scene = new SceneBlocks();
        object.getBlocks().forEach((position, state) -> scene.add(position.getBlockX(), position.getBlockY(), position.getBlockZ(), state.key()));
        return scene;
    }

    private static IrisObject variantOf(List<IrisObject> variants, int variant, String what) {
        if (variants.isEmpty()) {
            throw new NoSuchElementException("This " + what + " bakes nothing");
        }
        return variants.get(Math.floorMod(variant, variants.size()));
    }

    // Caves ------------------------------------------------------------------------------------------------------

    private static final class CaveView implements ProfileView {
        private static final Sizes SIZES = new Sizes(List.of(64, 128, 256), 128);
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
            return CAVES;
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
        public Sizes sizes() {
            return SIZES;
        }

        @Override
        public List<Entry> entries(Engine engine) {
            IrisData data = engine.getData();
            IrisDimension dimension = engine.getDimension();
            List<Entry> entries = new ArrayList<>();
            if (dimension.getCaveProfile().isEnabled()) {
                entries.add(new Entry(DIMENSION_KEY, dimension.getName(), "Dimension", VARIANTS));
            }
            for (IrisRegion region : data.getRegionLoader().loadAll(data.getRegionLoader().getPossibleKeys())) {
                if (region != null && region.getCaveProfile().isEnabled()) {
                    entries.add(new Entry(REGION_PREFIX + region.getLoadKey(), region.getName(), "Regions", VARIANTS));
                }
            }
            for (IrisBiome biome : data.getBiomeLoader().loadAll(data.getBiomeLoader().getPossibleKeys())) {
                if (biome != null && biome.getCaveProfile().isEnabled()) {
                    entries.add(new Entry(BIOME_PREFIX + biome.getLoadKey(), biome.getName(), "Biomes", VARIANTS));
                }
            }
            return entries;
        }

        @Override
        public ProfileForm.Form form(Engine engine, String key, Map<String, String> overrides) {
            IrisCaveProfile profile = ProfileForm.withOverrides(profileOf(engine, key), overrides);
            return ProfileForm.of("Cave profile", profile, Set.of(), overrides, List.of("Values typed here change the preview only"));
        }

        @Override
        public SceneBlocks scene(Engine engine, String key, int variant, int size, Map<String, String> overrides) throws IOException {
            IrisCaveProfile profile = ProfileForm.withOverrides(profileOf(engine, key), overrides);
            int surfaceY = Math.min(engine.getHeight() - 1, engine.getDimension().getFluidHeight() + SURFACE_ABOVE_SEA_BLOCKS);
            int bottomY = Math.max(0, Math.max((int) Math.floor(profile.getVerticalRange().getMin()), surfaceY - MAX_DEPTH_BLOCKS));
            boolean[] carved = CaveProfilePreview.carve(engine, profile, Math.floorMod(variant, VARIANTS) * VARIANT_SPACING_BLOCKS, size, bottomY, surfaceY);
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

    // Trees ------------------------------------------------------------------------------------------------------

    private static final class TreeView implements ProfileView {
        private static final Set<String> HIDDEN = Set.of("name", "chance", "density", "variants", "seed", "plausible", "mode",
                "carvingSupport", "underwater", "profile");

        @Override
        public String id() {
            return TREES;
        }

        @Override
        public String label() {
            return "Tree profiles";
        }

        @Override
        public String description() {
            return "The canopy shape each tree profile gives a procedural tree";
        }

        @Override
        public Sizes sizes() {
            return null;
        }

        @Override
        public List<Entry> entries(Engine engine) {
            int variants = new IrisProceduralTree().getVariants();
            return Arrays.stream(IrisTreeProfile.values()).map(profile -> presetEntry(profile.name(), variants)).toList();
        }

        @Override
        public ProfileForm.Form form(Engine engine, String key, Map<String, String> overrides) {
            return ProfileForm.of("Tree", treeOf(key, overrides), HIDDEN, overrides,
                    List.of(describe(profileOf(key)), "Values typed here change the preview only"));
        }

        @Override
        public SceneBlocks scene(Engine engine, String key, int variant, int size, Map<String, String> overrides) {
            return sceneOf(variantOf(treeOf(key, overrides).getVariantObjects(engine.getData()), variant, "tree"));
        }

        private static IrisProceduralTree treeOf(String key, Map<String, String> overrides) {
            IrisProceduralTree tree = ProfileForm.withOverrides(new IrisProceduralTree(), overrides);
            tree.setProfile(profileOf(key));
            return tree;
        }

        private static IrisTreeProfile profileOf(String key) {
            try {
                return IrisTreeProfile.valueOf(key);
            } catch (IllegalArgumentException unknown) {
                throw new NoSuchElementException("No tree profile " + key);
            }
        }

    }

    // Formations -------------------------------------------------------------------------------------------------

    private static final class FormationView implements ProfileView {
        private static final Set<String> HIDDEN = Set.of("name", "chance", "density", "variants", "seed", "mode", "carvingSupport",
                "surfaceSupportBuffer", "underwater", "profile");

        @Override
        public String id() {
            return FORMATIONS;
        }

        @Override
        public String label() {
            return "Formation profiles";
        }

        @Override
        public String description() {
            return "How each formation profile shapes a formation's width from base to top";
        }

        @Override
        public Sizes sizes() {
            return null;
        }

        @Override
        public List<Entry> entries(Engine engine) {
            int variants = new IrisFormation().getVariants();
            return Arrays.stream(IrisFormationProfile.values()).map(profile -> presetEntry(profile.name(), variants)).toList();
        }

        @Override
        public ProfileForm.Form form(Engine engine, String key, Map<String, String> overrides) {
            return ProfileForm.of("Formation", formationOf(key, overrides), HIDDEN, overrides,
                    List.of(describe(profileOf(key)), "Values typed here change the preview only"));
        }

        @Override
        public SceneBlocks scene(Engine engine, String key, int variant, int size, Map<String, String> overrides) {
            return sceneOf(variantOf(formationOf(key, overrides).getVariantObjects(engine.getData()), variant, "formation"));
        }

        private static IrisFormation formationOf(String key, Map<String, String> overrides) {
            IrisFormation formation = ProfileForm.withOverrides(new IrisFormation(), overrides);
            formation.setProfile(profileOf(key));
            return formation;
        }

        private static IrisFormationProfile profileOf(String key) {
            try {
                return IrisFormationProfile.valueOf(key);
            } catch (IllegalArgumentException unknown) {
                throw new NoSuchElementException("No formation profile " + key);
            }
        }

    }

    // Fluids -----------------------------------------------------------------------------------------------------

    private static final class FluidView implements ProfileView {
        private static final Sizes SIZES = new Sizes(List.of(16, 32, 64), 32);
        private static final int VARIANTS = 4;
        private static final int VARIANT_SPACING_BLOCKS = 1024;
        private static final int DEPTH_BLOCKS = 3;
        private static final String BASIN_BLOCK = "minecraft:stone";

        @Override
        public String id() {
            return FLUIDS;
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
        public Sizes sizes() {
            return SIZES;
        }

        @Override
        public List<Entry> entries(Engine engine) {
            return fluids(engine).stream().map(fluid -> new Entry(fluid.getName(), fluid.getName(), "Fluids", VARIANTS)).toList();
        }

        @Override
        public ProfileForm.Form form(Engine engine, String key, Map<String, String> overrides) {
            IrisFluidProfile fluid = fluidOf(engine, key);
            IrisData data = engine.getData();
            List<String> notes = new ArrayList<>();
            notes.add("Blocks: " + String.join(", ", fluid.getPalette().getBlockData(data).stream().map(state -> state.key()).toList()));
            notes.add("Seam: " + (fluid.getSeam() == null ? "none" : fluid.getSeam()));
            return new ProfileForm.Form("Fluid", List.of(), notes);
        }

        @Override
        public SceneBlocks scene(Engine engine, String key, int variant, int size, Map<String, String> overrides) {
            IrisData data = engine.getData();
            IrisFluidProfile fluid = fluidOf(engine, key);
            IrisFluidProfile seam = fluid.getSeam() == null ? null : fluidOf(engine, fluid.getSeam());
            int offset = Math.floorMod(variant, VARIANTS) * VARIANT_SPACING_BLOCKS;
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
}
