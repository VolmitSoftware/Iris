package com.volmit.iris.scaffold.jigsaw;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.*;
import com.volmit.iris.util.AxisAlignedBB;
import com.volmit.iris.util.KList;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BlockVector;

@Data
public class PlannedPiece {
    private IrisPosition position;
    private IrisObject object;
    private IrisJigsawPiece piece;
    private IrisObjectRotation rotation;
    private IrisDataManager data;
    private KList<IrisPosition> connected;
    private int rotationKey;

    public PlannedPiece(IrisPosition position, IrisJigsawPiece piece)
    {
        this(position, piece, 0,0,0);
    }

    public PlannedPiece(IrisPosition position, IrisJigsawPiece piece, int rx, int ry, int rz)
    {
        this.position = position;
        rotationKey = (rz * 100) + (rx * 10) + ry;
        this.data = piece.getLoader();
        this.rotation = IrisObjectRotation.of(rx*90D, ry*90D, rz*90D);
        this.object = rotation.rotateCopy(data.getObjectLoader().load(piece.getObject()));
        this.piece = rotation.rotateCopy(piece);
        this.piece.setLoadKey(piece.getLoadKey());
        this.object.setLoadKey(object.getLoadKey());
        this.connected = new KList<>();
    }

    public String toString()
    {
        return piece.getLoadKey() + "@(" + position.getX() + "," + position.getY() + "," + position.getZ() + ")[rot:" + rotationKey + "]";
    }

    public AxisAlignedBB getBox()
    {
        BlockVector v = getObject().getCenter();
        return object.getAABB().shifted(position.add(new IrisPosition(object.getCenter())));
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
        return this.position.add(position).add(new IrisPosition(object.getCenter()));
    }

    public boolean isFull() {
        return connected.size() >= piece.getConnectors().size();
    }

    public void place(World world) {
        getObject().placeCenterY(new Location(world, position.getX(), position.getY(), position.getZ()));
    }
}
