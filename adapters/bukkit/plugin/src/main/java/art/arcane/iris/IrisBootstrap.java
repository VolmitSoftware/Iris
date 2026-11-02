package art.arcane.iris;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;

@SuppressWarnings("UnstableApiUsage")
public final class IrisBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        IrisPluginRuntime.bootstrap(context);
    }
}
