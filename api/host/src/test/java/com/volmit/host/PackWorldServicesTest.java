package com.volmit.host;

import com.volmit.IrisModule;
import com.volmit.FeatureRegistry;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.service.Providers;
import com.volmit.service.Service;
import com.volmit.support.TestWorld;
import com.volmit.world.PackWorld;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A module asks another module's service through the world: {@code PackWorld.services}, which the validation context and
 * the tool pack answer the same way.
 */
class PackWorldServicesTest {
    @Service(Providers.ALL)
    interface Greeting { String greet(); }
    @Service(Providers.ONE)
    interface Clock { long now(); }
    interface NotAService { void run(); }

    private static void registerAs(ModuleRegistry registry, String moduleId, Consumer<FeatureRegistry> registrations) {
        ModuleSetups.run(DiscoveredModule.builtIn(new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of(moduleId, "Test module " + moduleId, "1"); }
            public void setup(ModuleSetup setup) { registrations.accept(setup.registry()); }
        }), new StageGraphBuilder(), registry);
    }

    private static FeatureCatalog catalogOf(Greeting zeta, Greeting alphaFirst, Greeting alphaSecond) {
        var registry = new ModuleRegistry();
        registerAs(registry, "test:zeta", services -> services.registerService(Greeting.class, zeta));
        registerAs(registry, "test:alpha", services -> {
            services.registerService(Greeting.class, alphaFirst);
            services.registerService(Greeting.class, alphaSecond);
        });
        return registry.freeze();
    }

    @Test
    void aWorldAnswersWithTheProvidersOfItsPacksModulesInModuleIdOrder() {
        Greeting zeta = () -> "zeta";
        Greeting alphaFirst = () -> "alpha 1";
        Greeting alphaSecond = () -> "alpha 2";
        try (var world = new TestWorld(catalogOf(zeta, alphaFirst, alphaSecond))) {
            assertEquals(List.of(alphaFirst, alphaSecond, zeta), world.services(Greeting.class));
        }
    }

    @Test
    void aServiceNobodyProvidesHasNoProviders() {
        try (var world = new TestWorld(catalogOf(() -> "zeta", () -> "alpha 1", () -> "alpha 2"))) {
            assertEquals(List.of(), world.services(Clock.class));
        }
    }

    @Test
    void anInterfaceThatIsNotAServiceIsRefused() {
        try (var world = new TestWorld(catalogOf(() -> "zeta", () -> "alpha 1", () -> "alpha 2"))) {
            assertThrows(IllegalArgumentException.class, () -> world.services(NotAService.class));
        }
    }

    @Test
    void theViewAModuleGetsOfAWorldAnswersLikeTheWorld() {
        Greeting zeta = () -> "zeta";
        Greeting alphaFirst = () -> "alpha 1";
        Greeting alphaSecond = () -> "alpha 2";
        var catalog = catalogOf(zeta, alphaFirst, alphaSecond);
        try (var world = new TestWorld(catalog)) {
            PackWorld view = catalog.registrations(Greeting.class).getFirst().scope(world);

            assertNotSame(world, view);
            assertEquals(world.services(Greeting.class), view.services(Greeting.class));
            assertEquals(List.of(alphaFirst, alphaSecond, zeta), view.services(Greeting.class));
        }
    }
}
