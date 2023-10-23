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
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.util.BlockVector;

import java.io.IOException;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor

@Desc("Represents a structure tile")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisJigsawPiece extends IrisRegistrant {
    @RegistryListResource(IrisObject.class)
    @Required
    @Desc("The object this piece represents")
    private String object = "";

    @Required
    @ArrayType(type = IrisJigsawPieceConnector.class, min = 1)
    @Desc("The connectors this object contains")
    private KList<IrisJigsawPieceConnector> connectors = new KList<>();

    @Desc("Configure everything about the object placement. Please don't define this unless you actually need it as using this option will slow down the jigsaw deign stage. Use this where you need it, just avoid using it everywhere to keep things fast.")
    private IrisObjectPlacement placementOptions = new IrisObjectPlacement().setMode(ObjectPlaceMode.FAST_MAX_HEIGHT);

    private transient AtomicCache<Integer> max2dDim = new AtomicCache<>();
    private transient AtomicCache<Integer> max3dDim = new AtomicCache<>();

    public int getMax2dDimension() {
        return max2dDim.aquire(() -> {
            try {
                BlockVector v = IrisObject.sampleSize(getLoader().getObjectLoader().findFile(getObject()));
                return Math.max(v.getBlockX(), v.getBlockZ());
            } catch (IOException e) {
                Iris.reportError(e);
                e.printStackTrace();
            }

            return 0;
        });
    }

    public int getMax3dDimension() {
        return max3dDim.aquire(() -> {
            try {
                BlockVector v = IrisObject.sampleSize(getLoader().getObjectLoader().findFile(getObject()));
                return Math.max(Math.max(v.getBlockX(), v.getBlockZ()), v.getBlockY());
            } catch (IOException e) {
                Iris.reportError(e);
                e.printStackTrace();
            }

            return -1;
        });
    }


    public IrisJigsawPieceConnector getConnector(IrisPosition relativePosition) {
        for (IrisJigsawPieceConnector i : connectors) {
            if (i.getPosition().equals(relativePosition)) {
                return i;
            }
        }

        return null;
    }

    public IrisJigsawPiece copy() {
        IrisJigsawPiece p = new IrisJigsawPiece();
        p.setObject(getObject());
        p.setLoader(getLoader());
        p.setLoadKey(getLoadKey());
        p.setLoadFile(getLoadFile());
        p.setConnectors(new KList<>());
        p.setPlacementOptions(getPlacementOptions());

        for (IrisJigsawPieceConnector i : getConnectors()) {
            p.getConnectors().add(i.copy());
        }

        return p;
    }

    public boolean isTerminal() {
        return connectors.size() == 1;
    }

    public ObjectPlaceMode getPlaceMode() {
        return getPlacementOptions().getMode();
    }

    @Override
    public String getFolderName() {
        return "jigsaw-pieces";
    }

    @Override
    public String getTypeName() {
        return "Jigsaw Piece";
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
