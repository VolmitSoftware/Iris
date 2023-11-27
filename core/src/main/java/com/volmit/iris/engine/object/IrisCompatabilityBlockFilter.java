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

package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.util.data.B;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Find and replace object materials for compatability")
@Data
public class IrisCompatabilityBlockFilter {
    private final transient AtomicCache<BlockData> findData = new AtomicCache<>(true);
    private final transient AtomicCache<BlockData> replaceData = new AtomicCache<>(true);
    @Required
    @Desc("When iris sees this block, and it's not reconized")
    private String when = "";
    @Required
    @Desc("Replace it with this block. Dont worry if this block is also not reconized, iris repeat this compat check.")
    private String supplement = "";
    @Desc("If exact is true, it compares block data for example minecraft:some_log[axis=x]")
    private boolean exact = false;

    public IrisCompatabilityBlockFilter(String when, String supplement) {
        this(when, supplement, false);
    }

    public BlockData getFind() {
        return findData.aquire(() -> B.get(when));
    }

    public BlockData getReplace() {
        return replaceData.aquire(() ->
        {
            BlockData b = B.getOrNull(supplement);

            if (b == null) {
                return null;
            }

            Iris.warn("Compat: Using " + supplement + " in place of " + when + " since this server doesnt support '" + supplement + "'");

            return b;
        });
    }
}
