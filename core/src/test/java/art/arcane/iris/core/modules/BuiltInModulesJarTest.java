/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.modifier.ApiChunk;
import art.arcane.iris.engine.modifier.MatterModule;
import com.volmit.IrisModule;
import com.volmit.block.BlockState;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static art.arcane.iris.engine.modifier.ApiChunk.block;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockito.Mockito.mock;

/**
 * The ported built-ins load from a pack's {@code modules/iris-builtins.jar} through the pack loader, seeing only what a pack
 * module sees, and generate the same chunk as their compiled-in copies.
 */
public class BuiltInModulesJarTest {
    private static final int HEIGHT = 64;
    private static final int GROUND = 20;
    private static final int UPPER_DIMENSION = 50;
    private static final BlockState STONE = block("minecraft:stone");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void theJarsModulesComeFromThePackLoaderInTheListedOrder() throws IOException {
        Path pack = BuiltInPack.newPackFolder(folder);
        List<String> listed = BuiltInModulesJar.writeInto(pack);

        try (LoadedPack loaded = loadWithStandIns(pack)) {
            List<DiscoveredModule> fromJar = loaded.packModules();
            assertEquals(listed, fromJar.stream().map(module -> module.module().getClass().getName()).toList());
            fromJar.forEach(module -> assertNotSame(MatterModule.class.getClassLoader(), module.module().getClass().getClassLoader()));
        }
    }

    @Test
    public void theJarsModulesGenerateWhatTheirCompiledInCopiesGenerate() throws IOException {
        Path pack = BuiltInPack.newPackFolder(folder);
        BuiltInModulesJar.writeInto(pack);
        ApiChunk compiledIn = scene();
        ApiChunk fromJar = scene();

        compiledIn.generate(BuiltInPack.jarModules().toArray(IrisModule[]::new));
        try (LoadedPack loaded = loadWithStandIns(pack)) {
            fromJar.generate(loaded.chunk());
        }

        assertEquals("ores hide", STONE, fromJar.get(0, 5, 0));
        assertEquals("planned blocks arrive below the dimension above", block("minecraft:oak_planks"), fromJar.get(0, UPPER_DIMENSION - 1, 0));
        assertEquals(ApiChunk.AIR, fromJar.get(1, UPPER_DIMENSION, 0));
        eachBlock(position -> assertEquals(Arrays.toString(position),
                compiledIn.get(position[0], position[1], position[2]), fromJar.get(position[0], position[1], position[2])));
    }

    /** The jar next to stand-ins for the compiled-in stages it orders after, exposing what Iris exposes. */
    private LoadedPack loadWithStandIns(Path pack) throws IOException {
        return LoadedPack.load(pack, ModuleHost.of(ApiChunk.standIns())
                .exposing(IrisModuleHost.EXPOSED_PACKAGES.toArray(String[]::new))
                .copyingJarsTo(folder.newFolder().toPath()));
    }

    /** A chunk every ported module has work in: a cliff, a step, a floating nib, ores, plants on stone, planned blocks. */
    private static ApiChunk scene() {
        ApiChunk chunk = new ApiChunk(0, 0, HEIGHT)
                .surface((x, z) -> x < 4 ? GROUND + 6 : x == 8 && z == 8 ? GROUND + 9 : x > 11 ? GROUND + 1 : GROUND)
                .upperDimension((x, z) -> UPPER_DIMENSION)
                .fluidHeight(GROUND + 2);
        for (int x = 0; x < ApiChunk.WIDTH; x++) {
            for (int z = 0; z < ApiChunk.WIDTH; z++) {
                int top = x < 4 ? GROUND + 6 : x > 11 ? GROUND + 1 : GROUND;
                for (int y = 0; y <= top; y++) {
                    chunk.set(x, y, z, y == 5 ? block("minecraft:iron_ore") : STONE);
                }
                chunk.set(x, top + 1, z, block("minecraft:poppy"));
                chunk.plan(x, UPPER_DIMENSION - 1 + (x & 1), z, block("minecraft:oak_planks"));
            }
        }
        chunk.set(8, GROUND + 9, 8, STONE);
        chunk.pending().hidingOres().walls(block("minecraft:cobblestone")).slabs(block("minecraft:stone_slab[type=bottom]"), 1);
        return chunk;
    }

    private static void eachBlock(Consumer<int[]> check) {
        for (int x = 0; x < ApiChunk.WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < ApiChunk.WIDTH; z++) {
                    check.accept(new int[]{x, y, z});
                }
            }
        }
    }
}
