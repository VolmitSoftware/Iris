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

package art.arcane.iris.pack.mod;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.validation.CompatPools;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectReplace;

import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Description("Represents a pack modification schema")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisMod extends IrisRegistrant {
    @MinNumber(2)
    @Required
    @Description("The human-readable name of this pack modification")
    private String name = "A Pack Modification";

    @Description("The optional dimension load key this modification targets. An empty value does not restrict the target.")
    private String forDimension = "";

    @MinNumber(-1)
    @MaxNumber(512)
    @Description("Override the fluid height. Otherwise set it to -1")
    private int overrideFluidHeight = -1;

    @Description("A list of biomes to remove")
    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class, min = 1)
    private KList<String> removeBiomes = new KList<>();

    @Description("A list of objects to remove")
    @RegistryListResource(IrisObject.class)
    @ArrayType(type = String.class, min = 1)
    private KList<String> removeObjects = new KList<>();

    @Description("A list of regions to remove")
    @RegistryListResource(IrisRegion.class)
    @ArrayType(type = String.class, min = 1)
    private KList<String> removeRegions = new KList<>();

    @Description("A list of regions to inject")
    @RegistryListResource(IrisRegion.class)
    @ArrayType(type = String.class, min = 1)
    private KList<String> injectRegions = new KList<>();

    @ArrayType(min = 1, type = IrisModBiomeInjector.class)
    @Description("Inject biomes into existing regions")
    private KList<IrisModBiomeInjector> biomeInjectors = new KList<>();

    @ArrayType(min = 1, type = IrisModBiomeReplacer.class)
    @Description("Replace biomes with other biomes")
    private KList<IrisModBiomeReplacer> biomeReplacers = new KList<>();

    @ArrayType(min = 1, type = IrisModObjectReplacer.class)
    @Description("Replace objects with other objects")
    private KList<IrisModObjectReplacer> objectReplacers = new KList<>();

    @ArrayType(min = 1, type = IrisModObjectPlacementBiomeInjector.class)
    @Description("Inject placers into existing biomes")
    private KList<IrisModObjectPlacementBiomeInjector> biomeObjectPlacementInjectors = new KList<>();

    @ArrayType(min = 1, type = IrisModObjectPlacementRegionInjector.class)
    @Description("Inject placers into existing regions")
    private KList<IrisModObjectPlacementRegionInjector> regionObjectPlacementInjectors = new KList<>();

    @ArrayType(min = 1, type = IrisModRegionReplacer.class)
    @Description("Replace regions with other regions")
    private KList<IrisModRegionReplacer> regionReplacers = new KList<>();

    @ArrayType(min = 1, type = IrisObjectReplace.class)
    @Description("Replace blocks with other blocks")
    private KList<IrisObjectReplace> blockReplacers = new KList<>();

    @ArrayType(min = 1, type = IrisModNoiseStyleReplacer.class)
    @Description("Replace noise styles with other styles")
    private KList<IrisModNoiseStyleReplacer> styleReplacers = new KList<>();

    /**
     * A pack modification only references other registrants, so it never cascades: excluded biome and region
     * references are dropped from its lists and reported, and the modification keeps applying whatever is left.
     */
    @Override
    public CompatStatus evaluateCompat(ContentGate gate) {
        CompatStatus base = super.evaluateCompat(gate);
        IrisData data = getLoader();

        if (data == null) {
            return base;
        }

        removeBiomes = biomes(data, removeBiomes, "removeBiomes");
        removeRegions = regions(data, removeRegions, "removeRegions");
        injectRegions = regions(data, injectRegions, "injectRegions");

        for (int index = 0; index < biomeInjectors.size(); index++) {
            IrisModBiomeInjector injector = biomeInjectors.get(index);
            injector.setInject(biomes(data, injector.getInject(), "biomeInjectors[" + index + "].inject"));
        }

        for (int index = 0; index < biomeReplacers.size(); index++) {
            IrisModBiomeReplacer replacer = biomeReplacers.get(index);
            replacer.setFind(biomes(data, replacer.getFind(), "biomeReplacers[" + index + "].find"));
        }

        for (int index = 0; index < regionReplacers.size(); index++) {
            IrisModRegionReplacer replacer = regionReplacers.get(index);
            replacer.setFind(regions(data, replacer.getFind(), "regionReplacers[" + index + "].find"));
        }

        return base;
    }

    private KList<String> biomes(IrisData data, KList<String> keys, String field) {
        return CompatPools.surviving(data.getBiomeLoader(), keys, data, "mod", getLoadKey(), field);
    }

    private KList<String> regions(IrisData data, KList<String> keys, String field) {
        return CompatPools.surviving(data.getRegionLoader(), keys, data, "mod", getLoadKey(), field);
    }

    @Override
    public String getFolderName() {
        return "mods";
    }

    @Override
    public String getTypeName() {
        return "Mod";
    }
}
