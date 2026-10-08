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
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.dimension.IrisDimensionModeType;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.project.hunk.Hunk;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The built-in iris:perfection chunk stage, run as part of the pack's chunk graph. */
public class PerfectionModuleTest {
    private static final int WIDTH = 16;
    private static final int HEIGHT = 4;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private LoadedPack pack;
    private Engine engine;
    private IrisDimension dimension;
    private Hunk<PlatformBlockState> blocks;
    private PlatformBlockState ground;

    @Before
    public void loadPack() throws IOException {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(call -> block(PlatformBlockStates.namespaced(call.<String>getArgument(0).toLowerCase()), false));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        pack = LoadedPack.load(folder.newFolder("pack").toPath(),
                ModuleHost.of(IrisModuleHost.builtInModules()).copyingJarsTo(folder.newFolder("copies").toPath()));
        engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        SeedManager seeds = mock(SeedManager.class);
        dimension = new IrisDimension();
        when(engine.getData()).thenReturn(data);
        when(data.getDataFolder()).thenReturn(folder.getRoot());
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getMetrics()).thenReturn(new EngineMetrics(1));
        blocks = Hunk.newArrayHunk(WIDTH, HEIGHT, WIDTH);
        ground = block("minecraft:grass_block", false);
        blocks.set(0, 0, 0, ground);
        blocks.set(1, 0, 0, ground);
    }

    @After
    public void closePack() {
        pack.close();
        IrisPlatforms.unbind();
    }

    @Test
    public void overworldChunksLoseDecorationsTheirGroundCannotHold() {
        PlatformBlockState floating = decorant(false);
        PlatformBlockState rooted = decorant(true);
        blocks.set(0, 1, 0, floating);
        blocks.set(1, 1, 0, rooted);

        generate();

        assertNotSame(floating, blocks.get(0, 1, 0));
        assertSame(rooted, blocks.get(1, 1, 0));
        assertSame(ground, blocks.get(0, 0, 0));
    }

    @Test
    public void otherWorldModesKeepEveryDecoration() {
        dimension.getMode().setType(IrisDimensionModeType.SUPERFLAT);
        PlatformBlockState floating = decorant(false);
        blocks.set(0, 1, 0, floating);

        generate();

        assertSame(floating, blocks.get(0, 1, 0));
    }

    private void generate() {
        ModuleChunkGeneration.generate(pack, engine, 0, 0, blocks, null, new IrisRegion(), new IrisBiome(), key -> block(key, false));
    }

    private static PlatformBlockState decorant(boolean held) {
        PlatformBlockState decorant = block("minecraft:poppy", true);
        when(decorant.canPlaceOnto(any())).thenReturn(held);
        return decorant;
    }

    private static PlatformBlockState block(String key, boolean decorant) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn(key);
        when(block.isDecorant()).thenReturn(decorant);
        when(block.isAir()).thenReturn(key.endsWith("air"));
        return block;
    }
}
