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

package art.arcane.iris.generation.decoration;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import lombok.experimental.Accessors;

@Snippet("deposit-variant")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Remaps ore block ids to alternate block ids within a vertical band. Ores declared at dimension, region, and biome scope can be rewritten at placement time (for example, iron_ore -> deepslate_iron_ore inside a deep carving band, or yourmod:iron -> yourmod:moon_iron inside a lunar biome).")
@Data
public class IrisDepositVariant {
    private final transient AtomicCache<KMap<String, PlatformBlockState>> resolved = new AtomicCache<>();

    @Required
    @MinNumber(-2048)
    @MaxNumber(8192)
    @Description("Inclusive minimum world Y this variant applies at.")
    private int minHeight = 0;

    @Required
    @MinNumber(-2048)
    @MaxNumber(8192)
    @Description("Inclusive maximum world Y this variant applies at.")
    private int maxHeight = 0;

    @Required
    @Description("Source block id (for example `minecraft:iron_ore`) -> replacement block id (for example `minecraft:deepslate_iron_ore`). Any block id the data loader resolves is accepted, including external/mod blocks. Source match is by material only, so block properties on the source key are ignored.")
    private KMap<String, String> remap = new KMap<>();

    public PlatformBlockState remapOrNull(PlatformBlockState ore, IrisData rdata) {
        if (ore == null || remap == null || remap.isEmpty()) {
            return null;
        }

        KMap<String, PlatformBlockState> map = resolved.aquire(() -> buildResolved(rdata));
        return map.get(IrisProceduralBlocks.materialKey(ore));
    }

    private KMap<String, PlatformBlockState> buildResolved(IrisData rdata) {
        KMap<String, PlatformBlockState> out = new KMap<>();

        for (java.util.Map.Entry<String, String> entry : remap.entrySet()) {
            PlatformBlockState source = B.getStateOrNull(entry.getKey(), false);
            PlatformBlockState target = B.getStateOrNull(entry.getValue(), true);

            if (source == null || target == null) {
                continue;
            }

            out.put(IrisProceduralBlocks.materialKey(source), target);
        }

        return out;
    }
}
