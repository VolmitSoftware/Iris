package art.arcane.iris.spi;

import art.arcane.iris.studio.StudioSVC;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PacksFolderContractTest {
    private static final String PLATFORM_OWNER = "art/arcane/iris/spi/IrisPlatform";
    private static final String PACKS_ROOT_NAME = StudioSVC.WORKSPACE_NAME;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void defaultPacksFolderIsDataFolderPacks() {
        File data = temporaryFolder.getRoot();
        IrisPlatform platform = mock(IrisPlatform.class, CALLS_REAL_METHODS);
        when(platform.dataFolder()).thenReturn(data);

        assertEquals(new File(data, "packs"), platform.packsFolder());
        assertEquals(new File(data, "packs" + File.separator + "overworld"), platform.packsFolderNoCreate("overworld"));
    }

    @Test
    public void noCompiledCallSiteResolvesThePacksRootByHand() throws Exception {
        Path classes = Path.of(StudioSVC.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue("core classes must be a directory to scan, got " + classes, Files.isDirectory(classes));
        List<String> violations = new ArrayList<>();
        int scanned = 0;

        try (Stream<Path> tree = Files.walk(classes)) {
            for (Path file : tree.filter(candidate -> candidate.toString().endsWith(".class")).toList()) {
                collectViolations(file, violations);
                scanned++;
            }
        }

        assertTrue("the gate scanned only " + scanned + " core classes, so it proves nothing", scanned > 1000);

        assertTrue("These methods spell the packs root by hand instead of going through"
                + " IrisPlatform.packsFolder()/packsFolderNoCreate():" + String.join("", violations),
                violations.isEmpty());
    }

    private static void collectViolations(Path classFile, List<String> violations) throws Exception {
        ClassReader reader;
        try (InputStream input = Files.newInputStream(classFile)) {
            reader = new ClassReader(input);
        }
        String owner = reader.getClassName();
        if (owner.equals(PLATFORM_OWNER)) {
            return;
        }

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                return new PacksRootCallSite(owner, name, violations);
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static final class PacksRootCallSite extends MethodVisitor {
        private final String owner;
        private final String method;
        private final List<String> violations;
        private boolean loadsPacksRootName;
        private boolean resolvesDataFolder;

        private PacksRootCallSite(String owner, String method, List<String> violations) {
            super(Opcodes.ASM9);
            this.owner = owner;
            this.method = method;
            this.violations = violations;
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (PACKS_ROOT_NAME.equals(value)) {
                loadsPacksRootName = true;
            }
        }

        @Override
        public void visitMethodInsn(
                int opcode,
                String methodOwner,
                String methodName,
                String methodDescriptor,
                boolean isInterface
        ) {
            if (!PLATFORM_OWNER.equals(methodOwner)) {
                return;
            }
            if ("dataFolder".equals(methodName) || "dataFolderNoCreate".equals(methodName)) {
                resolvesDataFolder = true;
            }
        }

        @Override
        public void visitEnd() {
            if (loadsPacksRootName && resolvesDataFolder) {
                violations.add("\n  " + owner.replace('/', '.') + "#" + method);
            }
        }
    }
}
