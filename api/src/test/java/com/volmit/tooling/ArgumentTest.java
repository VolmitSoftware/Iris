package com.volmit.tooling;

import com.volmit.config.Reference;
import com.volmit.resource.PackResources;
import com.volmit.resource.PlatformRegistries;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArgumentTest {
    enum Mode { STRICT_SHAPE, LOOSE }

    static final class Plan {}

    private static final ResourceKind<Plan> PLANS = ResourceKind.of("test:plan", "plans", Plan.class);

    private static PackResources resources() {
        return (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(), new Class<?>[]{PackResources.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "packId" -> ResourceId.parse("demo:pack");
                    case "idOf" -> ResourceId.ofPackKey("demo", (String) arguments[0]);
                    case "ids" -> List.of(ResourceId.parse("demo:villages/plains"), ResourceId.parse("demo:villages/desert"),
                            ResourceId.parse("other:castle"));
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static CommandContext context(Optional<PlatformRegistries> platform) {
        var pack = (ToolPack) Proxy.newProxyInstance(ToolPack.class.getClassLoader(), new Class<?>[]{ToolPack.class},
                (proxy, method, arguments) -> method.getName().equals("resources") ? resources() : null);
        return (CommandContext) Proxy.newProxyInstance(CommandContext.class.getClassLoader(), new Class<?>[]{CommandContext.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "pack" -> pack;
                    case "platform" -> platform;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static CommandContext context() {
        return context(Optional.empty());
    }

    @Test
    void wordsAndTextTakeTheirTextAsIs() {
        assertEquals("plains", Argument.word("structure").parse("plains", context()));
        assertEquals("a b c", Argument.text("message").parse("a b c", context()));
        assertTrue(Argument.text("message").takesRestOfLine());
        assertFalse(Argument.word("structure").takesRestOfLine());
    }

    @Test
    void integersAndDecimalsStayInTheirRange() {
        var radius = Argument.integer("radius", 1, 100);
        assertEquals(48L, radius.parse("48", context()));
        assertEquals("radius must be a whole number from 1 to 100",
                assertThrows(IllegalArgumentException.class, () -> radius.parse("101", context())).getMessage());
        assertEquals("radius must be a whole number",
                assertThrows(IllegalArgumentException.class, () -> radius.parse("many", context())).getMessage());
        var scale = Argument.decimal("scale", 0.5, 2);
        assertEquals(1.25, scale.parse("1.25", context()));
        assertThrows(IllegalArgumentException.class, () -> scale.parse("NaN", context()));
        assertThrows(IllegalArgumentException.class, () -> scale.parse("3", context()));
    }

    @Test
    void flagsAcceptTrueAndFalseInAnyCase() {
        var verbose = Argument.flag("verbose");
        assertTrue(verbose.parse("TRUE", context()));
        assertFalse(verbose.parse("false", context()));
        assertThrows(IllegalArgumentException.class, () -> verbose.parse("yes", context()));
        assertEquals(List.of("false"), verbose.complete("f", context()));
    }

    @Test
    void choicesAreWrittenInLowerCaseWithDashesAndNameTheOptionsOnAMiss() {
        var mode = Argument.choice("mode", Mode.class);
        assertEquals(Mode.STRICT_SHAPE, mode.parse("strict-shape", context()));
        assertEquals(Mode.LOOSE, mode.parse("LOOSE", context()));
        var miss = assertThrows(IllegalArgumentException.class, () -> mode.parse("wide", context()));
        assertEquals("mode must be one of strict-shape, loose", miss.getMessage());
        assertEquals(List.of("strict-shape"), mode.complete("st", context()));
        assertEquals(List.of("loose", "strict-shape"), mode.complete("", context()));
    }

    @Test
    void resourcesAreWrittenAsThePacksKeyOrTheFullIdAndMustExist() {
        var plan = Argument.resource("structure", PLANS);
        assertEquals(ResourceId.parse("demo:villages/plains"), plan.parse("villages/plains", context()));
        assertEquals(ResourceId.parse("other:castle"), plan.parse("other:castle", context()));
        var missing = assertThrows(IllegalArgumentException.class, () -> plan.parse("villages/swamp", context()));
        assertEquals("No plans resource 'villages/swamp' in this pack", missing.getMessage());
        assertEquals(List.of("villages/desert", "villages/plains"), plan.complete("vil", context()));
        assertEquals(List.of("other:castle"), plan.complete("oth", context()));
    }

    @Test
    void registeredIdsComeFromThePlatformAndMayLeaveOutMinecraft() {
        PlatformRegistries registries = registry -> registry == Reference.STRUCTURE
                ? Set.of(ResourceId.parse("minecraft:village_plains"), ResourceId.parse("mod:tower"))
                : Set.of();
        var structure = Argument.registered("structure", Reference.STRUCTURE);
        var context = context(Optional.of(registries));
        assertEquals(ResourceId.parse("minecraft:village_plains"), structure.parse("village_plains", context));
        assertEquals(ResourceId.parse("mod:tower"), structure.parse("mod:tower", context));
        assertEquals("No structure 'mod:keep' is registered",
                assertThrows(IllegalArgumentException.class, () -> structure.parse("mod:keep", context)).getMessage());
        assertEquals(List.of("village_plains"), structure.complete("vill", context));
        assertEquals(List.of("mod:tower"), structure.complete("mod", context));
        assertThrows(IllegalArgumentException.class, () -> structure.parse("village_plains", context()));
    }

    @Test
    void anArgumentWithAFallbackIsOptionalAndCompletesWhatTheModuleSuggestsToo() {
        var required = Argument.word("bay");
        assertFalse(required.isOptional());
        assertNull(required.fallback());
        var bay = required.orElse("main").describe("The bay to open").suggesting(context -> List.of("north", "main", "north"));
        assertTrue(bay.isOptional());
        assertEquals("main", bay.fallback());
        assertEquals("The bay to open", bay.description());
        assertEquals(List.of("main", "north"), bay.complete("", context()));
        assertEquals(List.of("north"), bay.complete("N", context()));
        assertThrows(NullPointerException.class, () -> required.orElse(null));
    }
}
