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

package art.arcane.iris.builtin.deposit;

import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

import java.util.List;

/**
 * The deposits one dimension, region or biome declares, written as {@code "deposits": [...]}, the list of generators. Levels stack:
 * a chunk gets the deposits of its dimension, its centre column's region and that column's biome. IrisApi types only,
 * like {@link DepositGenerator}.
 */
@Desc("Ores and other deposits generated in every chunk where this applies")
@Feature(id = "iris:deposit", name = "Deposits", key = "deposits", root = "generators", targets = {Target.DIMENSION, Target.REGION, Target.BIOME})
public final class DepositSettings implements IrisFeature {
    @Desc("Define deposit generators that this applies to chunks")
    private List<DepositGenerator> generators = List.of();

    public DepositSettings() {
    }

    public DepositSettings(List<DepositGenerator> generators) {
        this.generators = List.copyOf(generators);
    }

    public List<DepositGenerator> generators() {
        return generators;
    }
}
