package art.arcane.iris.generation.terrain;

import org.junit.Test;

import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class IrisDimensionTypeKeyTest {
    @Test
    public void dimensionTypeKeyUsesSanitizedSemanticPackKey() {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("Overworld");

        assertEquals("overworld", dimension.getDimensionTypeKey());
    }

    @Test
    public void dimensionTypeKeySanitizesUnsafePackCharacters() {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("Worlds/My Pack");

        assertEquals("worlds_my_pack", dimension.getDimensionTypeKey());
    }

    @Test
    public void customBiomeKeyPreservesFlatDimensionNamespace() {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("Overworld");

        assertEquals("overworld:aurora", dimension.getCustomBiomeKey("Aurora"));
    }

    @Test
    public void customBiomeKeyMapsRecursiveDimensionPath() {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("Layers/Sky");

        assertEquals("layers:sky/aurora", dimension.getCustomBiomeKey("Aurora"));
    }

    @Test
    public void customBiomeKeysPreserveSanitizerBoundaryBehavior() {
        String[] values = {null, "", " ", "\t\n", "a", "0", "_", "-", ".", "..", "...", "....",
                "/", "//", "/a/", "a//b", "a/./b", "a/../b", "a..b", "a...b", "a\\b", " a/b ",
                "A_Z-9.ext/path", "İ", "ΣΟΣ", "K", "ß", "é", "a\u2003b", "\u2003", "\u00a0",
                "\u0000a\u001f", "a\u0000b", "\ud83d\ude00", "\ud800", "\udc00"};
        for (String dimension : values) {
            for (String biome : values) {
                assertEquals(legacyCustomBiomeKey(dimension, biome), IrisDimension.customBiomeKey(dimension, biome));
            }
        }
    }

    @Test
    public void customBiomeKeysMatchPriorSanitizerForMixedUnicodeInputs() {
        Random random = new Random(7331L);
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789_-.//\\ ABCXYZ\t\nİΣKé\u2003\u0000\ud83d\ude00";
        for (int sample = 0; sample < 3000; sample++) {
            String dimension = randomValue(random, alphabet);
            String biome = randomValue(random, alphabet);
            assertEquals(legacyCustomBiomeKey(dimension, biome), IrisDimension.customBiomeKey(dimension, biome));
        }
    }

    private static String randomValue(Random random, String alphabet) {
        int length = random.nextInt(65);
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(random.nextInt(8) == 0 ? (char) random.nextInt(65536)
                    : alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return value.toString();
    }

    private static String legacyCustomBiomeKey(String dimension, String biome) {
        String dimensionPath = legacyRegistryPath(dimension, "dimension");
        int separator = dimensionPath.indexOf('/');
        String namespace = separator < 0 ? dimensionPath : dimensionPath.substring(0, separator);
        namespace = namespace.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        if (namespace.isBlank()) {
            namespace = "dimension";
        }
        return namespace + ":" + (separator < 0 ? "" : dimensionPath.substring(separator + 1) + "/")
                + legacyRegistryPath(biome, "biome");
    }

    private static String legacyRegistryPath(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String sanitized = value.trim().toLowerCase(Locale.ROOT).replace("\\", "/");
        sanitized = sanitized.replaceAll("[^a-z0-9/._-]", "_");
        sanitized = sanitized.replaceAll("/+", "/");
        sanitized = sanitized.replaceAll("^/+", "");
        sanitized = sanitized.replaceAll("/+$", "");
        while (sanitized.contains("..")) {
            sanitized = sanitized.replace("..", "_");
        }
        return sanitized.isBlank() ? fallback : sanitized;
    }
}
