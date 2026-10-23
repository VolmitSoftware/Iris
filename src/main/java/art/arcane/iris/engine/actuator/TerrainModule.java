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
package art.arcane.iris.engine.actuator;

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.terrain.BedrockSettings;
import art.arcane.iris.builtin.terrain.CaveCeilingLayersSettings;
import art.arcane.iris.builtin.terrain.ExplodeBiomePaletteSizeSettings;
import art.arcane.iris.builtin.terrain.ExplodeBiomePalettesSettings;
import art.arcane.iris.builtin.terrain.FluidPaletteSettings;
import art.arcane.iris.builtin.terrain.HideOresSettings;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.builtin.terrain.LockLayersMaxSettings;
import art.arcane.iris.builtin.terrain.LockLayersSettings;
import art.arcane.iris.builtin.terrain.OreSettings;
import art.arcane.iris.builtin.terrain.RockPaletteSettings;
import art.arcane.iris.builtin.terrain.SeaLayersSettings;
import art.arcane.iris.builtin.terrain.SlabSettings;
import art.arcane.iris.builtin.terrain.WallSettings;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLifecycle;
import com.volmit.ModuleSetup;
import com.volmit.stage.ChunkStageContext;
import com.volmit.world.PackWorld;

import art.arcane.iris.builtin.terrain.TerrainPalettes;

/**
 * Module {@code iris:terrain}: the chunk's ground, fluids and biome layers. Each column is filled from bedrock up
 * to its height and the fluid height with its biome's layers, with ore generators of the dimension, region and
 * biome replacing blocks on the way and the upper dimension's ground filled in from the top. The ore generators' noise
 * and the rock and fluid palettes' noise are built ahead of generation, in {@link #warmUp}.
 */
public final class TerrainModule implements IrisModule, ModuleLifecycle {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:terrain", "Terrain", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        setup.stages().define(IrisStages.Terrain.SHAPE).run(TerrainModule::shape);
        var registry = setup.registry();
        registry.registerSettings(LayersSettings.class, SeaLayersSettings.class, CaveCeilingLayersSettings.class, SlabSettings.class,
                WallSettings.class, LockLayersSettings.class, LockLayersMaxSettings.class, RockPaletteSettings.class, FluidPaletteSettings.class,
                OreSettings.class, HideOresSettings.class, BedrockSettings.class, ExplodeBiomePalettesSettings.class,
                ExplodeBiomePaletteSizeSettings.class);
        registry.registerLifecycle(this);
    }

    @Override
    public void warmUp(PackWorld world) {
        TerrainPalettes.of(world).warm();
        new OreWarmUp(world).warm();
    }

    private static void shape(ChunkStageContext context) {
        TerrainShaper.of(context.world()).shape(context);
    }
}
