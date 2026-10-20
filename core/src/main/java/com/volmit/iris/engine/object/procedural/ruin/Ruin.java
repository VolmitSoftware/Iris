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

package com.volmit.iris.engine.object.procedural.ruin;

import com.volmit.iris.engine.object.ObjectPlaceMode;
import com.volmit.iris.engine.object.CarvingMode;
import com.volmit.iris.engine.object.IrisObjectRotation;
import com.volmit.iris.engine.object.IrisObjectTranslate;
import com.volmit.iris.engine.object.IrisStiltSettings;
import com.volmit.iris.engine.object.IrisObjectLimit;
import com.volmit.iris.engine.object.IrisMaterialPalette;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListBlockType;
import com.volmit.iris.engine.object.annotations.Required;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Snippet("ruin")
@Desc("A single procedurally generated ruin: a crumbling man-made structure (pillar, wall, arch, foundation slab, or rubble pile). Iris bakes a pool of deterministic variants from these settings and scatters them at world-gen time, exactly like an object placement but generated from scratch instead of loaded from an iob file. Weathering swaps blocks for mossy or cracked variants by noise, erosion deletes blocks so the shape reads as collapsed, and a buried fraction sinks the ruin into the terrain.")
public final class Ruin {
    @Desc("A human readable name used in logs and as the variant load key (procedural/<name>#<index>).")
    private String name = "ruin";

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance (0-1) per chunk for this ruin to attempt placement. Use density to attempt more than one per chunk once the chance check passes.")
    private double chance = 0.05;

    @MinNumber(1)
    @Desc("If the chance check passes, attempt this many placements in the chunk. Defaults to a single ruin per qualifying chunk.")
    private int density = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("How many distinct variants to pre-bake for this ruin. Higher means more variety (different heights, gap patterns, erosion seeds) at a small memory cost.")
    private int variants = 6;

    @Desc("The base seed for deterministic generation. The same seed and settings always bake the same variants. Every random choice (size, weathering mask, erosion mask, accents) is derived from this.")
    private long seed = 1337;

    @Desc("The placement mode used to anchor the ruin to the terrain. MIN_HEIGHT plants the lowest footprint corner on the ground (good for slabs and rubble on slopes); CENTER_HEIGHT averages the footprint (good for tall pillars and arches).")
    private ObjectPlaceMode mode = ObjectPlaceMode.MIN_HEIGHT;

    @Desc("Rotate this ruin's placement so variants do not all face the same way.")
    private IrisObjectRotation rotation = new IrisObjectRotation();

    @Desc("Limit the max or min terrain height at which this ruin may place.")
    private IrisObjectLimit clamp = new IrisObjectLimit();

    @Desc("Whether this ruin may place on the terrain surface, under carvings (caves), or both. SURFACE_ONLY keeps ruins above ground.")
    private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;

    @Desc("If true the ruin anchors on the terrain height ignoring the water surface, so it can sit submerged. If false (default) it anchors to the surface and avoids water.")
    private boolean underwater = false;

    @Desc("Translate (offset) this placement along each axis; for example set a negative y to sink it into the ground.")
    private IrisObjectTranslate translate = new IrisObjectTranslate();

    @Desc("Settings for the stilt place modes (STILT, MIN_STILT, FAST_STILT, CENTER_STILT, ERODE_STILT, ORGANIC_STILT).")
    private IrisStiltSettings stiltSettings;

    @Required
    @Desc("The primary structural block, e.g. minecraft:cobblestone or minecraft:stone_bricks. Ignored when blockPalette is set. This is the bulk material of the ruin before weathering swaps some of it out.")
    @RegistryListBlockType
    private String block = "minecraft:cobblestone";

    @Desc("A noise-driven palette for the primary block. When set this overrides the single block, letting the structure mix materials by noise. Palette wins over the block string via IrisProceduralBlocks.resolve.")
    private IrisMaterialPalette blockPalette;

    @Desc("The overall silhouette of the ruin: PILLAR (broken column), WALL (gapped segment), ARCH (two legs plus a curved span), FLOOR_SLAB (foundation patch), or RUBBLE (low scattered pile).")
    private RuinForm form = RuinForm.PILLAR;

    @MinNumber(1)
    @Desc("Minimum structure height in blocks (PILLAR/WALL/ARCH height, slab thickness for FLOOR_SLAB, mound height for RUBBLE). Variants interpolate between this and heightMax.")
    private int heightMin = 4;

    @MinNumber(1)
    @Desc("Maximum structure height in blocks. Variants interpolate between heightMin and this so taller and shorter ruins coexist.")
    private int heightMax = 9;

    @MinNumber(1)
    @Desc("Minimum footprint width (X axis) in blocks: column/leg thickness for PILLAR/ARCH, wall thickness for WALL, slab width for FLOOR_SLAB, blob width for RUBBLE.")
    private int widthMin = 1;

