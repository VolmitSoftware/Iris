package com.volmit.iris.scaffold.jigsaw;

import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.IrisJigsawStructure;
import com.volmit.iris.object.IrisPosition;
import com.volmit.iris.util.KList;
import lombok.Data;

@Data
public class PlannedStructure {
    private KList<PlannedPiece> pieces;
    private IrisJigsawStructure structure;
    private IrisPosition position;
    private IrisDataManager data;

    public PlannedStructure(IrisJigsawStructure structure, IrisPosition position)
    {
        this.structure = structure;
        this.position = position;
        this.data = structure.getLoader();
        
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
