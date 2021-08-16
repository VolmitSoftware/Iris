package com.volmit.iris.util.decree.context;

import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.util.decree.DecreeContextHandler;
import com.volmit.iris.util.plugin.VolmitSender;

public class DimensionContextHandler implements DecreeContextHandler<IrisDimension> {
    public Class<IrisDimension> getType(){return IrisDimension.class;}

    public IrisDimension handle(VolmitSender sender)
    {
        if(sender.isPlayer()
                && IrisToolbelt.isIrisWorld(sender.player().getWorld())
                && IrisToolbelt.access(sender.player().getWorld()).getEngine() != null)
        {
            return IrisToolbelt.access(sender.player().getWorld()).getEngine().getDimension();
        }

        return null;
    }
}
