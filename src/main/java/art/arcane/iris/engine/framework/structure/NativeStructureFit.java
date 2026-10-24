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

package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.builtin.structure.StructureStilts;
import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.builtin.structure.StructureYBand;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.volmit.vanilla.StartMemo;

/**
 * How {@code iris:structure} places and fits one platform structure, or one start: how far it moves the platform's own
 * starts and into which band of world heights, whether they keep the platform's height, the foundation columns built
 * under their pieces and how they meet the terrain. A start the module places itself carries its fit as a memo, one
 * JSON object of its terrain and stilts, which the host keeps with the start, so its later chunks use the fit it began with.
 *
 * @param yBand null for no band
 * @param stilts null for no foundation columns
 * @param terrain null for the structure's own terrain adaptation (SOURCE)
 */
public record NativeStructureFit(int yShift, StructureYBand yBand, boolean keepsSourceY, StructureStilts stilts,
                                 StructureTerrain terrain) {
    /** The platform's starts as the platform makes them. */
    public static final NativeStructureFit NONE = new NativeStructureFit(0, null, false, null, null);

    private static final Gson MEMO_JSON = new Gson();
    private static final String MEMO_TERRAIN = "terrain";
    private static final String MEMO_STILTS = "stilts";

    /** The fit a start's memo records: its terrain and stilts, with no vertical adjustment. */
    public static NativeStructureFit of(StartMemo memo) {
        JsonObject text = MEMO_JSON.fromJson(memo.text(), JsonObject.class);
        if (text == null || !text.has(MEMO_TERRAIN)) {
            throw new IllegalStateException("A start's memo has no terrain fit: " + memo);
        }
        StructureTerrain terrain = MEMO_JSON.fromJson(text.get(MEMO_TERRAIN), StructureTerrain.class);
        StructureStilts stilts = text.has(MEMO_STILTS) ? MEMO_JSON.fromJson(text.get(MEMO_STILTS), StructureStilts.class) : null;
        return new NativeStructureFit(0, null, false, stilts, terrain);
    }

    /** The memo of a start placed with this fit's terrain and stilts: one JSON object holding both. */
    public StartMemo memo() {
        JsonObject text = new JsonObject();
        text.add(MEMO_TERRAIN, MEMO_JSON.toJsonTree(resolvedTerrain()));
        if (stilts != null) {
            text.add(MEMO_STILTS, MEMO_JSON.toJsonTree(stilts));
        }
        return new StartMemo(MEMO_JSON.toJson(text));
    }

    /** The terrain fitting; the structure's own adaptation (SOURCE) when none is set. */
    public StructureTerrain resolvedTerrain() {
        return terrain == null ? new StructureTerrain() : terrain;
    }

    public NativeStructureFit shifted(int blocks) {
        return new NativeStructureFit(Math.addExact(yShift, blocks), yBand, keepsSourceY, stilts, terrain);
    }

    /**
     * Both fits, {@code later} after this one: shifts add up, keeping the platform's height sticks, and a band, stilts
     * or terrain {@code later} states replaces this one's.
     */
    public NativeStructureFit then(NativeStructureFit later) {
        return new NativeStructureFit(Math.addExact(yShift, later.yShift),
                later.yBand == null ? yBand : later.yBand,
                keepsSourceY || later.keepsSourceY,
                later.stilts == null ? stilts : later.stilts,
                later.terrain == null ? terrain : later.terrain);
    }
}
