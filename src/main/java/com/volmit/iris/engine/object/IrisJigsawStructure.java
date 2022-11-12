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
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.data.cache.AtomicCache;
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
@NoArgsConstructor
@AllArgsConstructor

@Desc("Represents a jigsaw structure")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisJigsawStructure extends IrisRegistrant {
    @RegistryListResource(IrisJigsawPiece.class)
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("The starting pieces. Randomly chooses a starting piece, then connects pieces using the pools define in the starting piece.")
    private KList<String> pieces = new KList<>();

    @MaxNumber(32)
    @MinNumber(1)
    @Desc("The maximum pieces that can step out from the center piece")
    private int maxDepth = 9;

    @Desc("Jigsaw grows the parallax layer which slows iris down a bit. Since there are so many pieces, Iris takes the avg piece size and calculates the parallax radius from that. Unless your structures are using only the biggest pieces, your structure should fit in the chosen size fine. If you are seeing cut-off parts of your structures or broken terrain, turn this option on. This option will pick the biggest piece dimensions and multiply it by your (maxDepth+1) * 2 as the size to grow the parallax layer by. But typically keep this off.")
    private boolean useMaxPieceSizeForParallaxRadius = false;

    @Desc("If set to true, iris will look for any pieces with only one connector in valid pools for edge connectors and attach them to 'terminate' the paths/piece connectors. Essentially it caps off ends. For example in a village, Iris would add houses to the ends of roads where possible. For terminators to be selected, they can only have one connector or they wont be chosen.")
    private boolean terminate = true;

    @Desc("Override the y range instead of placing on the height map")
    private IrisStyledRange overrideYRange = null;

    @Desc("Force Y to a specific value")
    private int lockY = -1;

    private transient AtomicCache<Integer> maxDimension = new AtomicCache<>();

    private void loadPool(String p, KList<String> pools, KList<String> pieces) {
        if (p.isEmpty()) {
            return;
        }

        IrisJigsawPool pool = getLoader().getJigsawPoolLoader().load(p);

        if (pool == null) {
            Iris.warn("Can't find jigsaw pool: " + p);
            return;
        }

        for (String i : pool.getPieces()) {
            if (pieces.addIfMissing(i)) {
                loadPiece(i, pools, pieces);
            }
        }
    }

    private void loadPiece(String p, KList<String> pools, KList<String> pieces) {
        IrisJigsawPiece piece = getLoader().getJigsawPieceLoader().load(p);

        if (piece == null) {
            Iris.warn("Can't find jigsaw piece: " + p);
            return;
        }

        for (IrisJigsawPieceConnector i : piece.getConnectors()) {
            for (String j : i.getPools()) {
                if (pools.addIfMissing(j)) {
                    loadPool(j, pools, pieces);
                }
            }
        }
    }

    public int getMaxDimension() {
        return maxDimension.aquire(() -> {
            if (useMaxPieceSizeForParallaxRadius) {
                int max = 0;
                KList<String> pools = new KList<>();
                KList<String> pieces = new KList<>();

                for (String i : getPieces()) {
                    loadPiece(i, pools, pieces);
                }

                for (String i : pieces) {
                    max = Math.max(max, getLoader().getJigsawPieceLoader().load(i).getMax3dDimension());
                }

                return max * (((getMaxDepth() + 1) * 2) + 1);
            } else {
                KList<String> pools = new KList<>();
                KList<String> pieces = new KList<>();

                for (String i : getPieces()) {
                    loadPiece(i, pools, pieces);
                }

                int avg = 0;

                for (String i : pieces) {
                    avg += getLoader().getJigsawPieceLoader().load(i).getMax2dDimension();
                }

                return (avg / (pieces.size() > 0 ? pieces.size() : 1)) * (((getMaxDepth() + 1) * 2) + 1);
            }
        });
    }

    @Override
    public String getFolderName() {
        return "jigsaw-structures";
    }

    @Override
    public String getTypeName() {
        return "Jigsaw Structure";
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
