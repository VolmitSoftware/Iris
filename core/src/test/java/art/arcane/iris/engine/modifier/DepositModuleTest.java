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

import art.arcane.iris.builtin.deposit.DepositSettings;
import art.arcane.iris.engine.object.terrain.deposit.IrisDepositGenerator;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings;
import com.volmit.resource.ResourceId;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class DepositModuleTest {
    private static final int CENTRE = 7;

    @Test
    public void everyLevelAtTheCentreColumnStacksInLevelOrder() {
        IrisDepositGenerator dimension = new IrisDepositGenerator();
        IrisDepositGenerator region = new IrisDepositGenerator();
        IrisDepositGenerator biome = new IrisDepositGenerator();
        IrisDepositGenerator elsewhere = new IrisDepositGenerator();
        FeatureSettings settings = new FeatureSettings() {
            @Override
            @SuppressWarnings("unchecked")
            public <F extends IrisFeature> List<Entry<F>> at(Class<F> type, int worldX, int worldZ) {
                if (worldX != 32 + CENTRE || worldZ != -16 + CENTRE) {
                    return List.of((Entry<F>) entry(Target.BIOME, "biome/other", elsewhere));
                }
                return List.of((Entry<F>) entry(Target.DIMENSION, "dimension/overworld", dimension),
                        (Entry<F>) entry(Target.REGION, "region/hot", region),
                        (Entry<F>) entry(Target.BIOME, "biome/desert", biome));
            }
        };

        assertEquals(List.of(dimension, region, biome), DepositModule.generatorsAtCentre(settings, 32, -16));
    }

    private static FeatureSettings.Entry<DepositSettings> entry(Target level, String source, IrisDepositGenerator generator) {
        return new FeatureSettings.Entry<>(level, ResourceId.parse("pack:" + source), new DepositSettings(List.of(generator)));
    }
}
