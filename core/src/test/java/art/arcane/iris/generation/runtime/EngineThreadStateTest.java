package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.runtime.EngineRuntimeBuilder.RuntimeAssembly;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

public class EngineThreadStateTest {
    @Test
    public void bindingsShareOneFrameAndReleaseItWhenAllAreCleared() {
        EngineThreadState state = new EngineThreadState();
        RuntimeAssembly assembly = mock(RuntimeAssembly.class);
        IrisEngine.GenerationRuntimeBinding binding = new IrisEngine.GenerationRuntimeBinding(
                mock(IrisEngine.class), mock(GenerationRuntime.class));

        assertNull(state.current());
        state.setAssembly(assembly);
        IrisEngine.GenerationRuntimeScope scope = state.open(binding);
        EngineThreadState.Frame frame = state.current();
        assertSame(assembly, frame.assembly);
        assertSame(binding, frame.binding);

        state.setAssembly(null);
        assertSame(frame, state.current());
        assertSame(binding, state.binding());
        scope.close();
        assertNull(state.current());
    }

    @Test
    public void nestedScopesRestoreThePreviousBindingInOrder() {
        EngineThreadState state = new EngineThreadState();
        IrisEngine.GenerationRuntimeBinding outer = new IrisEngine.GenerationRuntimeBinding(
                mock(IrisEngine.class), mock(GenerationRuntime.class));
        IrisEngine.GenerationRuntimeBinding inner = new IrisEngine.GenerationRuntimeBinding(
                mock(IrisEngine.class), mock(GenerationRuntime.class));

        IrisEngine.GenerationRuntimeScope outerScope = state.open(outer);
        IrisEngine.GenerationRuntimeScope innerScope = state.open(inner);
        assertThrows(IllegalStateException.class, outerScope::close);
        innerScope.close();
        assertSame(outer, state.binding());
        outerScope.close();
        assertNull(state.current());
    }

    @Test
    public void threadsSharingATableSlotKeepSeparateFrames() throws InterruptedException {
        EngineThreadState state = new EngineThreadState();
        IrisEngine.GenerationRuntimeBinding mainBinding = new IrisEngine.GenerationRuntimeBinding(
                mock(IrisEngine.class), mock(GenerationRuntime.class));
        IrisEngine.GenerationRuntimeBinding otherBinding = new IrisEngine.GenerationRuntimeBinding(
                mock(IrisEngine.class), mock(GenerationRuntime.class));
        IrisEngine.GenerationRuntimeScope mainScope = state.open(mainBinding);
        long slot = Thread.currentThread().threadId() & 255;
        AtomicReference<Object> otherSeen = new AtomicReference<>();
        AtomicReference<Object> otherAfterClose = new AtomicReference<>(otherBinding);
        Thread colliding = null;
        while (colliding == null) {
            Thread candidate = new Thread(() -> {
                IrisEngine.GenerationRuntimeScope scope = state.open(otherBinding);
                otherSeen.set(state.binding());
                scope.close();
                otherAfterClose.set(state.current());
            });
            if ((candidate.threadId() & 255) == slot) {
                colliding = candidate;
            }
        }
        colliding.start();
        colliding.join();

        assertSame(otherBinding, otherSeen.get());
        assertNull(otherAfterClose.get());
        assertSame(mainBinding, state.binding());
        mainScope.close();
        assertNull(state.current());
    }

    @Test
    public void framesAreThreadConfinedAndScopesRejectForeignClose() throws InterruptedException {
        EngineThreadState state = new EngineThreadState();
        IrisEngine.GenerationRuntimeBinding binding = new IrisEngine.GenerationRuntimeBinding(
                mock(IrisEngine.class), mock(GenerationRuntime.class));
        IrisEngine.GenerationRuntimeScope scope = state.open(binding);
        AtomicReference<Object> seen = new AtomicReference<>(binding);
        AtomicReference<Throwable> foreignClose = new AtomicReference<>();
        Thread other = new Thread(() -> {
            seen.set(state.current());
            try {
                scope.close();
            } catch (Throwable failure) {
                foreignClose.set(failure);
            }
        });
        other.start();
        other.join();

        assertNull(seen.get());
        assertSame(IllegalStateException.class, foreignClose.get().getClass());
        scope.close();
        assertNull(state.current());
    }
}
