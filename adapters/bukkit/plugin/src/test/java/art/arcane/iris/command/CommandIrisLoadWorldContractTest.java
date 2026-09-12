package art.arcane.iris.command;

import art.arcane.iris.world.BukkitWorldReconciler;
import art.arcane.volmlib.util.director.annotations.Param;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;

public class CommandIrisLoadWorldContractTest {
    @Test
    public void commandUsesTypedAsyncReconciliation() throws Exception {
        Method reconciliation = BukkitWorldReconciler.class.getDeclaredMethod(
                "loadWorld",
                File.class,
                String.class);
        assertEquals(CompletableFuture.class, reconciliation.getReturnType());
        Method commandMethod = CommandIris.class.getDeclaredMethod("loadWorld", String.class);
        Parameter worldParameter = commandMethod.getParameters()[0];
        assertEquals(CommandIris.ManagedWorldNameHandler.class,
                worldParameter.getAnnotation(Param.class).customHandler());
    }
}
