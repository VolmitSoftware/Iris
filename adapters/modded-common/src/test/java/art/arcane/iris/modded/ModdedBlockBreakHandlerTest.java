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

package art.arcane.iris.modded;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.loot.IrisBlockDrops;
import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemStack;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldGenerators;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.core.Direction;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.Test;
import org.junit.BeforeClass;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedBlockBreakHandlerTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void exactMatchingIncludesPropertiesWhileTypeMatchingDoesNot() {
        ModdedBlockState north = ModdedBlockState.of(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH), null);
        ModdedBlockState east = ModdedBlockState.of(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST), null);

        assertTrue(ModdedBlockBreakHandler.matchesState(north, north, true));
        assertFalse(ModdedBlockBreakHandler.matchesState(north, east, true));
        assertTrue(ModdedBlockBreakHandler.matchesState(north, east, false));
        assertFalse(ModdedBlockBreakHandler.matchesState(north, ModdedBlockState.of(Blocks.COBBLESTONE.defaultBlockState(), null), false));
    }

    @Test
    public void historicalEnvironmentSuppliesAllRulesAndScopesMatchingAndLoot() {
        try (DropFixture fixture = new DropFixture()) {
            fixture.dimensionRule.setReplaceVanillaDrops(true);

            ModdedBlockBreakHandler.Result result = fixture.evaluate();

            assertTrue(result.replaceVanillaDrops());
            assertEquals(3, result.drops().size());
            assertSame(fixture.item, result.drops().getFirst());
            assertFalse(fixture.scoped.get());
            verify(fixture.engine).getBiomeEnvironment(16, 136, 32);
            verify(fixture.engine).openBiomeEnvironmentScope(fixture.environment);
            verify(fixture.engine, never()).getData();
            verify(fixture.engine, never()).getDimension();
            verify(fixture.engine, never()).getBiome(anyInt(), anyInt(), anyInt());
            verify(fixture.engine, never()).getRegion(anyInt(), anyInt(), anyInt());
        }
    }

    @Test
    public void historicalBiomeCanSkipRegionAndDimensionDrops() {
        try (DropFixture fixture = new DropFixture()) {
            fixture.biomeRule.setSkipParents(true);
            fixture.dimensionRule.setReplaceVanillaDrops(true);

            ModdedBlockBreakHandler.Result result = fixture.evaluate();

            assertFalse(result.replaceVanillaDrops());
            assertEquals(1, result.drops().size());
            assertFalse(fixture.scoped.get());
            verify(fixture.region, never()).getBlockDrops();
            verify(fixture.dimension, never()).getBlockDrops();
        }
    }

    private static final class DropFixture implements AutoCloseable {
        private final NativeWorld world = mock(NativeWorld.class);
        private final Engine engine = mock(Engine.class);
        private final IrisBiome biome = mock(IrisBiome.class);
        private final IrisRegion region = mock(IrisRegion.class);
        private final IrisDimension dimension = mock(IrisDimension.class);
        private final IrisData data = mock(IrisData.class);
        private final BiomeEnvironment environment = new BiomeEnvironment(7L, biome, region, dimension, data);
        private final ModdedBlockState state = ModdedBlockState.of(Blocks.COBBLESTONE.defaultBlockState(), null);
        private final NativeItemStack item = mock(NativeItemStack.class);
        private final IrisLoot loot = new IrisLoot().setRarity(1);
        private final IrisBlockData configuredBlock = mock(IrisBlockData.class);
        private final IrisBlockDrops biomeRule = rule();
        private final IrisBlockDrops regionRule = rule();
        private final IrisBlockDrops dimensionRule = rule();
        private final AtomicBoolean scoped = new AtomicBoolean();
        private final MockedStatic<NativeWorldGenerators> generators = mockStatic(NativeWorldGenerators.class);
        private final MockedStatic<ModdedItemTranslator> translator = mockStatic(ModdedItemTranslator.class);

        private DropFixture() {
            IrisModdedChunkGenerator generator = mock(IrisModdedChunkGenerator.class);
            generators.when(() -> NativeWorldGenerators.find(world, IrisModdedChunkGenerator.class)).thenReturn(generator);
            when(generator.engineIfBound()).thenReturn(engine);
            when(engine.getMinHeight()).thenReturn(-64);
            when(engine.getBiomeEnvironment(16, 136, 32)).thenReturn(environment);
            when(engine.openBiomeEnvironmentScope(environment)).thenAnswer(invocation -> {
                assertTrue(scoped.compareAndSet(false, true));
                return (BiomeEnvironment.Scope) () -> scoped.set(false);
            });
            when(biome.getBlockDrops()).thenReturn(new KList<>(biomeRule));
            when(region.getBlockDrops()).thenReturn(new KList<>(regionRule));
            when(dimension.getBlockDrops()).thenReturn(new KList<>(dimensionRule));
            when(configuredBlock.getBlockData(data)).thenAnswer(invocation -> {
                assertTrue(scoped.get());
                return state;
            });
            translator.when(() -> ModdedItemTranslator.stack(eq(loot), eq(RNG.r), any())).thenAnswer(invocation -> {
                assertTrue(scoped.get());
                return item;
            });
        }

        private IrisBlockDrops rule() {
            return new IrisBlockDrops().setBlocks(new KList<>(configuredBlock)).setDrops(new KList<>(loot));
        }

        private ModdedBlockBreakHandler.Result evaluate() {
            return ModdedBlockBreakHandler.evaluateManagedDrops(world, new NativeBlockPoint(16, 72, 32), state);
        }

        @Override
        public void close() {
            translator.close();
            generators.close();
        }
    }
}
