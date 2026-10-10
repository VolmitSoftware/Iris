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

package art.arcane.iris.engine.spawn;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.builtin.spawn.EffectSettings;
import art.arcane.iris.spi.PlatformWorld;
import com.volmit.service.AmbientEffects;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The ambient effects due around a player: the region's, then the biome's, each when its interval and chance allow, fully decided. */
public class AmbientEffectingTest {
    private static final BlockPosition AT = new BlockPosition(10, 70, -20);
    private static final String SOUND_AND_POTION = """
            {"effects": [{"sound": "ambient.cave", "soundDistance": 4, "minPitch": 0.5, "maxPitch": 0.5, "volume": 2,
                          "potionEffect": "luck", "potionStrength": 1, "potionTicksMin": 100, "potionTicksMax": 100,
                          "interval": 1, "chance": 1}]}""";
    private static final String PARTICLES_AND_COMMANDS = """
            {"effects": [{"particleEffect": "flame", "particleDistance": 1, "particleAway": 5, "particleDistanceWidth": 0,
                          "particleCount": 2, "particleAltX": 0.5, "randomAltX": false, "randomAltZ": false, "extra": 0.25,
                          "commandRegistry": {"rawCommands": [{"commands": ["say hi {x}"], "delay": 3},
                                                              {"commands": ["say never"], "weather": "DOWNFALL"}],
                                              "commandOffsetX": 2, "commandRandomAltX": false, "commandAllRandomLocations": false},
                          "interval": 1, "chance": 1}]}""";

    private Engine engine;
    private IrisRegion region;
    private IrisBiome biome;
    private PackWorld world;

    @Before
    public void buildWorld() {
        engine = mock(Engine.class);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        IrisWorld engineWorld = mock(IrisWorld.class);
        PlatformWorld platformWorld = mock(PlatformWorld.class);
        when(engineWorld.platformWorld()).thenReturn(platformWorld);
        when(engine.getWorld()).thenReturn(engineWorld);
        region = mock(IrisRegion.class);
        biome = mock(IrisBiome.class);
        when(region.getModuleSettings()).thenReturn(ModuleSettingsOf.none());
        when(biome.getModuleSettings()).thenReturn(ModuleSettingsOf.none());
        when(engine.getRegion(10, -20)).thenReturn(region);
        when(engine.getBiome(10, 70, -20)).thenReturn(biome);
        world = new EngineGeneration(engine).world();
    }

    @Test
    public void anAreaWithoutEffectsHasNothingDue() {
        assertEquals(List.of(), new AmbientEffecting().due(world, AT));
    }

    @Test
    public void aSoundAndAPotionComeWithTheirDecidedValues() throws Exception {
        when(biome.getModuleSettings()).thenReturn(BuiltInPack.settings(TestSettings.of(EffectSettings.class, SOUND_AND_POTION)));
        Thread.sleep(5);

        List<AmbientEffects.Effect> due = new AmbientEffecting().due(world, AT);

        assertEquals(1, due.size());
        AmbientEffects.Sound sound = due.getFirst().sound();
        assertEquals("ambient.cave", sound.key());
        assertTrue(Math.abs(sound.offsetX()) <= 4 && Math.abs(sound.offsetY()) <= 4 && Math.abs(sound.offsetZ()) <= 4);
        assertEquals(2F, sound.volume(), 0);
        assertEquals(0.5F, sound.pitch(), 0);
        assertEquals(new AmbientEffects.Potion("luck", 1, 100), due.getFirst().potion());
        assertNull(due.getFirst().particles());
        assertTrue(due.getFirst().commands().isEmpty());
    }

    @Test
    public void particlesAndOnlyTheCommandsThatMayRunNowComeWithTheirOffsets() throws Exception {
        when(region.getModuleSettings()).thenReturn(BuiltInPack.settings(TestSettings.of(EffectSettings.class, PARTICLES_AND_COMMANDS)));
        Thread.sleep(5);

        AmbientEffects.Effect effect = new AmbientEffecting().due(world, AT).getFirst();

        assertEquals("flame", effect.particles().key());
        assertEquals(5, effect.particles().forward(), 0);
        assertEquals(0, effect.particles().sideways(), 0);
        assertEquals(0.5, effect.particles().altX(), 0);
        assertEquals(0.25, effect.particles().extra(), 0);
        assertEquals(2, effect.particles().count());
        assertEquals(1, effect.commands().size());
        assertEquals(List.of("say hi {x}"), effect.commands().getFirst().command().commands());
        assertEquals(3, effect.commands().getFirst().command().delay());
        assertEquals(2, effect.commands().getFirst().offsetX(), 0);
        assertNull(effect.sound());
        assertNull(effect.potion());
    }

    @Test
    public void anEffectPlaysOnlyOncePerInterval() {
        when(biome.getModuleSettings()).thenReturn(BuiltInPack.settings(TestSettings.of(EffectSettings.class,
                SOUND_AND_POTION.replace("\"interval\": 1", "\"interval\": 600000"))));
        AmbientEffecting effecting = new AmbientEffecting();

        assertEquals(1, effecting.due(world, AT).size());
        assertEquals(0, effecting.due(world, AT).size());
    }

    private static final class ModuleSettingsOf {
        private static art.arcane.iris.core.modules.ModuleSettings none() {
            return art.arcane.iris.core.modules.ModuleSettings.NONE;
        }
    }
}
