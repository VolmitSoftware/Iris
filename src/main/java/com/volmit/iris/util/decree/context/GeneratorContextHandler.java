package com.volmit.iris.util.decree.context;

import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.noise.IrisGenerator;
import com.volmit.iris.util.decree.DecreeContextHandler;
import com.volmit.iris.util.plugin.VolmitSender;

public class GeneratorContextHandler implements DecreeContextHandler<IrisGenerator> {
    @Override
    public Class<IrisGenerator> getType() {
        return IrisGenerator.class;
    }

    @Override
    public IrisGenerator handle(VolmitSender sender) {
        if(sender.isPlayer()
                && IrisToolbelt.isIrisWorld(sender.player().getWorld())
                && IrisToolbelt.access(sender.player().getWorld()).getEngine() != null)
        {
            Engine engine = IrisToolbelt.access(sender.player().getWorld()).getEngine();
            return engine.getData().getGeneratorLoader().load(engine.getBiome(sender.player().getLocation()).getGenerators().getRandom().getGenerator());
        }

        return null;
    }
}
