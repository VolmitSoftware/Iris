package art.arcane.iris.integration;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;

import java.util.Optional;

public record IrisPapiWorldView(
        IrisPapiPosition position,
        long builtAtMs,
        String available,
        String biome,
        String biomeKey,
        String region,
        String regionKey,
        String dimension) {
    public static IrisPapiWorldView absent(IrisPapiPosition position, long builtAtMs) {
        return new IrisPapiWorldView(
                position,
                builtAtMs,
                PlaceholderValues.FALSE,
                PlaceholderValues.UNAVAILABLE,
                PlaceholderValues.UNAVAILABLE,
                PlaceholderValues.UNAVAILABLE,
                PlaceholderValues.UNAVAILABLE,
                PlaceholderValues.UNAVAILABLE);
    }

    public static IrisPapiWorldView present(
            IrisPapiPosition position,
            long builtAtMs,
            Optional<String> biome,
            Optional<String> biomeKey,
            Optional<String> region,
            Optional<String> regionKey,
            Optional<String> dimension) {
        return new IrisPapiWorldView(
                position,
                builtAtMs,
                PlaceholderValues.TRUE,
                text(biome),
                text(biomeKey),
                text(region),
                text(regionKey),
                text(dimension));
    }

    static String text(Optional<String> value) {
        return value == null || value.isEmpty() ? PlaceholderValues.UNAVAILABLE : PlaceholderValues.text(value.get());
    }
}
