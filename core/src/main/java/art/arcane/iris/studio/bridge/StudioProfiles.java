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

import art.arcane.iris.generation.hydrology.config.IrisFluidProfile;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * The views of the config profiles the pack has: fluid profiles and objects. Each lists what the pack has of its kind,
 * shows its settings as a form and draws it as blocks, so the studio's scene viewer can show them. Values typed into a
 * form only change the preview.
 */
final class StudioProfiles {
    static final String FLUIDS = "fluidProfiles";

    private static final List<ProfileView> VIEWS = List.of(new FluidView(), StudioObjects.packObjects());

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

        /** Whether each entry has a settings form; the object views have none. */
        default boolean hasForm() {
            return true;
        }

        /** Whether the entries are baked from settings, so their bake can be timed. */
        default boolean isBaked() {
            return false;
        }

        /** One timed bake of the entry; only asked of baked views. */
        default StudioObjects.BakeTiming timeBake(Engine engine, String key) {
            throw new UnsupportedOperationException(id() + " is not baked");
        }

        SceneBlocks scene(Engine engine, String key, int variant, int size, Map<String, String> overrides) throws IOException;
    }

    static SceneBlocks sceneOf(IrisObject object) {
        SceneBlocks scene = new SceneBlocks();
        object.getBlocks().forEach((position, state) -> scene.add(position.getBlockX(), position.getBlockY(), position.getBlockZ(), state.key()));
        return scene;
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
