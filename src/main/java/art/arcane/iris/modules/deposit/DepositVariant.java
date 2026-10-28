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

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.Map;

/**
 * One deposit ore remap rule, an entry of {@code depositVariants}: within a vertical band, ore blocks are rewritten to
 * alternate blocks. A value of IrisApi types only; {@code ResolvedDepositVariant} resolves the blocks.
 */
@Snippet("iris/deposit/deposit-variant")
@Desc("Remaps ore block ids to alternate block ids within a vertical band. Ores declared at dimension, region, and biome scope can be rewritten at placement time (for example, iron_ore -> deepslate_iron_ore inside a deep carving band, or yourmod:iron -> yourmod:moon_iron inside a lunar biome).")
public final class DepositVariant {
    @Required
    @MinNumber(-2048)
    @MaxNumber(8192)
    @Desc("Inclusive minimum world Y this variant applies at.")
    private int minHeight = 0;

    @Required
    @MinNumber(-2048)
    @MaxNumber(8192)
    @Desc("Inclusive maximum world Y this variant applies at.")
    private int maxHeight = 0;

    @Required
    @Desc("Source block id (for example `minecraft:iron_ore`) -> replacement block id (for example `minecraft:deepslate_iron_ore`). Any block id the data loader resolves is accepted, including external/mod blocks. Source match is by material only, so block properties on the source key are ignored.")
    private Map<String, String> remap = Map.of();

    public DepositVariant() {
    }

    public int minHeight() {
        return minHeight;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public Map<String, String> remap() {
        return remap == null ? Map.of() : Collections.unmodifiableMap(remap);
    }
}
