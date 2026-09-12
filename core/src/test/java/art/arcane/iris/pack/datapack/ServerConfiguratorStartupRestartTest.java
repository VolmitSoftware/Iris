package art.arcane.iris.pack.datapack;

import art.arcane.iris.spi.IrisLogging;
import org.bukkit.Bukkit;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

public class ServerConfiguratorStartupRestartTest {
    @Test
    public void startupBoundaryRestartsImmediatelyAndStopsIfRestartReturns() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            ServerConfigurator.restartAtStartupBoundary(" updated external datapacks ");

            bukkit.verify(Bukkit::restart, times(1));
            bukkit.verify(Bukkit::shutdown, times(1));
            logging.verify(() -> IrisLogging.warn(
                    "updated external datapacks Restarting server before default worlds are loaded."));
            logging.verify(() -> IrisLogging.error(
                    "The immediate Iris startup restart returned unexpectedly; stopping the server instead."));
        }
    }

    @Test
    public void startupBoundaryStopsWhenImmediateRestartThrows() {
        IllegalStateException failure = new IllegalStateException("restart unavailable");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            bukkit.when(Bukkit::restart).thenThrow(failure);

            ServerConfigurator.restartAtStartupBoundary(null);

            bukkit.verify(Bukkit::restart, times(1));
            bukkit.verify(Bukkit::shutdown, times(1));
            logging.verify(() -> IrisLogging.reportError(
                    "Unable to restart the server at the Iris startup boundary.", failure));
        }
    }

    @Test
    public void immediateRestartCapabilityIsOptional() throws ReflectiveOperationException {
        RestartCapableApi.restarted = false;

        assertTrue(ServerConfigurator.invokeImmediateRestartIfSupported(RestartCapableApi.class));
        assertTrue(RestartCapableApi.restarted);
        assertFalse(ServerConfigurator.invokeImmediateRestartIfSupported(ShutdownOnlyApi.class));
    }

    @Test
    public void productionBytecodeDoesNotLinkBukkitRestartDirectly() throws Exception {
        AtomicBoolean directRestartInvocation = new AtomicBoolean(false);
        ClassReader reader = new ClassReader(ServerConfigurator.class.getName());
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
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String methodName,
                            String methodDescriptor,
                            boolean isInterface
                    ) {
                        if ("org/bukkit/Bukkit".equals(owner) && "restart".equals(methodName)) {
                            directRestartInvocation.set(true);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertFalse(directRestartInvocation.get());
    }

    public static final class RestartCapableApi {
        private static boolean restarted;

        public static void restart() {
            restarted = true;
        }
    }

    public static final class ShutdownOnlyApi {
    }
}
