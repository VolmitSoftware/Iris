/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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
import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.cache.AtomicCache;
import com.volmit.iris.engine.data.B;
import com.volmit.iris.engine.data.DataProvider;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.noise.CNG;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.DependsOn;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListBiome;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.common.IRare;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.data.VanillaBiomeMap;
import com.volmit.iris.util.inventorygui.RandomColor;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.awt.*;

@SuppressWarnings("DefaultAnnotationParam")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor

@Desc("Represents a cave biome in iris. Cave biomes are placed inside of caves and hold objects.\nA biome consists of layers (block palletes), decorations, objects & generators.")
@Data
@EqualsAndHashCode(callSuper = true)
public class IrisCaveBiome extends IrisBiome {

    @Required
    @ArrayType(min = 1, type = IrisBiomePaletteLayer.class)
    @Desc("This defines the layers of materials in this biome. Each layer has a palette and min/max height and some other properties. Usually a grassy/sandy layer then a dirt layer then a stone layer. Iris will fill in the remaining blocks below your layers with stone.")
    private KList<IrisBiomePaletteLayer> lavaLayers = new KList<IrisBiomePaletteLayer>().qadd(new IrisBiomePaletteLayer());

    @Required
    @ArrayType(min = 1, type = IrisBiomePaletteLayer.class)
    @Desc("This defines the layers of materials in this biome. Each layer has a palette and min/max height and some other properties. Usually a grassy/sandy layer then a dirt layer then a stone layer. Iris will fill in the remaining blocks below your layers with stone.")
    private KList<IrisBiomePaletteLayer> waterLayers = new KList<IrisBiomePaletteLayer>().qadd(new IrisBiomePaletteLayer());


    public KList<IrisBiomePaletteLayer> getLavaLayers() {
        return lavaLayers.isEmpty() ? getLayers() : lavaLayers;
    }

    public KList<IrisBiomePaletteLayer> getWaterLayers() {
        return waterLayers.isEmpty() ? getLayers() : waterLayers;
    }

    public boolean freezeLava = false;
    public boolean freezeWater = false;
}
