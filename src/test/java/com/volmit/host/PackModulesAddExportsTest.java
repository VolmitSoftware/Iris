package com.volmit.host;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static com.volmit.support.TestModuleJar.module;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only in the {@code addExportsTest} task, whose JVM is started with
 * {@code --add-exports java.base/jdk.internal.misc=ALL-UNNAMED}, like a server that grants modules
 * that package.
 */
@Tag(PackModulesAddExportsTest.ADD_EXPORTS_TAG)
class PackModulesAddExportsTest {
    static final String ADD_EXPORTS_TAG = "jdk-add-exports";

    @TempDir
    Path pack;

    @Test
    void packageExportedToAllUnnamedModulesOnTheCommandLineIsUsable() {
        module("pack.user.User", "user:core", "static Object unsafe() { return jdk.internal.misc.Unsafe.class; }", "")
                .compileWithAccessTo("java.base", "jdk.internal.misc")
                .writeInto(pack, "user.jar");

        try (var packModules = PackModules.load(pack)) {
            assertEquals(List.of(), packModules.missingClasses());
        }
    }

    @Test
    void onlyTheExportedPackageBecomesUsable() {
        assertEquals(Optional.empty(), NonExportedJdkPackages.packageOf("jdk.internal.misc.Unsafe"),
                "this JVM must run with --add-exports java.base/jdk.internal.misc=ALL-UNNAMED (gradle addExportsTest)");
        assertTrue(NonExportedJdkPackages.packageOf("sun.nio.ch.Net").isPresent());
    }
}
