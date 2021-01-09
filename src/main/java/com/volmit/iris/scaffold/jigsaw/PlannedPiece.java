package com.volmit.iris.scaffold.jigsaw;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.*;
import com.volmit.iris.util.AxisAlignedBB;
import com.volmit.iris.util.KList;
import lombok.Data;

@Data
public class PlannedPiece {
    private IrisPosition position;
    private IrisObject object;
    private IrisJigsawPiece piece;
    private IrisObjectRotation rotation;
    private IrisDataManager data;
    private AxisAlignedBB box;
    private KList<IrisPosition> connected;

    public PlannedPiece(IrisPosition position, IrisJigsawPiece piece)
    {
        this(position, piece, 0,0,0);
    }

    public PlannedPiece(IrisPosition position, IrisJigsawPiece piece, int rx, int ry, int rz)
    {
        this.position = position;
        this.data = piece.getLoader();
        this.rotation = IrisObjectRotation.of(rx*90, ry*90, rz*90);
        this.object = rotation.rotateCopy(data.getObjectLoader().load(piece.getObject()));
        this.piece = rotation.rotateCopy(piece);
        this.box = object.getAABB();
        this.connected = new KList<>();
    }

    public boolean contains(IrisPosition p)
    {
        return getBox().contains(p);
    }

    public boolean collidesWith(PlannedPiece p)
    {
        return getBox().intersects(p.getBox());
    }

    public KList<IrisJigsawPieceConnector> getAvailableConnectors()
    {
        if(connected.isEmpty())
        {
            return piece.getConnectors().copy();
        }

        if(connected.size() == piece.getConnectors().size())
        {
            return new KList<>();
        }

        KList<IrisJigsawPieceConnector> c = new KList<>();

        for(IrisJigsawPieceConnector i : piece.getConnectors())
        {
            if(!connected.contains(i.getPosition()))
            {
                c.add(i);
            }
        }

        return c;
    }

    public boolean connect(IrisJigsawPieceConnector c)
    {
        if(piece.getConnectors().contains(c))
        {
            return connect(c.getPosition());
        }

        return false;
    }

    private boolean connect(IrisPosition p)
    {
        return connected.addIfMissing(p);
    }

    public IrisPosition getWorldPosition(IrisJigsawPieceConnector c)
    {
        return getWorldPosition(c.getPosition());
    }

    public IrisPosition getWorldPosition(IrisPosition position)
    {
        return this.position.add(position);
    }

    public boolean isFull() {
        return connected.size() >= piece.getConnectors().size();
    }
}