    @MinNumber(1)
    @Desc("Maximum footprint width (X axis) in blocks. Variants interpolate between widthMin and this.")
    private int widthMax = 3;

    @MinNumber(1)
    @Desc("Minimum footprint length (Z axis) in blocks: wall run length, arch leg spacing, slab length, rubble blob length.")
    private int lengthMin = 3;

    @MinNumber(1)
    @Desc("Maximum footprint length (Z axis) in blocks. Variants interpolate between lengthMin and this.")
    private int lengthMax = 7;

    @Desc("A palette of weathered variants (mossy/cracked block ids) blended over the structure by noise. When set this drives weathering; otherwise weatheredBlock is used.")
    private IrisMaterialPalette weatheringPalette;

    @Desc("A single weathered block id used when weatheringPalette is not set, e.g. minecraft:mossy_cobblestone. Applied to a noise-selected fraction of the structure, biased toward the lower rows.")
    @RegistryListBlockType
    private String weatheredBlock = "minecraft:mossy_cobblestone";

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How much of the structure is replaced by weathered blocks (0 none, 1 nearly all). The replacement mask is value-noise driven and weighted so lower rows weather more than upper rows, mimicking moss climbing from the ground.")
    private double mossiness = 0.45;

    @MinNumber(0)
    @MaxNumber(8)
    @Desc("The scale of the weathering noise. Higher values produce smaller, busier moss/crack patches; lower values produce broad continuous weathered zones.")
    private double weatheringScale = 1.0;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How crumbled the ruin is (0 intact, 1 mostly gone). Blocks whose value-noise falls below this threshold are deleted, so the shape reads as collapsed. Structural integrity is preserved: the bottom row and core legs are never eroded away.")
    private double erosion = 0.25;

    @MinNumber(0)
    @MaxNumber(8)
    @Desc("The scale of the erosion noise. Higher values knock out small speckled holes; lower values carve larger missing chunks out of the silhouette.")
    private double erosionScale = 1.5;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How far the structure extends below the surface as a fraction of its height (0 sits fully on top, 1 sinks it a full height into the ground). The buried rows live at negative y so the ruin reads as settled and partly swallowed by the terrain.")
    private double buriedFraction = 0.2;

    @ArrayType(min = 1, type = RuinDecorator.class)
    @Desc("Accent decorators applied after the ruin is built and eroded (moss carpets and lanterns on broken tops, vines and lichen on the faces, rubble and growth scattered around the base).")
    private List<RuinDecorator> accents = List.of();

    public Ruin() {
    }

    public String name() {
        return name;
    }

    public double chance() {
        return chance;
    }

    public int density() {
        return density;
    }

    public int variants() {
        return variants;
    }

    public long seed() {
        return seed;
    }

    public ObjectPlaceMode mode() {
        return mode == null ? ObjectPlaceMode.MIN_HEIGHT : mode;
    }

    public IrisObjectRotation rotation() {
        return rotation;
    }

    public IrisObjectLimit clamp() {
        return clamp;
    }

    public CarvingMode carvingSupport() {
        return carvingSupport == null ? CarvingMode.SURFACE_ONLY : carvingSupport;
    }

    public boolean underwater() {
        return underwater;
    }

    public IrisObjectTranslate translate() {
        return translate;
    }

    /** Empty where the pack sets none. */
    public Optional<IrisStiltSettings> stiltSettings() {
        return Optional.ofNullable(stiltSettings);
    }

    /** Empty where the pack sets none. */

    public String block() {
        return block;
    }

    /** Empty where the pack sets none. */
    public Optional<IrisMaterialPalette> blockPalette() {
        return Optional.ofNullable(blockPalette);
    }

    public RuinForm form() {
        return form == null ? RuinForm.PILLAR : form;
    }

    public int heightMin() {
        return heightMin;
    }

    public int heightMax() {
        return heightMax;
    }

    public int widthMin() {
        return widthMin;
    }

    public int widthMax() {
        return widthMax;
    }

    public int lengthMin() {
        return lengthMin;
    }

    public int lengthMax() {
        return lengthMax;
    }

    /** Empty where the pack sets none. */
    public Optional<IrisMaterialPalette> weatheringPalette() {
        return Optional.ofNullable(weatheringPalette);
    }

    public String weatheredBlock() {
        return weatheredBlock;
    }

    public double mossiness() {
        return mossiness;
    }

    public double weatheringScale() {
        return weatheringScale;
    }

    public double erosion() {
        return erosion;
    }

    public double erosionScale() {
        return erosionScale;
    }

    public double buriedFraction() {
        return buriedFraction;
    }

    public List<RuinDecorator> accents() {
        return accents == null ? List.of() : Collections.unmodifiableList(accents);
    }
}
