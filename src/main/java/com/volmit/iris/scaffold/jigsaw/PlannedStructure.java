package com.volmit.iris.scaffold.jigsaw;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisJigsawPiece;
import com.volmit.iris.object.IrisJigsawPieceConnector;
import com.volmit.iris.object.IrisJigsawStructure;
import com.volmit.iris.object.IrisPosition;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;
import lombok.Data;

@Data
public class PlannedStructure {
    private KList<PlannedPiece> pieces;
    private IrisJigsawStructure structure;
    private IrisPosition position;
    private IrisDataManager data;
    private RNG rng;

    public PlannedStructure(IrisJigsawStructure structure, IrisPosition position, RNG rng)
    {
        this.structure = structure;
        this.position = position;
        this.rng = rng;
        this.data = structure.getLoader();

        generateStartPiece();

        for(int i = 0; i < structure.getMaxDepth(); i++)
        {
            generateOutwards(i);
        }

        generateTerminators();
    }

    private void generateOutwards(int layer) {
        for(PlannedPiece i : getPiecesWithAvailableConnectors())
        {
            generatePieceOutwards(i);
        }
    }

    private boolean generatePieceOutwards(PlannedPiece piece) {
        for(IrisJigsawPieceConnector i : piece.getAvailableConnectors())
        {
            if(generateConnectorOutwards(piece, i))
            {
                return true;
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
        for(int i = 0; i < 4; i++)
        {
            PlannedPiece test = new PlannedPiece(piece.getPosition(), idea, 0, i, 0);

            for(IrisJigsawPieceConnector j : test.getPiece().getConnectors())
            {
                if(generatePositionedPiece(piece, pieceConnector, test, j))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean generatePositionedPiece(PlannedPiece piece,
                                            IrisJigsawPieceConnector pieceConnector,
                                            PlannedPiece test,
                                            IrisJigsawPieceConnector testConnector) {
        IrisPosition rParent = pieceConnector.getPosition();
        IrisPosition rTest = testConnector.getPosition();
        test.setPosition(piece.getPosition().add(rParent).sub(rTest));

        if(collidesWith(test, piece))
        {
            return false;
        }

        if(!pieceConnector.isInnerConnector() && piece.collidesWith(test))
        {
            return false;
        }

        pieces.add(test);

        return true;
    }

    private KList<IrisJigsawPiece> getShuffledPiecesFor(IrisJigsawPieceConnector c)
    {
        KList<IrisJigsawPiece> p = new KList<>();

        for(String i : c.getPools())
        {
            for(String j : getData().getJigsawPoolLoader().load(i).getPieces())
            {
                p.addIfMissing(getData().getJigsawPieceLoader().load(j));
            }
        }

        return p.shuffleCopy(rng);
    }

    private void generateStartPiece() {
        pieces.add(new PlannedPiece(position, getData().getJigsawPieceLoader().load(rng.pick(getStructure().getPieces()))));
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
