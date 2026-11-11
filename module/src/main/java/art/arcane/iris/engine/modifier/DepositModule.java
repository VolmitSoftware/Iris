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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.modules.deposit.DepositGenerator;
import art.arcane.iris.modules.deposit.DepositSettings;
import art.arcane.iris.modules.deposit.DepositVariantSettings;
import art.arcane.iris.modules.deposit.OreFrequencySettings;
import art.arcane.iris.modules.deposit.OreSizeSettings;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.config.FeatureSettings;
import com.volmit.stage.ChunkStageContext;

import java.util.List;

/**
 * Module {@code iris:deposit}: ores and other deposits. The dimension, a region and a biome each declare their
 * own {@link DepositSettings}, and they stack: a chunk gets every level's deposits at its centre column, the
 * dimension's first, then the region's, then the biome's. The stage runs where any column of the chunk has
 * deposits, but places only what the centre column declares, so a chunk's deposits do not depend on which
 * of its columns a biome border crosses.
 */
public final class DepositModule implements IrisModule {
    private static final String VERSION = "1.0.0";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:deposit", "Deposits", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        var registry = setup.registry();
        registry.registerSettings(DepositVariantSettings.class);
        registry.registerSettings(OreFrequencySettings.class);
        registry.registerSettings(OreSizeSettings.class);

        setup.stages().define(IrisStages.Deposit.ORES)
                .when(DepositSettings.class)
                .requires(IrisStages.Terrain.SHAPE)
                .softAfter(IrisStages.Floating.SOLIDS)
                .run(DepositModule::place);
    }

    private static void place(ChunkStageContext context) {
        List<DepositGenerator> generators = generatorsAt(context.settings(), context.centreX(), context.centreZ());
        if (generators.isEmpty()) {
            return;
        }
        DepositPlacer.of(context.world()).generate(context, generators);
    }

    /** Every level's generators at the column {@code x}, {@code z}, in level order. */
    static List<DepositGenerator> generatorsAt(FeatureSettings settings, int x, int z) {
        return settings.at(DepositSettings.class, x, z).values().stream()
                .flatMap(declared -> declared.generators().stream())
                .toList();
    }
}
