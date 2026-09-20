package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NativeMixinRegistrationTest {
    @Test
    public void sharedMixinConfigurationsNameBundledClasses() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        for (String config : List.of("volmlib.entity.mixins.json", "volmlib.client.mixins.json")) {
            assertConfiguration(loader, config);
        }
    }

    @Test
    public void fabricMixinConfigurationAccompaniesItsLoader() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        if (loader.getResource("art/arcane/volmlib/nativelib/minecraft26_2/fabric/NativeFabricBootstrap.class") != null) {
            assertConfiguration(loader, "volmlib.fabric.mixins.json");
        }
    }

    @Test
    public void declaredMixinsAvoidArrayOwnedVirtualCalls() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        List<String> configurations = new ArrayList<>(List.of("volmlib.entity.mixins.json", "volmlib.client.mixins.json"));
        if (loader.getResource("volmlib.fabric.mixins.json") != null) {
            configurations.add("volmlib.fabric.mixins.json");
        }
        List<String> violations = new ArrayList<>();
        for (String configuration : configurations) {
            for (String resource : configurationClasses(loader, configuration)) {
                try (InputStream input = loader.getResourceAsStream(resource)) {
                    assertNotNull(resource, input);
                    new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                         String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override
                                public void visitMethodInsn(int opcode, String owner, String method,
                                                            String methodDescriptor, boolean isInterface) {
                                    if (opcode == Opcodes.INVOKEVIRTUAL && owner.startsWith("[")) {
                                        violations.add(resource + ": " + name + " -> " + owner + "." + method);
                                    }
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
        assertTrue("Array-owned calls cannot be transformed by Mixin: " + violations, violations.isEmpty());
    }

    private static void assertConfiguration(ClassLoader loader, String config) throws Exception {
        for (String resource : configurationClasses(loader, config)) {
            assertNotNull(config + ": " + resource, loader.getResource(resource));
        }
    }

    private static List<String> configurationClasses(ClassLoader loader, String config) throws Exception {
        List<String> classes = new ArrayList<>();
        try (InputStream stream = loader.getResourceAsStream(config)) {
            assertNotNull(config, stream);
            JsonObject document = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            String base = document.get("package").getAsString().replace('.', '/') + "/";
            for (String side : List.of("mixins", "client", "server")) {
                JsonArray mixins = document.getAsJsonArray(side);
                if (mixins == null) {
                    continue;
                }
                for (JsonElement mixin : mixins) {
                    String resource = base + mixin.getAsString().replace('.', '/') + ".class";
                    classes.add(resource);
                }
            }
            if (document.has("plugin")) {
                String plugin = document.get("plugin").getAsString().replace('.', '/') + ".class";
                classes.add(plugin);
            }
        }
        return classes;
    }
}
