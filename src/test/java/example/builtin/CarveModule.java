package example.builtin;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;

/** A built-in module outside the API's package, like the engine modules inside Iris. Pack jars can
 * require its stages only when the host exposes this package.
 */
public final class CarveModule implements IrisModule {
    public static final PlacementStage<Void> CLEAR = Stage.placement("iris:carve/clear");

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:carve", "Carve", "test");
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(CLEAR).run(context -> {});
    }
}
