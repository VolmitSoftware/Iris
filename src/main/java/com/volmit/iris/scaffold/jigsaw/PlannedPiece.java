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
    private boolean dead = false;
    private int rotationKey;
    private AxisAlignedBB box;
    private PlannedStructure structure;

    public PlannedPiece(PlannedStructure structure, IrisPosition position, IrisJigsawPiece piece)
    {
        this(structure, position, piece, 0,0,0);
    }

    public PlannedPiece(PlannedStructure structure, IrisPosition position, IrisJigsawPiece piece, int rx, int ry, int rz)
    {
        this.structure = structure;
        this.position = position;
        rotationKey = (rz * 100) + (rx * 10) + ry;
        this.data = piece.getLoader();
        this.rotation = IrisObjectRotation.of(rx*90D, ry*90D, rz*90D);
        this.object = structure.rotated(piece, rotation);
        this.piece = rotation.rotateCopy(piece);
        this.piece.setLoadKey(piece.getLoadKey());
        this.object.setLoadKey(piece.getObject());
        this.connected = new KList<>();
    }

    public void setPosition(IrisPosition p)
    {
        this.position = p;
        box = null;
    }

    public String toString()
    {
        return piece.getLoadKey() + "@(" + position.getX() + "," + position.getY() + "," + position.getZ() + ")[rot:" + rotationKey + "]";
    }

    public AxisAlignedBB getBox()
    {
        if(box != null)
        {
            return box;
        }

        BlockVector v = getObject().getCenter();
        box = object.getAABB().shifted(position.add(new IrisPosition(object.getCenter())));
        return box;
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
        return connected.size() >= piece.getConnectors().size() || isDead();
    }

    public void place(World world) {
        getObject().placeCenterY(new Location(world, position.getX(), position.getY(), position.getZ()));
    }
}
