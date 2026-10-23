package art.arcane.iris.engine.object.annotations.functions;

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.host.LoadedPack;
import com.volmit.stage.Stage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/** Studio offers the flags of Iris's own mantle stages for a dimension's disabledComponents, then the pack's mantle stage ids. */
public class ComponentFlagFunctionTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void offersTheFlagsOfIrisMantleStagesInTheirOrderThenThePacksMantleStageIds() throws IOException {
        IrisModule moss = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:moss", "Moss", "1"); }
            public void setup(ModuleSetup setup) {
                setup.stages().define(Stage.mantle("test:moss/veins")).requires(IrisStages.Structure.PLACE).reach(8).run(context -> { });
            }
        };
        IrisData data = mock(IrisData.class);

        try (LoadedPack ignored = BuiltInPack.serve(data, folder, List.of(moss))) {
            assertEquals(List.of("CARVED", "OBJECT", "FLOATING_OBJECT", "JIGSAW", "test:moss/veins"), new ComponentFlagFunction().apply(data));
        }
    }
}
