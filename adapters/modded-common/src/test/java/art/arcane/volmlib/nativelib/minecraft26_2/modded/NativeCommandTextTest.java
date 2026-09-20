package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NativeCommandTextTest {
    @Test
    public void preservesMutableCompositionAndInteractiveStyle() {
        NativeCommandText text = NativeCommandText.literal("World")
                .withStyle(style -> style.withColor(0x123456).withBold(true)
                        .runCommand("/terrain list").hover(NativeCommandText.literal("Open list")));
        assertSame(text, text.append(" ready"));
        assertEquals("World ready", text.getString());
        Component component = text.component();
        assertEquals(0x123456, component.getStyle().getColor().getValue());
        assertTrue(component.getStyle().isBold());
        assertEquals("/terrain list", ((ClickEvent.RunCommand) component.getStyle().getClickEvent()).command());
        assertEquals("Open list", ((HoverEvent.ShowText) component.getStyle().getHoverEvent()).value().getString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void preservesLazySuccessEvaluationAndFailureText() {
        CommandSourceStack handle = mock(CommandSourceStack.class);
        NativeCommandSource source = new NativeCommandSource(handle);
        AtomicInteger evaluations = new AtomicInteger();
        source.sendSuccess(() -> {
            evaluations.incrementAndGet();
            return NativeCommandText.literal("Completed");
        }, true);
        assertEquals(0, evaluations.get());
        ArgumentCaptor<Supplier<Component>> success = ArgumentCaptor.forClass(Supplier.class);
        verify(handle).sendSuccess(success.capture(), eq(true));
        assertEquals("Completed", success.getValue().get().getString());
        assertEquals(1, evaluations.get());
        NativeCommandText failure = NativeCommandText.literal("Failed");
        source.sendFailure(failure);
        verify(handle).sendFailure(failure.component());
    }
}
