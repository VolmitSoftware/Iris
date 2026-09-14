package art.arcane.iris.integration;

import art.arcane.iris.api.terrain.IrisBiomeInfo;
import art.arcane.iris.api.terrain.IrisCustomBiomeInfo;
import art.arcane.iris.api.terrain.IrisWorldInfo;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public record IrisPapiWorldView(
        IrisPapiPosition position,
        long builtAtMs,
        String available,
        String biome,
        String biomeKey,
        String region,
        String regionKey,
        String dimension,
        BiomeDetails details,
        Heights heights) {
    public static IrisPapiWorldView absent(IrisPapiPosition position, long builtAtMs) {
        return new IrisPapiWorldView(
                position,
                builtAtMs,
                PlaceholderValues.FALSE,
                PlaceholderValues.UNAVAILABLE,
                PlaceholderValues.UNAVAILABLE,
                PlaceholderValues.UNAVAILABLE,
                PlaceholderValues.UNAVAILABLE,
                PlaceholderValues.UNAVAILABLE,
                BiomeDetails.absent(),
                Heights.absent());
    }

    public static IrisPapiWorldView present(
            IrisPapiPosition position,
            long builtAtMs,
            Optional<IrisBiomeInfo> biome,
            Optional<IrisWorldInfo> world) {
        return new IrisPapiWorldView(
                position,
                builtAtMs,
                PlaceholderValues.TRUE,
                text(biome.map(IrisBiomeInfo::name)),
                text(biome.map(IrisBiomeInfo::key)),
                text(biome.map(IrisBiomeInfo::regionName)),
                text(biome.map(IrisBiomeInfo::regionKey)),
                text(world.map(IrisWorldInfo::dimensionKey)),
                biome.map(BiomeDetails::of).orElseGet(BiomeDetails::absent),
                world.map(Heights::of).orElseGet(Heights::absent));
    }

    static String text(Optional<String> value) {
        return value == null || value.isEmpty() ? PlaceholderValues.UNAVAILABLE : PlaceholderValues.text(value.get());
    }

    public record BiomeDetails(
            String custom,
            String customId,
            String customIds,
            String customKey,
            String customKeys,
            String customCount,
            String derivative,
            String vanillaDerivative,
            String type) {
        static BiomeDetails absent() {
            String unavailable = PlaceholderValues.UNAVAILABLE;
            return new BiomeDetails(unavailable, unavailable, unavailable, unavailable, unavailable,
                    unavailable, unavailable, unavailable, unavailable);
        }

        static BiomeDetails of(IrisBiomeInfo biome) {
            List<IrisCustomBiomeInfo> derivatives = biome.customDerivatives();
            StringJoiner ids = new StringJoiner(", ");
            StringJoiner keys = new StringJoiner(", ");
            boolean keysComplete = true;
            for (IrisCustomBiomeInfo derivative : derivatives) {
                ids.add(derivative.id());
                keys.add(derivative.registryKey());
                keysComplete &= !derivative.registryKey().isBlank();
            }
            IrisCustomBiomeInfo single = derivatives.size() == 1 ? derivatives.getFirst() : null;
            return new BiomeDetails(
                    PlaceholderValues.bool(!derivatives.isEmpty()),
                    single == null ? PlaceholderValues.UNAVAILABLE : PlaceholderValues.text(single.id()),
                    PlaceholderValues.text(ids.toString()),
                    single == null ? PlaceholderValues.UNAVAILABLE : PlaceholderValues.text(single.registryKey()),
                    keysComplete ? PlaceholderValues.text(keys.toString()) : PlaceholderValues.UNAVAILABLE,
                    Integer.toString(derivatives.size()),
                    PlaceholderValues.text(biome.derivativeKey()),
                    PlaceholderValues.text(biome.vanillaDerivativeKey()),
                    PlaceholderValues.text(biome.type()));
        }
    }

    public record Heights(String minimum, String maximum, String height, String fluid) {
        static Heights absent() {
            String unavailable = PlaceholderValues.UNAVAILABLE;
            return new Heights(unavailable, unavailable, unavailable, unavailable);
        }

        static Heights of(IrisWorldInfo world) {
            return new Heights(Integer.toString(world.minHeight()), Integer.toString(world.maxHeight()),
                    Integer.toString(world.height()), Integer.toString(world.fluidHeight()));
        }
    }
}
