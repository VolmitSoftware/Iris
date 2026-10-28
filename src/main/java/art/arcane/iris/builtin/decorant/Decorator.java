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

package art.arcane.iris.builtin.decorant;

import art.arcane.iris.builtin.prepare.SlopeClip;
import com.volmit.block.BlockSpec;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.config.Snippet;
import com.volmit.noise.NoiseStyle;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One decorator of a biome, as packs write it in the {@code "decorators"} list or under
 * {@code snippet/iris/decorant/decorator/}: which blocks it places, where, how often and how tall. A value of IrisApi types only; the
 * decorant module resolves the blocks and builds the noise.
 */
@Snippet("iris/decorant/decorator")
@Desc("A biome decorator is used for placing flowers, grass, cacti and so on")
public final class Decorator {
    private static final String STATIC_NOISE = "STATIC";
    private static final String DEFAULT_BLOCK = "grass";

    @Desc("The varience dispersion is used when multiple blocks are put in the palette. Scatter scrambles them, Wispy shows streak-looking varience")
    private NoiseStyle variance = NoiseStyle.of(STATIC_NOISE);

    @Desc("Forcefully place this decorant anywhere it is supposed to go even if it should not go on a specific surface block. For example, you could force tallgrass to place on top of stone by using this.")
    private boolean forcePlace = false;

    @Desc("Forced the surface block of this decorant to be the specified block. Assumes forcePlace.")
    private BlockSpec forceBlock;

    @MinItems(1)
    @Desc("When set, the decorator can only place onto any of these blocks.")
    private List<BlockSpec> whitelist;

    @MinItems(1)
    @Desc("When set, the decorator will never place onto any of these blocks.")
    private List<BlockSpec> blacklist;

    @Desc("The slope at which this decorator can be placed. Range from 0 to 10 by default. Calculated from a 3-block radius from the center of the decorator placement.")
    private SlopeClip slopeCondition = new SlopeClip();

    @Desc("If stackMax is set to true, use this to limit its max height for large caverns")
    private int absoluteMaxStack = 30;

    @Desc("Dispersion is used to pick places to spawn. Scatter randomly places them (vanilla) or Wispy for a streak like patch system.")
    private NoiseStyle style = NoiseStyle.of(STATIC_NOISE);

    @Desc("If this decorator has a height more than 1 this changes how it picks the height between your maxes. Scatter = random, Wispy = wavy heights")
    private NoiseStyle heightVariance = NoiseStyle.of(STATIC_NOISE);

    @Desc("Tells iris where this decoration is a part of. I.e. SHORE_LINE or SEA_SURFACE")
    private DecorationPart partOf = DecorationPart.NONE;

    @MinNumber(1)
    @MaxNumber(2032)
    @Desc("The minimum repeat stack height (setting to 3 would stack 3 of <block> on top of each other")
    private int stackMin = 1;

    @MinNumber(1)
    @MaxNumber(2032)
    @Desc("The maximum repeat stack height")
    private int stackMax = 1;

    @Desc("""
            Changes stackMin and stackMin from being absolute block heights and instead uses them as a percentage to scale the stack based on the cave height

            Within a cave, setting them stackMin/max to 50 would make the stack 50% of the cave height""")
    private boolean scaleStack = false;

    @Required
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance for this decorator to decorate at a given X,Y coordinate. This is hit 256 times per chunk (per surface block)")
    private double chance = 0.1;

    @Required
    @MinItems(1)
    @Desc("The palette of blocks to pick from when this decorator needs to place.")
    private List<BlockSpec> palette = List.of(BlockSpec.of(DEFAULT_BLOCK));

    @MinItems(1)
    @Desc("The palette of blocks used at the very top of a 'stackMax' of higher than 1. For example, bamboo tops.")
    private List<BlockSpec> topPalette = List.of();

    @MinNumber(0.01)
    @MaxNumber(1.0)
    @Desc("When the stack passes the top threshold, the top palette will start being used instead of the normal palette.")
    private double topThreshold = 1.0;

    /** A decorator with every option at its default, as packs get it from {@code {}}. */
    public Decorator() {
    }

    public NoiseStyle variance() {
        return variance == null ? NoiseStyle.of(STATIC_NOISE) : variance;
    }

    public boolean forcePlace() {
        return forcePlace;
    }

    public Optional<BlockSpec> forceBlock() {
        return Optional.ofNullable(forceBlock);
    }

    /** The blocks it may only be placed onto; empty when unset, which {@link #hasWhitelist()} tells apart from an explicitly empty list that blocks all placement. */
    public List<BlockSpec> whitelist() {
        return listOrEmpty(whitelist);
    }

    public boolean hasWhitelist() {
        return whitelist != null;
    }

    public List<BlockSpec> blacklist() {
        return listOrEmpty(blacklist);
    }

    public boolean hasBlacklist() {
        return blacklist != null;
    }

    public SlopeClip slopeCondition() {
        return slopeCondition == null ? new SlopeClip() : slopeCondition;
    }

    public int absoluteMaxStack() {
        return absoluteMaxStack;
    }

    public NoiseStyle style() {
        return style == null ? NoiseStyle.of(STATIC_NOISE) : style;
    }

    public NoiseStyle heightVariance() {
        return heightVariance == null ? NoiseStyle.of(STATIC_NOISE) : heightVariance;
    }

    public DecorationPart partOf() {
        return partOf == null ? DecorationPart.NONE : partOf;
    }

    public int stackMin() {
        return stackMin;
    }

    public int stackMax() {
        return stackMax;
    }

    public boolean scaleStack() {
        return scaleStack;
    }

    public double chance() {
        return chance;
    }

    public List<BlockSpec> palette() {
        return listOrEmpty(palette);
    }

    public List<BlockSpec> topPalette() {
        return listOrEmpty(topPalette);
    }

    public double topThreshold() {
        return topThreshold;
    }

    /** Whether it places more than one block per column. */
    public boolean isStacking() {
        return stackMax > 1;
    }

    private static <T> List<T> listOrEmpty(List<T> list) {
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }
}
