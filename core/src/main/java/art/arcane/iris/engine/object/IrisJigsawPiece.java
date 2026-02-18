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

package art.arcane.iris.engine.object;

import art.arcane.iris.Iris;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.iris.util.common.plugin.VolmitSender;
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

    @ArrayType(type = IrisJigsawPieceConnector.class)
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
        var gson = getLoader().getGson();
        IrisJigsawPiece copy = gson.fromJson(gson.toJson(this), IrisJigsawPiece.class);
        copy.setLoader(getLoader());
        copy.setLoadKey(getLoadKey());
        copy.setLoadFile(getLoadFile());
        return copy;
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
