package art.arcane.iris.purity;

import art.arcane.iris.core.loader.IrisData;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

public class CoreProcessStreamPurityGateTest {
    private static final String SYSTEM_OWNER = "java/lang/System";
    private static final String STACK_TRACE_METHOD = "printStackTrace";

    @Test
    public void coreProductionBytecodeNeverTouchesTheProcessStreams() throws Exception {
        Path classes = Path.of(IrisData.class.getProtectionDomain().getCodeSource().getLocation().toURI());
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

        assertTrue("Core production code must report through IrisLogging, never the process streams."
                + " printStackTrace(PrintStream)/printStackTrace(PrintWriter) write to a caller-supplied"
                + " sink and stay allowed:" + String.join("", violations), violations.isEmpty());
    }

    private static void collectViolations(Path classFile, List<String> violations) throws Exception {
        ClassReader reader;
        try (InputStream input = Files.newInputStream(classFile)) {
            reader = new ClassReader(input);
        }
        String owner = reader.getClassName();

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(int opcode, String fieldOwner, String fieldName, String fieldDescriptor) {
                        if (opcode != Opcodes.GETSTATIC || !SYSTEM_OWNER.equals(fieldOwner)) {
                            return;
                        }
                        if ("out".equals(fieldName) || "err".equals(fieldName)) {
                            violations.add(report(owner, name, "System." + fieldName));
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
                        if (STACK_TRACE_METHOD.equals(methodName) && "()V".equals(methodDescriptor)) {
                            violations.add(report(owner, name, methodOwner.replace('/', '.') + "#printStackTrace()"));
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static String report(String owner, String method, String detail) {
        return "\n  " + owner.replace('/', '.') + "#" + method + " -> " + detail;
    }
}
