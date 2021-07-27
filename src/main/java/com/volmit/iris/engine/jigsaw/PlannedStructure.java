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

package com.volmit.iris.engine.jigsaw;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.tools.IrisWorlds;
import com.volmit.iris.engine.framework.EngineParallaxManager;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.interpolation.InterpolationMethod;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.engine.object.common.IObjectPlacer;
import com.volmit.iris.engine.parallax.ParallaxChunkMeta;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.math.RNG;
import lombok.Data;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

@Data
public class PlannedStructure {
    private KList<PlannedPiece> pieces;
    private IrisJigsawStructure structure;
    private IrisPosition position;
    private IrisDataManager data;
    private static KMap<String, IrisObject> objectRotationCache;
    private RNG rng;
    private boolean verbose;
    private boolean terminating;

    public PlannedStructure(IrisJigsawStructure structure, IrisPosition position, RNG rng) {
        terminating = false;
        objectRotationCache = new KMap<>();
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
    }

    public KList<Runnable> place(IObjectPlacer placer, EngineParallaxManager e) {
        KList<Runnable> after = new KList<>();
        IrisObjectPlacement options = new IrisObjectPlacement();
        options.getRotation().setEnabled(false);
        int startHeight = pieces.get(0).getPosition().getY();

        for (PlannedPiece i : pieces) {
            if (i.getPiece().getPlaceMode().equals(ObjectPlaceMode.VACUUM)) {
                place(i, startHeight, options, placer, e);
            } else {
                after.add(() -> place(i, startHeight, options, placer, e));
            }
        }

        return after;
    }

    public void place(PlannedPiece i, int startHeight, IrisObjectPlacement o, IObjectPlacer placer, EngineParallaxManager e) {
        IrisObjectPlacement options = o;

        if (i.getPiece().getPlacementOptions() != null) {
            options = i.getPiece().getPlacementOptions();
            options.getRotation().setEnabled(false);
        } else {
            options.setMode(i.getPiece().getPlaceMode());
        }

        IrisObject v = i.getObject();
        int sx = (v.getW() / 2);
        int sz = (v.getD() / 2);
        int xx = i.getPosition().getX() + sx;
        int zz = i.getPosition().getZ() + sz;
        int offset = i.getPosition().getY() - startHeight;
        int height = placer.getHighest(xx, zz) + offset + (v.getH() / 2);

        if (options.getMode().equals(ObjectPlaceMode.PAINT) || options.isVacuum()) {
            height = -1;
        }

        int id = rng.i(0, Integer.MAX_VALUE);

        int h = v.place(xx, height, zz, placer, options, rng, (b) -> {
            int xf = b.getX();
            int yf = b.getY();
            int zf = b.getZ();
            e.getParallaxAccess().setObject(xf, yf, zf, v.getLoadKey() + "@" + id);
            ParallaxChunkMeta meta = e.getParallaxAccess().getMetaRW(xf >> 4, zf >> 4);
            meta.setObjects(true);
            meta.setMinObject(Math.min(Math.max(meta.getMinObject(), 0), yf));
            meta.setMaxObject(Math.max(Math.max(meta.getMaxObject(), 0), yf));
        }, null, getData());


        for (IrisJigsawPieceConnector j : i.getAvailableConnectors()) {
            if (j.getSpawnEntity() != null)// && h != -1)
            {
                IrisPosition p;
                if (j.getEntityPosition() == null) {
                    p = i.getWorldPosition(j).add(new IrisPosition(j.getDirection().toVector().multiply(2)));
                } else {
                    p = i.getWorldPosition(j).add(j.getEntityPosition());
                }

                if (options.getMode().equals(ObjectPlaceMode.PAINT) || options.isVacuum()) {
                    p.setY(placer.getHighest(xx, zz) + offset + (v.getH() / 2));
                } else {
                    p.setY(height);
                }
                for (int k = 0; k < j.getEntityCount(); k++) {
                    e.getParallaxAccess().setEntity(p.getX(), p.getY(), p.getZ(), j.getSpawnEntity());
                }
            }
        }

        if (options.isVacuum()) {
            double a = Math.max(v.getW(), v.getD());
            IrisFeature f = new IrisFeature();
            f.setConvergeToHeight(h - (v.getH() >> 1) - 1);
            f.setBlockRadius(a);
            f.setInterpolationRadius(a / 4);
            f.setInterpolator(InterpolationMethod.BILINEAR_STARCAST_9);
            f.setStrength(1D);
            e.getParallaxAccess().getMetaRW(xx >> 4, zz >> 4)
                    .getFeatures()
                    .add(new IrisFeaturePositional(xx, zz, f));
        }
    }

    public void place(World world) {
        for (PlannedPiece i : pieces) {
            Iris.sq(() -> {
                for (IrisJigsawPieceConnector j : i.getAvailableConnectors()) {
                    if (j.getSpawnEntity() != null) {
                        IrisAccess a = IrisWorlds.access(world);
                        if (a == null) {
                            Iris.warn("Cannot spawn entities from jigsaw in non Iris world!");
                            break;
                        }
                        IrisPosition p = i.getWorldPosition(j).add(new IrisPosition(j.getDirection().toVector().multiply(2)));
                        IrisEntity e = getData().getEntityLoader().load(j.getSpawnEntity());

                        if (a != null) {
                            Entity entity = e.spawn(a.getCompound().getEngineForHeight(p.getY()), new Location(world, p.getX() + 0.5, p.getY(), p.getZ() + 0.5), rng);
                            if (j.isKeepEntity()) {
                                entity.setPersistent(true);
                            }
                        }
                    }
                }
            });

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
        if (!idea.getPlacementOptions().getRotation().isEnabled())
            rotation = piece.getRotation(); //Inherit parent rotation

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

    private boolean generatePositionedPiece(PlannedPiece piece,
                                            IrisJigsawPieceConnector pieceConnector,
                                            PlannedPiece test,
                                            IrisJigsawPieceConnector testConnector) {
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
        KList<PlannedPiece> collision = collidesWith(test);

        if (pieceConnector.isInnerConnector() && collision.isNotEmpty()) {
            for (PlannedPiece i : collision) {
                if (i.equals(piece)) {
                    continue;
                }

                return false;
            }
        } else if (collision.isNotEmpty()) {
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

    public KList<PlannedPiece> collidesWith(PlannedPiece piece) {
        KList<PlannedPiece> v = new KList<>();
        for (PlannedPiece i : pieces) {
            if (i.collidesWith(piece)) {
                v.add(i);
            }
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

        if (objectRotationCache.containsKey(key)) {
            IrisObject o = objectRotationCache.get(key);

            if (o != null) {
                return o;
            }
        }

        IrisObject o = rotation.rotateCopy(data.getObjectLoader().load(piece.getObject()));
        objectRotationCache.put(key, o);
        return o;
    }
}
