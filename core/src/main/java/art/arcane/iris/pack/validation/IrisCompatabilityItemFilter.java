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

package art.arcane.iris.pack.validation;

import art.arcane.iris.platform.bukkit.BukkitBlockResolution;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.Required;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Material;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Find and replace object items for compatability")
@Data
public class IrisCompatabilityItemFilter {
    private final transient AtomicCache<Material> findData = new AtomicCache<>(true);
    private final transient AtomicCache<Material> replaceData = new AtomicCache<>(true);
    @Required
    @Description("When iris sees this block, and it's not reconized")
    private String when = "";
    @Required
    @Description("Replace it with this block. Dont worry if this block is also not reconized, iris repeat this compat check.")
    private String supplement = "";

    public IrisCompatabilityItemFilter(String when, String supplement) {
        this.when = when;
        this.supplement = supplement;
    }

    public Material getFind() {
        return findData.aquire(() -> BukkitBlockResolution.getMaterial(when));
    }

    public Material getReplace() {
        return replaceData.aquire(() ->
        {
            Material b = BukkitBlockResolution.getMaterialOrNull(supplement);

            if (b == null) {
                return null;
            }

            IrisLogging.debug("Compat: Using " + supplement + " in place of " + when + " since this server doesnt support '" + supplement + "'");

            return b;
        });
    }
}
