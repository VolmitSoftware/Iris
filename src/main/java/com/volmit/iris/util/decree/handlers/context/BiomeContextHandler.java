package com.volmit.iris.util.decree.handlers.context;

import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.util.decree.DecreeContextHandler;
import com.volmit.iris.util.plugin.VolmitSender;

public class BiomeContextHandler implements DecreeContextHandler<IrisBiome> {
    public Class<IrisBiome> getType(){return IrisBiome.class;}

    public IrisBiome handle(VolmitSender sender)
    {
        if(sender.isPlayer()
                && IrisToolbelt.isIrisWorld(sender.player().getWorld())
                && IrisToolbelt.access(sender.player().getWorld()).getEngine() != null)
        {
            return IrisToolbelt.access(sender.player().getWorld()).getEngine().getBiome(sender.player().getLocation());
        }

        return null;
    }
}
