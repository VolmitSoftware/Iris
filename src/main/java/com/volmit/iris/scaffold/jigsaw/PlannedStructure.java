package com.volmit.iris.scaffold.jigsaw;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.*;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;
import lombok.Data;
import org.bukkit.Axis;
import org.bukkit.World;

@Data
public class PlannedStructure {
    private KList<PlannedPiece> pieces;
    private IrisJigsawStructure structure;
    private IrisPosition position;
    private IrisDataManager data;
    private RNG rng;
    private boolean verbose;

    public PlannedStructure(IrisJigsawStructure structure, IrisPosition position, RNG rng)
    {
        verbose = true;
        this.pieces = new KList<>();
        this.structure = structure;
        this.position = position;
        this.rng = rng;
        this.data = structure.getLoader();
        v("Creating Structure " + structure.getLoadKey() + " at " + position.toString() + " with depth of " + structure.getMaxDepth());
        generateStartPiece();

        for(int i = 0; i < structure.getMaxDepth(); i++)
        {
            v("--- Layer " + i + " ---");
            generateOutwards(i);
        }

        generateTerminators();
    }

    public void v(String v)
    {
        if(!verbose)
        {
            return;
        }

        Iris.info("[Jigsaw]: " + v);
    }

    public void place(World world)
    {
        for(PlannedPiece i : pieces)
        {
            i.place(world);
        }
    }

    private void generateOutwards(int layer) {
        if(getPiecesWithAvailableConnectors().isEmpty())
        {
            v("There are no more available pieces with open connections!");
        }

        for(PlannedPiece i : getPiecesWithAvailableConnectors().shuffleCopy(rng))
        {
            v("  Expanding Piece: " + i.toString());
            generatePieceOutwards(i);
        }
    }

    private boolean generatePieceOutwards(PlannedPiece piece) {
        if(piece.getAvailableConnectors().isEmpty())
        {
            v("Piece " + piece.toString() + " has no more connectors!");
        }

        for(IrisJigsawPieceConnector i : piece.getAvailableConnectors().shuffleCopy(rng))
        {
            v("    Expanding Piece " + piece + " Connector: " + i);
            if(generateConnectorOutwards(piece, i))
            {

            }
        }

        return false;
    }

    private boolean generateConnectorOutwards(PlannedPiece piece, IrisJigsawPieceConnector pieceConnector) {
        for(IrisJigsawPiece i : getShuffledPiecesFor(pieceConnector))
        {
            if(generateRotatedPiece(piece, pieceConnector, i))
            {
                return true;
            }
        }

        return false;
    }

    private boolean generateRotatedPiece(PlannedPiece piece, IrisJigsawPieceConnector pieceConnector, IrisJigsawPiece idea) {
        KList<Integer> forder1 = new KList<Integer>().qadd(0).qadd(1).qadd(2).qadd(3).shuffle(rng);
        KList<Integer> forder2 = new KList<Integer>().qadd(0).qadd(1).qadd(2).qadd(3).shuffle(rng);

        for(Integer i : forder1)
        {
            if(pieceConnector.isRotateConnector() && !pieceConnector.getDirection().getAxis().equals(Axis.Y))
            {
                for(Integer j : forder2)
                {
                    if(pieceConnector.getDirection().getAxis().equals(Axis.X) && generateRotatedPiece(piece, pieceConnector, idea, j, i, 0))
                    {
                        return true;
                    }

                    if(pieceConnector.getDirection().getAxis().equals(Axis.Z) && generateRotatedPiece(piece, pieceConnector, idea, 0, i, j))
                    {
                        return true;
                    }
                }
            }

            if (generateRotatedPiece(piece, pieceConnector, idea, 0, i, 0))
            {
                return true;
            }
        }

        return false;
    }

    private boolean generateRotatedPiece(PlannedPiece piece, IrisJigsawPieceConnector pieceConnector, IrisJigsawPiece idea, int x, int y, int z)
    {
        PlannedPiece test = new PlannedPiece(piece.getPosition(), idea, x, y, z);

        for(IrisJigsawPieceConnector j : test.getPiece().getConnectors().shuffleCopy(rng))
        {
            if(generatePositionedPiece(piece, pieceConnector, test, j))
            {
                return true;
            }
        }

        return false;
    }

    private boolean generatePositionedPiece(PlannedPiece piece,
                                            IrisJigsawPieceConnector pieceConnector,
                                            PlannedPiece test,
                                            IrisJigsawPieceConnector testConnector) {
        test.setPosition(new IrisPosition(0,0,0));
        IrisPosition connector = piece.getWorldPosition(pieceConnector);
        IrisDirection desiredDirection = pieceConnector.getDirection().reverse();
        IrisPosition desiredPosition = connector.sub(new IrisPosition(desiredDirection.toVector()));

        if(!testConnector.getDirection().equals(desiredDirection))
        {
            return false;
        }

        IrisPosition shift = test.getWorldPosition(testConnector);
        test.setPosition(desiredPosition.sub(shift));

        if(collidesWith(test))
        {
            return false;
        }


        v("Connected {" + test + "/" + testConnector + "} <==> {" + piece + "/" + pieceConnector + "}");
        piece.connect(pieceConnector);
        test.connect(testConnector);

        pieces.add(test);

        return true;
    }

    private KList<IrisJigsawPiece> getShuffledPiecesFor(IrisJigsawPieceConnector c)
    {
        KList<IrisJigsawPiece> p = new KList<>();

        for(String i : c.getPools().shuffleCopy(rng))
        {
            for(String j : getData().getJigsawPoolLoader().load(i).getPieces().shuffleCopy(rng))
            {
                p.addIfMissing(getData().getJigsawPieceLoader().load(j));
            }
        }

        return p.shuffleCopy(rng);
    }

    private void generateStartPiece() {
        pieces.add(new PlannedPiece(position, getData().getJigsawPieceLoader().load(rng.pick(getStructure().getPieces())), 0, rng.nextInt(4), 0));
    }

    private void generateTerminators() {
    }

    public KList<PlannedPiece> getPiecesWithAvailableConnectors()
    {
        return pieces.copy().removeWhere(PlannedPiece::isFull);
    }

    public int getVolume()
    {
        int v = 0;

        for(PlannedPiece i : pieces)
        {
            v += i.getObject().getH() * i.getObject().getW() * i.getObject().getD();
        }

        return v;
    }

    public int getMass()
    {
        int v = 0;

        for(PlannedPiece i : pieces)
        {
            v += i.getObject().getBlocks().size();
        }

        return v;
    }

    public boolean collidesWith(PlannedPiece piece)
    {
        for(PlannedPiece i : pieces)
        {
            if(i.collidesWith(piece))
            {
                return true;
            }
        }

        return false;
    }

    public boolean collidesWith(PlannedPiece piece, PlannedPiece ignore)
    {
        for(PlannedPiece i : pieces)
        {
            if(i.equals(ignore))
            {
                continue;
            }

            if(i.collidesWith(piece))
            {
                return true;
            }
        }

        return false;
    }

    public boolean contains(IrisPosition p)
    {
        for(PlannedPiece i : pieces)
        {
            if(i.contains(p))
            {
                return true;
            }
        }

        return false;
    }
}
