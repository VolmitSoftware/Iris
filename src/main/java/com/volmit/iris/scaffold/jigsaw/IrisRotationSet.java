package com.volmit.iris.scaffold.jigsaw;

import com.volmit.iris.object.IrisAxisRotationClamp;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.object.IrisObjectRotation;
import com.volmit.iris.util.KMap;
import lombok.Data;

@Data
public class IrisRotationSet
{
    private KMap<IrisObjectRotation, IrisObject> cache;
    private IrisObject base;

    public IrisRotationSet(IrisObject base)
    {
        this.base = base;
        this.cache = new KMap<>();
    }

    public IrisObject getObject(double x, double y, double z)
    {
        IrisObjectRotation rt = new IrisObjectRotation();
        rt.setEnabled(true);
        IrisAxisRotationClamp rtx = new IrisAxisRotationClamp();
        rtx.setEnabled(x != 0);
        rtx.setMax(x);
        rt.setXAxis(rtx);
        IrisAxisRotationClamp rty = new IrisAxisRotationClamp();
        rty.setEnabled(y != 0);
        rty.setMax(y);
        rt.setXAxis(rty);
        IrisAxisRotationClamp rtz = new IrisAxisRotationClamp();
        rtz.setEnabled(z != 0);
        rtz.setMax(z);
        rt.setXAxis(rtz);

        if(cache.containsKey(rt))
        {
            return cache.get(rt);
        }

        IrisObject rotated = base.rotateCopy(rt);
        cache.put(rt, rotated);
        return rotated;
    }
}
