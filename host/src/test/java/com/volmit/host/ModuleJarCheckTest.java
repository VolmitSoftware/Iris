package com.volmit.host;

import com.volmit.support.TestModuleJar;
import example.builtin.CarveModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static com.volmit.support.TestModuleJar.module;
import static org.junit.jupiter.api.Assertions.*;

class ModuleJarCheckTest {
    private static final String NO_MEMBERS = "";
    private static final String NO_STAGES = "";
    private static final String USES_JUNIT = "static Object used() { return org.junit.jupiter.api.Test.class; }";
    private static final List<String> NO_EXPOSED_PACKAGES = List.of();

    @TempDir
    Path build;

    private static String failure(Executable check) {
        return assertThrows(ModuleLoadException.class, check).getMessage();
    }

    @Test
    void jarOfSeveralModulesPassesAndReturnsThemInListedOrder() {
        var jar = module("pack.First", "first:core", NO_MEMBERS, NO_STAGES)
                .alsoModule("pack.Second", "second:core", NO_MEMBERS, NO_STAGES)
                .writeInto(build, "two.jar");

        assertEquals(List.of("pack.First", "pack.Second"), ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES));
    }

    @Test
    void jarThatUsesAnExposedHostPackagePasses() {
        var jar = module("pack.User", "user:core", NO_MEMBERS,
                "graph.define(Stage.placement(\"user:core/carved\")).requires(example.builtin.CarveModule.CLEAR).run(context -> {});")
                .compileAgainstHostOf(CarveModule.class)
                .writeInto(build, "user.jar");

        assertEquals(List.of("pack.User"), ModuleJarCheck.check(jar, List.of(CarveModule.class.getPackageName())));
    }

    @Test
    void jarThatUsesAHiddenHostClassFailsNamingTheClass() {
        var jar = module("pack.User", "user:core", USES_JUNIT, NO_STAGES)
                .compileAgainstHostOf(Test.class)
                .writeInto(build, "user.jar");

        var message = failure(() -> ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES));

        assertTrue(message.contains("hidden from modules"), message);
        assertTrue(message.contains("pack.User -> org.junit.jupiter.api.Test"), message);
    }

    @Test
    void jarThatUsesAHostClassItsHostDoesNotExposeFails() {
        var jar = module("pack.User", "user:core", NO_MEMBERS,
                "graph.define(Stage.placement(\"user:core/carved\")).requires(example.builtin.CarveModule.CLEAR).run(context -> {});")
                .compileAgainstHostOf(CarveModule.class)
                .writeInto(build, "user.jar");

        var message = failure(() -> ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES));

        assertTrue(message.contains("pack.User -> example.builtin.CarveModule"), message);
    }

    @Test
    void moduleClassOutsideTheJarFailsNamingTheJarAndTheClass() {
        var jar = module("pack.User", "user:core", NO_MEMBERS, NO_STAGES)
                .entryPoint("pack.Elsewhere")
                .writeInto(build, "user.jar");

        var message = failure(() -> ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES));

        assertTrue(message.contains(jar.toString()), message);
        assertTrue(message.contains("lists pack.Elsewhere"), message);
    }

    @Test
    void jarThatListsNoModuleFails() {
        var jar = new TestModuleJar().source("pack.Plain", "package pack; public final class Plain {}").writeInto(build, "plain.jar");

        assertTrue(failure(() -> ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES)).contains("has no entry point"));
    }

    @Test
    void jarThatUsesAnExposedClassTheHostJarLeavesOutFailsNamingTheClass() {
        var jar = module("pack.User", "user:core", NO_MEMBERS,
                "graph.define(Stage.placement(\"user:core/carved\")).requires(example.builtin.CarveModule.CLEAR).run(context -> {});")
                .compileAgainstHostOf(CarveModule.class)
                .writeInto(build, "user.jar");
        var exposed = List.of(CarveModule.class.getPackageName());
        var leftOut = List.of("# stripped from the host jar", "example/builtin/CarveModule");

        var message = failure(() -> ModuleJarCheck.check(jar, exposed, leftOut));

        assertTrue(message.contains("leaves out"), message);
        assertTrue(message.contains("pack.User -> example.builtin.CarveModule"), message);
    }

    @Test
    void jarThatUsesAnExposedClassNotLeftOutStillPasses() {
        var jar = module("pack.User", "user:core", NO_MEMBERS,
                "graph.define(Stage.placement(\"user:core/carved\")).requires(example.builtin.CarveModule.CLEAR).run(context -> {});")
                .compileAgainstHostOf(CarveModule.class)
                .writeInto(build, "user.jar");

        assertEquals(List.of("pack.User"), ModuleJarCheck.check(jar, List.of(CarveModule.class.getPackageName()),
                List.of("example/builtin/OtherModule")));
    }
}
