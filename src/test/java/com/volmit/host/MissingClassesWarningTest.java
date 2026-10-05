package com.volmit.host;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MissingClassesWarningTest {
    private static final String HEADER = "Pack overworld: module jars use classes that neither the pack, Iris nor the JDK"
            + " has, usually optional dependencies of a bundled library. The jars load, but code that needs these"
            + " classes throws NoClassDefFoundError when it runs:";

    @Test
    void listsTheMissingClassesOfEveryJarOnOneLine() {
        var jars = List.of(
                new MissingClasses("modules/moss.jar", List.of("org.slf4j.Logger", "org.slf4j.LoggerFactory")),
                new MissingClasses("modules/snow.jar", List.of("com.google.gson.Gson")));

        assertEquals(HEADER
                        + "\n  modules/moss.jar: org.slf4j.Logger, org.slf4j.LoggerFactory"
                        + "\n  modules/snow.jar: com.google.gson.Gson",
                MissingClassesWarning.describe("overworld", jars));
    }

    @Test
    void capsLongListsPerJar() {
        var classNames = IntStream.rangeClosed(1, 214).mapToObj(index -> "lib.Missing%03d".formatted(index)).toList();
        var jars = List.of(new MissingClasses("modules/big.jar", classNames));

        assertEquals(HEADER
                        + "\n  modules/big.jar: lib.Missing001, lib.Missing002, lib.Missing003, lib.Missing004, lib.Missing005 and 209 more",
                MissingClassesWarning.describe("overworld", jars));
    }

    @Test
    void listAtTheCapHasNoRemainder() {
        var classNames = IntStream.rangeClosed(1, MissingClassesWarning.MAX_LISTED_CLASSES_PER_JAR)
                .mapToObj(index -> "lib.Missing" + index).toList();
        var jars = List.of(new MissingClasses("modules/big.jar", classNames));

        assertEquals(HEADER + "\n  modules/big.jar: lib.Missing1, lib.Missing2, lib.Missing3, lib.Missing4, lib.Missing5",
                MissingClassesWarning.describe("overworld", jars));
    }
}
