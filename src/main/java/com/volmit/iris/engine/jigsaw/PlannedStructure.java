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

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.RNG;
import lombok.Data;
import org.bukkit.Axis;
import org.bukkit.World;

@Data
public class PlannedStructure {
    private static ConcurrentLinkedHashMap<String, IrisObject> objectRotationCache
            = new ConcurrentLinkedHashMap.Builder<String, IrisObject>()
            .initialCapacity(64)
            .maximumWeightedCapacity(1024)
            .concurrencyLevel(32)
            .build();
    private KList<PlannedPiece> pieces;
    private IrisJigsawStructure structure;
    private IrisPosition position;
    private IrisData data;
    private RNG rng;
    private boolean verbose;
    private boolean terminating;

    public PlannedStructure(IrisJigsawStructure structure, IrisPosition position, RNG rng) {
        terminating = false;
        verbose = true;
        this.pieces = new KList<>();
        this.structure = structure;
        this.position = position;
        this.rng = rng;
        this.data = structure.getLoader();
        generateStartPiece();

        for (int i = 0; i < structure.getMaxDepth(); i++) {
            generateOutwards();
        }

        generateTerminators();

        Iris.debug("JPlace: ROOT @ relative " + position.toString());

        for (PlannedPiece i : pieces) {
            Iris.debug("Place: " + i.getObject().getLoadKey() + " at @ relative " + i.getPosition().toString());
        }
    }

    public void place(IObjectPlacer placer, Mantle e, Engine eng) {
        IrisObjectPlacement options = new IrisObjectPlacement();
        options.getRotation().setEnabled(false);
        int startHeight = pieces.get(0).getPosition().getY();

        for (PlannedPiece i : pieces) {
            place(i, startHeight, options, placer, e, eng);
        }
    }

    public void place(PlannedPiece i, int startHeight, IrisObjectPlacement o, IObjectPlacer placer, Mantle e, Engine eng) {
        IrisObjectPlacement options = o;

        if (i.getPiece().getPlacementOptions() != null) {
            options = i.getPiece().getPlacementOptions();
            options.getRotation().setEnabled(false);
        } else {
            options.setMode(i.getPiece().getPlaceMode());
        }

        IrisObject vo = i.getOgObject();
        IrisObject v = i.getObject();
        int sx = (v.getW() / 2);
        int sz = (v.getD() / 2);
        int xx = i.getPosition().getX() + sx;
        int zz = i.getPosition().getZ() + sz;
        RNG rngf = new RNG(Cache.key(xx, zz));
        int offset = i.getPosition().getY() - startHeight;
        int height;

        if (i.getStructure().getStructure().getLockY() == -1) {
            if (i.getStructure().getStructure().getOverrideYRange() != null) {
                height = (int) i.getStructure().getStructure().getOverrideYRange().get(rng, xx, zz, getData());
            } else {
                height = placer.getHighest(xx, zz, getData());
            }
        } else {
            height = i.getStructure().getStructure().getLockY();
        }

        height += offset + (v.getH() / 2);

        if (options.getMode().equals(ObjectPlaceMode.PAINT)) {
            height = -1;
        }

        int id = rng.i(0, Integer.MAX_VALUE);
        vo.place(xx, height, zz, placer, options, rng, (b, data) -> e.set(b.getX(), b.getY(), b.getZ(), v.getLoadKey() + "@" + id), null, getData());
    }

    public void place(World world) {
        for (PlannedPiece i : pieces) {
            Iris.sq(() -> i.place(world));
        }
    }

    private void generateOutwards() {
        for (PlannedPiece i : getPiecesWithAvailableConnectors().shuffle(rng)) {
            if (!generatePieceOutwards(i)) {
                i.setDead(true);
            }
        }
    }

    private boolean generatePieceOutwards(PlannedPiece piece) {
        boolean b = false;

        for (IrisJigsawPieceConnector i : piece.getAvailableConnectors().shuffleCopy(rng)) {
            if (generateConnectorOutwards(piece, i)) {
                b = true;
            }
        }

        return b;
    }

    private boolean generateConnectorOutwards(PlannedPiece piece, IrisJigsawPieceConnector pieceConnector) {
        for (IrisJigsawPiece i : getShuffledPiecesFor(pieceConnector)) {
            if (generateRotatedPiece(piece, pieceConnector, i)) {
                return true;
            }
        }

        return false;
    }

