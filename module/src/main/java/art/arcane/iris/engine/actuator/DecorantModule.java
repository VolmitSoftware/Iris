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
import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.modules.decorant.DecorateSettings;
import art.arcane.iris.engine.decorator.BiomeDecorators;
import art.arcane.iris.engine.decorator.DecoratedBiome;
import art.arcane.iris.engine.decorator.DecorationScene;
import art.arcane.iris.engine.decorator.IrisSeaFloorDecorator;
import art.arcane.iris.engine.decorator.IrisSeaSurfaceDecorator;
import art.arcane.iris.engine.decorator.IrisShoreLineDecorator;
import art.arcane.iris.engine.decorator.IrisSurfaceDecorator;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLifecycle;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.hunk.Hunk;
import com.volmit.stage.ChunkStageContext;
import com.volmit.world.BiomeKind;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;

/**
 * Module {@code iris:decorant}: the biomes' decorators ({@link DecorantSettings}), placed on the surface the mantle's
 * blocks left: the surface itself, the sea surface and floor, and the shore line. A dimension turns decorating off with
 * {@link DecorateSettings}. The decorators' noise is built ahead of generation, in {@link #warmUp}.
 */
public final class DecorantModule implements IrisModule, ModuleLifecycle {
    private static final String VERSION = "1.0.0";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:decorant", "Decorators", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        // Ordering only: iris:matter ships as a module jar, which a pack may not have.
        setup.stages().define(IrisStages.Decorant.DECORATE).softAfter(IrisStages.Matter.INSERT).run(DecorantModule::decorate);
        var registry = setup.registry();
        registry.registerSettings(DecorantSettings.class);
        registry.registerSettings(DecorateSettings.class);
        registry.registerLifecycle(this);
    }

    @Override
    public void warmUp(PackWorld world) {
        BiomeDecorators.of(world).warm();
    }

    private static void decorate(ChunkStageContext context) {
        if (!decorates(context)) {
            return;
        }
        decorate(DecorationScene.of(context), context.settings(), context.originX(), context.originZ());
    }

    /** A dimension decorates unless it says {@code decorate: false}. */
    private static boolean decorates(ChunkStageContext context) {
        return context.settings().at(DecorateSettings.class, context.centreX(), context.centreZ()).dimension()
                .map(DecorateSettings::decorate).orElse(true);
    }

    /** Decorates the 16 x 16 columns of the chunk whose first block is {@code x}, {@code z}, a biome's decorators on the surface its blocks left. */
    static void decorate(DecorationScene scene, FeatureSettings settings, int x, int z) {
        Hunk<BlockState> output = scene.blocks();
        TerrainSampler terrain = scene.terrain();
        BiomeDecorators decorators = scene.decorators();
        IrisSurfaceDecorator surfaceDecorator = new IrisSurfaceDecorator(scene);
        IrisSeaSurfaceDecorator seaSurfaceDecorator = new IrisSeaSurfaceDecorator(scene);
        IrisSeaFloorDecorator seaFloorDecorator = new IrisSeaFloorDecorator(scene);
        IrisShoreLineDecorator shoreLineDecorator = new IrisShoreLineDecorator(scene);
        int fluidHeight = scene.fluidHeight();
        int worldHeight = scene.worldHeight();

        for (int i = 0; i < output.width(); i++) {
            int realX = Math.round(x + i);
            for (int j = 0; j < output.depth(); j++) {
                int realZ = Math.round(z + j);
                DecoratedBiome biome = BiomeDecorators.biomeOf(settings, realX, realZ, terrain.surfaceKind(realX, realZ));
                if (!decorators.hasDecorators(biome)) {
                    continue;
                }
                int height = terrain.surfaceHeight(realX, realZ);

                if (height >= 0 && height < fluidHeight && scene.isSolid(output.get(i, height, j))
                        && height + 1 < output.height() && scene.isWater(output.get(i, height + 1, j))) {
                    seaSurfaceDecorator.decorate(i, j,
                            realX, Math.round(i + 1), Math.round(x + i - 1),
                            realZ, Math.round(z + j + 1), Math.round(z + j - 1),
                            biome, fluidHeight, worldHeight);
                    seaFloorDecorator.decorate(i, j,
                            realX, realZ, biome, height + 1,
                            fluidHeight + 1);
                }

                if (height == fluidHeight) {
                    shoreLineDecorator.decorate(i, j,
                            realX, Math.round(x + i + 1), Math.round(x + i - 1),
                            realZ, Math.round(z + j + 1), Math.round(z + j - 1),
                            biome, height, worldHeight);
                }

                if (height >= 0 && height < output.height()) {
                    surfaceDecorator.decorate(i, j, realX, realZ, biome, height, worldHeight - height);
                }
            }
        }
    }
}
