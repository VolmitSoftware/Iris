package art.arcane.iris.integration;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;

import java.util.logging.Logger;

public final class IrisPapiInstaller {
    private IrisPapiInstaller() {
    }

    public static boolean install(PlaceholderRegistration registration, IrisPapiState state, Logger logger) {
        return registration.register(() -> new IrisPapiExpansion(state, logger));
    }
}
