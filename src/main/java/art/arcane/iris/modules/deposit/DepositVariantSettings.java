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

package art.arcane.iris.modules.deposit;

import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

import java.util.Collections;
import java.util.List;

/**
 * The deposit ore remap rules one dimension, region or biome declares, written as {@code "depositVariants": [...]}, the list
 * of {@link DepositVariant}s. The biome's rules apply first, then the region's, then the dimension's; the first matching rule wins.
 */
@Desc("Deposit ore remap rules. Each entry declares a vertical band and a source->replacement block id map. Applied biome first, then region, then dimension; first matching rule wins.")
@Feature(id = "iris:deposit/variants", name = "Deposit variants", key = "depositVariants", root = "variants",
        targets = {Level.DIMENSION, Level.REGION, Level.BIOME})
public final class DepositVariantSettings implements IrisFeature {
    @Desc("Deposit ore remap rules. Each entry declares a vertical band and a source->replacement block id map. Applied biome first, then region, then dimension; first matching rule wins.")
    private List<DepositVariant> variants = List.of();

    public DepositVariantSettings() {
    }

    public DepositVariantSettings(List<DepositVariant> variants) {
        this.variants = List.copyOf(variants);
    }

    public List<DepositVariant> variants() {
        return variants == null ? List.of() : Collections.unmodifiableList(variants);
    }
}
