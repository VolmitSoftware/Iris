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

import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Desc("Represents a dimension")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisMod extends IrisRegistrant {
    @MinNumber(2)
    @Required
    @Desc("The human readable name of this dimension")
    private String name = "A Dimension Mod";

    @Desc("If this mod only works with a specific dimension, define it's load key here. Such as overworld, or flat. Otherwise iris will assume this mod works with anything.")
    private String forDimension = "";

    @MinNumber(-1)
    @MaxNumber(512)
    @Desc("Override the fluid height. Otherwise set it to -1")
    private int overrideFluidHeight = -1;

    @Desc("A list of biomes to remove")
    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class, min = 1)
    private KList<String> removeBiomes = new KList<>();

    @Desc("A list of objects to remove")
    @RegistryListResource(IrisObject.class)
    @ArrayType(type = String.class, min = 1)
    private KList<String> removeObjects = new KList<>();

    @Desc("A list of regions to remove")
    @RegistryListResource(IrisRegion.class)
    @ArrayType(type = String.class, min = 1)
    private KList<String> removeRegions = new KList<>();

    @Desc("A list of regions to inject")
    @RegistryListResource(IrisRegion.class)
    @ArrayType(type = String.class, min = 1)
    private KList<String> injectRegions = new KList<>();

    @ArrayType(min = 1, type = IrisModBiomeInjector.class)
    @Desc("Inject biomes into existing regions")
    private KList<IrisModBiomeInjector> biomeInjectors = new KList<>();

    @ArrayType(min = 1, type = IrisModBiomeReplacer.class)
    @Desc("Replace biomes with other biomes")
    private KList<IrisModBiomeReplacer> biomeReplacers = new KList<>();

    @ArrayType(min = 1, type = IrisModObjectReplacer.class)
    @Desc("Replace objects with other objects")
    private KList<IrisModObjectReplacer> objectReplacers = new KList<>();

    @ArrayType(min = 1, type = IrisModObjectPlacementBiomeInjector.class)
    @Desc("Inject placers into existing biomes")
    private KList<IrisModObjectPlacementBiomeInjector> biomeObjectPlacementInjectors = new KList<>();

    @ArrayType(min = 1, type = IrisModObjectPlacementRegionInjector.class)
    @Desc("Inject placers into existing regions")
    private KList<IrisModObjectPlacementRegionInjector> regionObjectPlacementInjectors = new KList<>();

    @ArrayType(min = 1, type = IrisModRegionReplacer.class)
    @Desc("Replace biomes with other biomes")
    private KList<IrisModRegionReplacer> regionReplacers = new KList<>();

    @ArrayType(min = 1, type = IrisObjectReplace.class)
    @Desc("Replace blocks with other blocks")
    private KList<IrisObjectReplace> blockReplacers = new KList<>();

    @ArrayType(min = 1, type = IrisModNoiseStyleReplacer.class)
    @Desc("Replace noise styles with other styles")
    private KList<IrisModNoiseStyleReplacer> styleReplacers = new KList<>();

    @Override
    public String getFolderName() {
        return "mods";
    }

    @Override
    public String getTypeName() {
        return "Mod";
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
