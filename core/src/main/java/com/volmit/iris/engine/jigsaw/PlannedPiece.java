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

package com.volmit.iris.engine.jigsaw;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.AxisAlignedBB;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ALL")
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

    public boolean connect(IrisJigsawPieceConnector c) {
        if (piece.getConnectors().contains(c)) {
            return connected.addIfMissing(c);
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
}
