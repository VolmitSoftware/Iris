package com.volmit.host;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.stage.Stage;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveredModuleTest {
    /** Answers with its first identity once, then with another one. */
    private static final class ShiftingIdentity implements IrisModule {
        private int infoCalls;

        public ModuleInfo info() {
            infoCalls++;
            return infoCalls == 1 ? ModuleInfo.of("first:core", "First", "1") : ModuleInfo.of("second:core", "Second", "2");
        }

        public void setup(ModuleSetup setup) {
            setup.stages().define(Stage.placement("first:core/mark")).run(context -> {});
        }
    }

    @Test
    void builtInReadsInfoOnceAndLoadingUsesOnlyThatValue() {
        var module = new ShiftingIdentity();
        var discovered = DiscoveredModule.builtIn(module);

        var loaded = LoadedModules.load(List.of(discovered), Map.of(), Set.of(), Set.of());

        assertEquals("first:core", discovered.info().id().toString());
        assertEquals(List.of("first:core/mark"),
                loaded.placement().stages().stream().map(stage -> stage.stage().id().toString()).toList());
        assertEquals(1, module.infoCalls);
    }

    @Test
    void builtInWhoseInfoIsNullFailsNamingTheClass() {
        var module = new IrisModule() {
            public ModuleInfo info() { return null; }
            public void setup(ModuleSetup setup) {}
        };

        var error = assertThrows(NullPointerException.class, () -> DiscoveredModule.builtIn(module));

        assertTrue(error.getMessage().contains(module.getClass().getName()), error.getMessage());
    }
}
