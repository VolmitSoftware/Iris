/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.jigsaw;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.math.AxisAlignedBB;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.List;

@Data
public class PlannedPiece {
    private IrisPosition position;
    private IrisObject object;
    private IrisObject ogObject;
    private IrisJigsawPiece piece;
    private IrisObjectRotation rotation;
    @EqualsAndHashCode.Exclude
    private IrisData data;
    private KList<IrisJigsawPieceConnector> connected;
    private boolean dead = false;
    private AxisAlignedBB box;
    @EqualsAndHashCode.Exclude
    private PlannedStructure structure;
    @EqualsAndHashCode.Exclude
    @Setter(AccessLevel.NONE)
    private ParentConnection parent = null;
    @EqualsAndHashCode.Exclude
    @Setter(AccessLevel.NONE)
    private KMap<IrisJigsawPieceConnector, IrisPosition> realPositions;

    public PlannedPiece(PlannedStructure structure, IrisPosition position, IrisJigsawPiece piece) {
        this(structure, position, piece, 0, 0, 0);
    }

    public PlannedPiece(PlannedStructure structure, IrisPosition position, IrisJigsawPiece piece, int rx, int ry, int rz) {
        this(structure, position, piece, IrisObjectRotation.of(rx * 90D, ry * 90D, rz * 90D));
    }

    public PlannedPiece(PlannedStructure structure, IrisPosition position, IrisJigsawPiece piece, IrisObjectRotation rot) {
        this.structure = structure;
        this.position = position;
        this.data = piece.getLoader();
        this.setRotation(rot);
        this.ogObject = data.getObjectLoader().load(piece.getObject());
        this.object = structure.rotated(piece, rotation);
        this.piece = rotation.rotateCopy(piece);
        this.piece.setLoadKey(piece.getLoadKey());
        this.object.setLoadKey(piece.getObject());
        this.ogObject.setLoadKey(piece.getObject());
        this.connected = new KList<>();
        this.realPositions = new KMap<>();

    }

    public void setPosition(IrisPosition p) {
        this.position = p;
        box = null;
    }

    public String toString() {
        return piece.getLoadKey() + "@(" + position.getX() + "," + position.getY() + "," + position.getZ() + ")[rot:" + rotation.toString() + "]";
    }

    public AxisAlignedBB getBox() {
        if (box != null) {
            return box;
        }

        BlockVector v = getObject().getCenter();
        IrisPosition pos = new IrisPosition();
        IrisObjectPlacement options = piece.getPlacementOptions();
        if (options != null && options.getTranslate() != null) {
            IrisObjectTranslate translate = options.getTranslate();
            pos.setX(translate.getX());
            pos.setY(translate.getY());
            pos.setZ(translate.getZ());
        }
        box = object.getAABB().shifted(position.add(new IrisPosition(object.getCenter())).add(pos));
        return box;
    }

    public boolean contains(IrisPosition p) {
        return getBox().contains(p);
    }

    public boolean collidesWith(PlannedPiece p) {
        return getBox().intersects(p.getBox());
    }

    public KList<IrisJigsawPieceConnector> getAvailableConnectors() {
        if (connected.isEmpty()) {
            return piece.getConnectors().copy();
        }

        if (connected.size() == piece.getConnectors().size()) {
            return new KList<>();
        }

        KList<IrisJigsawPieceConnector> c = new KList<>();

        for (IrisJigsawPieceConnector i : piece.getConnectors()) {
            if (!connected.contains(i)) {
                c.add(i);
            }
        }

        return c;
    }

    public KList<IrisJigsawPieceConnector> getChildConnectors() {
        ParentConnection pc = getParent();
        KList<IrisJigsawPieceConnector> c = getConnected().copy();
        if (pc != null) c.removeIf(i -> i.equals(pc.connector));
        return c;
    }

    public boolean connect(IrisJigsawPieceConnector c, PlannedPiece p, IrisJigsawPieceConnector pc) {
        if (piece.getConnectors().contains(c) && p.getPiece().getConnectors().contains(pc)) {
            if (connected.contains(c) || p.connected.contains(pc)) return false;
            connected.add(c);
            p.connected.add(pc);
            p.parent = new ParentConnection(this, c, p, pc);
            return true;
        }
        return false;
    }

    public IrisPosition getWorldPosition(IrisJigsawPieceConnector c) {
        return getWorldPosition(c.getPosition());
    }

    public List<IrisPosition> getConnectorWorldPositions() {
        List<IrisPosition> worldPositions = new ArrayList<>();

        for (IrisJigsawPieceConnector connector : this.piece.getConnectors()) {
            IrisPosition worldPosition = getWorldPosition(connector.getPosition());
            worldPositions.add(worldPosition);
        }

        return worldPositions;
    }

    public IrisPosition getWorldPosition(IrisPosition position) {
        return this.position.add(position).add(new IrisPosition(object.getCenter()));
    }

    public void debugPrintConnectorPositions() {
        Iris.debug("Connector World Positions for PlannedPiece at " + position + ":");
        List<IrisPosition> connectorPositions = getConnectorWorldPositions();
        for (IrisPosition pos : connectorPositions) {
            Iris.debug(" - Connector at: " + pos);
        }
    }

    public boolean isFull() {
        return connected.size() >= piece.getConnectors().size();
    }

    public void setRealPositions(int x, int y, int z, IObjectPlacer placer) {
        boolean isUnderwater = piece.getPlacementOptions().isUnderwater();
        for (IrisJigsawPieceConnector c : piece.getConnectors()) {
            var pos = c.getPosition().add(new IrisPosition(x, 0, z));
            if (y < 0) {
                pos.setY(pos.getY() + placer.getHighest(pos.getX(), pos.getZ(), getData(), isUnderwater) + (object.getH() / 2));
            } else {
                pos.setY(pos.getY() + y);
            }
            realPositions.put(c, pos);
        }
    }

    public record ParentConnection(PlannedPiece parent,
                                   IrisJigsawPieceConnector parentConnector,
                                   PlannedPiece self,
                                   IrisJigsawPieceConnector connector) {
        public IrisPosition getTargetPosition() {
            var pos = parent.realPositions.get(parentConnector);
            if (pos == null) return null;
            return pos.add(new IrisPosition(parentConnector.getDirection().toVector()))
                    .sub(connector.getPosition())
                    .sub(new IrisPosition(self.object.getCenter()));
        }
    }
}
