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

package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.Engine;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StructureReachabilityContractTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Test
    public void reachableKeysRejectsNullEngine() {
        assertThrows(NullPointerException.class, () -> StructureReachability.reachableKeys(null));
    }

    @Test
    public void reachableKeysRejectsEngineWithoutData() {
        Engine engine = mock(Engine.class);
        assertThrows(IllegalStateException.class, () -> StructureReachability.reachableKeys(engine));
    }

    @Test
    public void isReachableRejectsNullEngine() {
        assertThrows(NullPointerException.class,
                () -> StructureReachability.isReachable(null, "minecraft:village_taiga"));
    }

    @Test
    public void isReachableRejectsNullOrEmptyKey() {
        Engine engine = mock(Engine.class);
        assertThrows(IllegalArgumentException.class, () -> StructureReachability.isReachable(engine, null));
        assertThrows(IllegalArgumentException.class, () -> StructureReachability.isReachable(engine, ""));
    }

    @Test
    public void isReachableRejectsEngineWithoutData() {
        Engine engine = mock(Engine.class);
        assertThrows(IllegalStateException.class,
                () -> StructureReachability.isReachable(engine, "minecraft:village_taiga"));
    }

    @Test
    public void missingBiomeKeysRejectsNullEngine() {
        assertThrows(NullPointerException.class,
                () -> StructureReachability.missingBiomeKeys(null, "minecraft:village_taiga"));
    }

    @Test
    public void missingBiomeKeysRejectsNullKey() {
        Engine engine = mock(Engine.class);
        assertThrows(IllegalArgumentException.class,
                () -> StructureReachability.missingBiomeKeys(engine, null));
    }

    @Test
    public void missingBiomeKeysRejectsUnavailableWorld() {
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        assertThrows(IllegalStateException.class,
                () -> StructureReachability.missingBiomeKeys(engine, "minecraft:village_taiga"));
    }

    @Test
    public void invalidateIsNullSafe() {
        StructureReachability.invalidate(null);
        StructureReachability.invalidate(mock(Engine.class));
    }

    @Test
    public void failedReachabilityBuildIsNotCachedAsEmpty() {
        Engine engine = mock(Engine.class);
        IrisWorld world = mock(IrisWorld.class);
        PlatformWorld platformWorld = mock(PlatformWorld.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        IllegalStateException cause = new IllegalStateException("registry unavailable");
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getWorld()).thenReturn(world);
        when(world.platformWorld()).thenReturn(platformWorld);
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.reachableStructureKeys(platformWorld))
                .thenThrow(cause)
                .thenReturn(List.of("minecraft:monument"));

        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            assertThrows(IllegalStateException.class, () -> StructureReachability.reachableKeys(engine));
            assertTrue(StructureReachability.reachableKeys(engine).contains("minecraft:monument"));
        } finally {
            StructureReachability.invalidate(engine);
            IrisPlatforms.unbind();
        }
    }

    @Test
    public void missingBiomeKeysForwardsCanonicalStructureKey() {
        Engine engine = mock(Engine.class);
        IrisWorld world = mock(IrisWorld.class);
        PlatformWorld platformWorld = mock(PlatformWorld.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        when(engine.getWorld()).thenReturn(world);
        when(world.platformWorld()).thenReturn(platformWorld);
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.possibleBiomeKeys(platformWorld)).thenReturn(List.of("minecraft:deep_ocean"));
        when(hooks.structureBiomeKeys("minecraft:monument")).thenReturn(List.of("minecraft:deep_ocean"));

        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            assertTrue(StructureReachability.missingBiomeKeys(
                    engine, "  MINECRAFT:MONUMENT  ").isEmpty());
            verify(hooks).structureBiomeKeys("minecraft:monument");
        } finally {
            IrisPlatforms.unbind();
        }
    }
}