    private boolean generateRotatedPiece(PlannedPiece piece, IrisJigsawPieceConnector pieceConnector, IrisJigsawPiece idea) {
        if (!piece.getPiece().getPlacementOptions().getRotation().isEnabled()) {
            if (generateRotatedPiece(piece, pieceConnector, idea, 0, 0, 0)) {
                return true;
            }
        }

        KList<Integer> forder1 = new KList<Integer>().qadd(0).qadd(1).qadd(2).qadd(3).shuffle(rng);
        KList<Integer> forder2 = new KList<Integer>().qadd(0).qadd(1).qadd(2).qadd(3).shuffle(rng);

        for (Integer i : forder1) {
            if (pieceConnector.isRotateConnector()) {
                assert pieceConnector.getDirection().getAxis() != null;
                if (!pieceConnector.getDirection().getAxis().equals(Axis.Y)) {
                    for (Integer j : forder2) {
                        if (pieceConnector.getDirection().getAxis().equals(Axis.X) && generateRotatedPiece(piece, pieceConnector, idea, j, i, 0)) {
                            return true;
                        }

                        if (pieceConnector.getDirection().getAxis().equals(Axis.Z) && generateRotatedPiece(piece, pieceConnector, idea, 0, i, j)) {
                            return true;
                        }
                    }
                }
            }

            if (generateRotatedPiece(piece, pieceConnector, idea, 0, i, 0)) {
                return true;
            }
        }

        return false;
    }

    private boolean generateRotatedPiece(PlannedPiece piece, IrisJigsawPieceConnector pieceConnector, IrisJigsawPiece idea, IrisObjectRotation rotation) {
        if (!idea.getPlacementOptions().getRotation().isEnabled()) {
            rotation = piece.getRotation();
        }

        PlannedPiece test = new PlannedPiece(this, piece.getPosition(), idea, rotation);

        for (IrisJigsawPieceConnector j : test.getPiece().getConnectors().shuffleCopy(rng)) {
            if (generatePositionedPiece(piece, pieceConnector, test, j)) {
                return true;
            }
        }

        return false;
    }

    private boolean generateRotatedPiece(PlannedPiece piece, IrisJigsawPieceConnector pieceConnector, IrisJigsawPiece idea, int x, int y, int z) {
        return generateRotatedPiece(piece, pieceConnector, idea, IrisObjectRotation.of(x, y, z));
    }

    private boolean generatePositionedPiece(PlannedPiece piece, IrisJigsawPieceConnector pieceConnector, PlannedPiece test, IrisJigsawPieceConnector testConnector) {
        test.setPosition(new IrisPosition(0, 0, 0));
        IrisPosition connector = piece.getWorldPosition(pieceConnector);
        IrisDirection desiredDirection = pieceConnector.getDirection().reverse();
        IrisPosition desiredPosition = connector.sub(new IrisPosition(desiredDirection.toVector()));

        if (!pieceConnector.getTargetName().equals("*") && !pieceConnector.getTargetName().equals(testConnector.getName())) {
            return false;
        }

        if (!testConnector.getDirection().equals(desiredDirection)) {
            return false;
        }

        IrisPosition shift = test.getWorldPosition(testConnector);
        test.setPosition(desiredPosition.sub(shift));

        if (collidesWith(test, piece)) {
            return false;
        }

        piece.connect(pieceConnector);
        test.connect(testConnector);
        pieces.add(test);

        return true;
    }

    private KList<IrisJigsawPiece> getShuffledPiecesFor(IrisJigsawPieceConnector c) {
        KList<IrisJigsawPiece> p = new KList<>();

        for (String i : c.getPools().shuffleCopy(rng)) {
            for (String j : getData().getJigsawPoolLoader().load(i).getPieces().shuffleCopy(rng)) {
                IrisJigsawPiece pi = getData().getJigsawPieceLoader().load(j);

                if (pi == null || (terminating && !pi.isTerminal())) {
                    continue;
                }

                p.addIfMissing(pi);
            }
        }
        return p.shuffle(rng);
    }

    private void generateStartPiece() {
        pieces.add(new PlannedPiece(this, position, getData().getJigsawPieceLoader().load(rng.pick(getStructure().getPieces())), 0, rng.nextInt(4), 0));
    }

    private void generateTerminators() {
        if (getStructure().isTerminate()) {
            terminating = true;
            generateOutwards();
        }
    }

    public KList<PlannedPiece> getPiecesWithAvailableConnectors() {
        return pieces.copy().removeWhere(PlannedPiece::isFull);
    }

    public int getVolume() {
        int v = 0;

        for (PlannedPiece i : pieces) {
            v += i.getObject().getH() * i.getObject().getW() * i.getObject().getD();
        }

        return v;
    }

    public int getMass() {
        int v = 0;

        for (PlannedPiece i : pieces) {
            v += i.getObject().getBlocks().size();
        }

        return v;
    }

    public boolean collidesWith(PlannedPiece piece, PlannedPiece ignore) {
        for (PlannedPiece i : pieces) {
            if (i.equals(ignore)) {
                continue;
            }

            if (i.collidesWith(piece)) {
                return true;
            }
        }

        return false;
    }

    public boolean contains(IrisPosition p) {
        for (PlannedPiece i : pieces) {
            if (i.contains(p)) {
                return true;
            }
        }

        return false;
    }

    public IrisObject rotated(IrisJigsawPiece piece, IrisObjectRotation rotation) {
        String key = piece.getObject() + "-" + rotation.hashCode();

        return objectRotationCache.computeIfAbsent(key, (k) -> rotation.rotateCopy(data.getObjectLoader().load(piece.getObject())));
    }
}
