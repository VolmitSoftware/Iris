package com.volmit.host;

import com.volmit.host.NonExportedJdkPackages.JdkPackage;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NonExportedJdkPackagesTest {
    @Test
    void namesPackagesTheirModuleDoesNotExport() {
        assertEquals(Optional.of(new JdkPackage("jdk.internal.misc", "java.base")),
                NonExportedJdkPackages.packageOf("jdk.internal.misc.Unsafe"));
        assertEquals(Optional.of(new JdkPackage("sun.nio.ch", "java.base")), NonExportedJdkPackages.packageOf("sun.nio.ch.Net"));
    }

    @Test
    void packagesExportedOnlyToNamedModulesAreNotExported() {
        assertEquals(Optional.of(new JdkPackage("jdk.internal.access", "java.base")),
                NonExportedJdkPackages.packageOf("jdk.internal.access.SharedSecrets"));
    }

    @Test
    void exportedPackagesAreUsable() {
        assertEquals(Optional.empty(), NonExportedJdkPackages.packageOf("sun.misc.Unsafe"));
        assertEquals(Optional.empty(), NonExportedJdkPackages.packageOf("java.lang.String"));
        assertEquals(Optional.empty(), NonExportedJdkPackages.packageOf("java.sql.Connection"));
    }

    @Test
    void modulesOutsideThePlatformLoaderAndNonJdkClassesAreNotListed() {
        assertEquals(Optional.empty(), NonExportedJdkPackages.packageOf("com.sun.tools.javac.code.Type"));
        assertEquals(Optional.empty(), NonExportedJdkPackages.packageOf("org.slf4j.Logger"));
        assertEquals(Optional.empty(), NonExportedJdkPackages.packageOf("TopLevel"));
    }
}
