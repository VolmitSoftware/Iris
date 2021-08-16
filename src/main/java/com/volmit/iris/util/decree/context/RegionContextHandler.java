package com.volmit.iris.util.decree.context;

import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.util.decree.DecreeContextHandler;
import com.volmit.iris.util.plugin.VolmitSender;

public class RegionContextHandler implements DecreeContextHandler<IrisRegion> {
    public Class<IrisRegion> getType(){return IrisRegion.class;}

    public IrisRegion handle(VolmitSender sender)
    {
        if(sender.isPlayer()
                && IrisToolbelt.isIrisWorld(sender.player().getWorld())
                && IrisToolbelt.access(sender.player().getWorld()).getEngine() != null)
        {
            return IrisToolbelt.access(sender.player().getWorld()).getEngine().getRegion(sender.player().getLocation());
        }

        return null;
    }
}
