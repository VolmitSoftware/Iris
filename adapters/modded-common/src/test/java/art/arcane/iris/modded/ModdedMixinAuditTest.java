package art.arcane.iris.modded;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assume;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ModdedMixinAuditTest {
    private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final Set<String> INJECTOR_ANNOTATIONS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;");
    private static final Pattern NEOFORGE_MIXIN_CONFIG = Pattern.compile(
            "\\[\\[mixins]]\\s*config\\s*=\\s*\"([^\"]+)\"");
    // One applied mixin per config proves the config was registered, which is all the audit detects.
    private static final Set<String> UNAUDITED = Set.of(
            "volmlib.entity.mixins.json/WorldGenerationWriteGuardMixin",
            "volmlib.entity.mixins.json/ChunkMapTerrainReceiptAccess",
            "volmlib.entity.mixins.json/ChunkTerrainReceiptMixin",
            "volmlib.entity.mixins.json/LevelChunkTerrainReceiptMixin",
            "volmlib.entity.mixins.json/SerializableTerrainReceiptMixin",
            "volmlib.fabric.mixins.json/BlockItemMixin",
            "volmlib.fabric.mixins.json/BlockMixin",
            "volmlib.fabric.mixins.json/PackRepositoryMixin");

    @Test
    public void everyExpectedHandlerIsAnInjectorDeclaredByItsMixin() throws IOException {
        for (ModdedMixinAudit.ExpectedMixin expected : ModdedMixinAudit.EXPECTED) {
            MixinConfig config = MixinConfig.read(expected.config());
            String section = config.sections().get(expected.mixinName());
            assertNotNull(expected.config() + " does not list " + expected.mixinName(), section);
            assertEquals(expected.mixinName() + " client-only flag disagrees with its config section",
                    "client".equals(section), expected.clientOnly());

            MixinClass mixin = MixinClass.read(config.packageName() + '.' + expected.mixinName());
            assertTrue(expected.mixinName() + " does not target " + expected.targetClass().className()
                            + " (targets " + mixin.targets() + ")",
                    mixin.targets().contains(expected.targetClass().className()));
            assertTrue(expected.mixinName() + " declares no injector handler named " + expected.handlerMethod()
                            + " (injectors " + mixin.injectors() + ")",
                    mixin.injectors().contains(expected.handlerMethod()));
        }
    }

    @Test
    public void everyRegisteredMixinIsAuditedOrDeliberatelyUnaudited() throws IOException {
        Set<String> registered = registeredConfigs();
        Assume.assumeFalse("loader metadata on this classpath declares no mixin configs", registered.isEmpty());
        Set<String> audited = new LinkedHashSet<>();
        for (ModdedMixinAudit.ExpectedMixin expected : ModdedMixinAudit.EXPECTED) {
            audited.add(expected.config() + '/' + expected.mixinName());
        }

        Set<String> listed = new TreeSet<>();
        for (String configName : registered) {
            for (String mixinName : MixinConfig.read(configName).sections().keySet()) {
                listed.add(configName + '/' + mixinName);
            }
        }

        Set<String> uncovered = new TreeSet<>(listed);
        uncovered.removeAll(audited);
        uncovered.removeAll(UNAUDITED);
        assertEquals("mixins neither audited nor deliberately unaudited", Set.of(), uncovered);

        Set<String> unregisteredAudits = new TreeSet<>(audited);
        unregisteredAudits.removeAll(listed);
        assertEquals("audited mixins missing from the registered configs", Set.of(), unregisteredAudits);
    }

    private static Set<String> registeredConfigs() throws IOException {
        Set<String> configs = new LinkedHashSet<>();
        String fabric = readResource("fabric.mod.json");
        if (fabric != null) {
            for (JsonElement entry : JsonParser.parseString(fabric).getAsJsonObject().getAsJsonArray("mixins")) {
                configs.add(entry.isJsonObject()
                        ? entry.getAsJsonObject().get("config").getAsString()
                        : entry.getAsString());
            }
        }
        String neoforge = readResource("META-INF/neoforge.mods.toml");
        if (neoforge != null) {
            Matcher matcher = NEOFORGE_MIXIN_CONFIG.matcher(neoforge);
            while (matcher.find()) {
                configs.add(matcher.group(1));
            }
        }
        return configs;
    }

    private static String readResource(String name) throws IOException {
        try (InputStream input = ModdedMixinAuditTest.class.getClassLoader().getResourceAsStream(name)) {
            return input == null ? null : new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record MixinConfig(String packageName, Map<String, String> sections) {
        static MixinConfig read(String name) throws IOException {
            String json = readResource(name);
            assertNotNull("Missing mixin config resource " + name, json);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            Map<String, String> sections = new LinkedHashMap<>();
            for (String section : new String[]{"mixins", "client", "server"}) {
                JsonArray entries = root.getAsJsonArray(section);
                if (entries == null) {
                    continue;
                }
                for (JsonElement entry : entries) {
                    sections.put(entry.getAsString(), section);
                }
            }
            return new MixinConfig(root.get("package").getAsString(), sections);
        }
    }

    private record MixinClass(Set<String> targets, Set<String> injectors) {
        static MixinClass read(String className) throws IOException {
            String resource = className.replace('.', '/') + ".class";
            byte[] bytes;
            try (InputStream input = ModdedMixinAuditTest.class.getClassLoader().getResourceAsStream(resource)) {
                assertNotNull("Missing mixin class " + className, input);
                bytes = input.readAllBytes();
            }
            Set<String> targets = new TreeSet<>();
            Set<String> injectors = new TreeSet<>();
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!MIXIN_ANNOTATION.equals(descriptor)) {
                        return null;
                    }
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitArray(String name) {
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String ignored, Object value) {
                                    if (value instanceof Type type) {
                                        targets.add(type.getClassName());
                                    } else if (value instanceof String target) {
                                        targets.add(target.replace('/', '.'));
                                    }
                                }
                            };
                        }
                    };
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                            if (INJECTOR_ANNOTATIONS.contains(annotation)) {
                                injectors.add(name);
                            }
                            return null;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new MixinClass(targets, injectors);
        }
    }
}
