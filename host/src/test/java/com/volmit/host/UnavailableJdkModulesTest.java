package com.volmit.host;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UnavailableJdkModulesTest {
    @Test
    void namesJdkModulesOutsideThePlatformLoader() {
        assertEquals(Optional.of("jdk.compiler"), UnavailableJdkModules.moduleOf("com.sun.source.tree.Tree"));
        assertEquals(Optional.of("jdk.jshell"), UnavailableJdkModules.moduleOf("jdk.jshell.JShell"));
    }

    @Test
    void modulesOfTheBootAndPlatformLoadersAreAvailable() {
        assertEquals(Optional.empty(), UnavailableJdkModules.moduleOf("java.lang.String"));
        assertEquals(Optional.empty(), UnavailableJdkModules.moduleOf("java.sql.Connection"));
        assertEquals(Optional.empty(), UnavailableJdkModules.moduleOf("javax.tools.ToolProvider"));
    }

    @Test
    void classesOutsideTheJdkAreNotJdkModules() {
        assertEquals(Optional.empty(), UnavailableJdkModules.moduleOf("org.slf4j.Logger"));
        assertEquals(Optional.empty(), UnavailableJdkModules.moduleOf("TopLevel"));
    }
}
