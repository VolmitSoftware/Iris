package art.arcane.iris.platform.bukkit.plugin;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.localization.C;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class VolmitSenderComponentDeliveryTest {
    private IrisSettings previousSettings;

    @Before
    public void installSettings() {
        previousSettings = IrisSettings.settings;
        IrisSettings settings = new IrisSettings();
        settings.getGeneral().setSpinh(0);
        settings.getGeneral().setSpins(0);
        settings.getGeneral().setSpinb(0);
        IrisSettings.settings = settings;
    }

    @After
    public void restoreSettings() {
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void sectionColorsReachTheRichSenderWithoutLiteralControlCodes() {
        List<String> messages = new ArrayList<>();
        VolmitSender sender = new VolmitSender(recordingSender(messages));

        sender.sendMessage(C.RED + "Broken");

        assertEquals(1, messages.size());
        assertFalse(messages.getFirst().contains("\u00a7"));
        assertEquals("Broken", plain(messages.getFirst()));
    }

    @Test
    public void noMiniKeepsMiniMessageTagsLiteral() {
        List<String> messages = new ArrayList<>();
        VolmitSender sender = new VolmitSender(recordingSender(messages));

        sender.sendMessage("<NOMINI><red>literal");

        assertEquals(1, messages.size());
        assertEquals("<red>literal", plain(messages.getFirst()));
    }

    @Test
    public void nonPlayerFallbackReceivesPlainTextWithoutSectionSymbols() {
        List<String> messages = new ArrayList<>();
        VolmitSender sender = new VolmitSender(plainFallbackSender(messages));

        sender.sendMessage(C.RED + "Broken");

        assertEquals(1, messages.size());
        assertEquals("Broken", messages.getFirst());
        assertFalse(messages.getFirst().contains("\u00a7"));
    }

    private static String plain(String miniMessage) {
        Component component = MiniMessage.miniMessage().deserialize(miniMessage);
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static CommandSender recordingSender(List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(
                VolmitSenderComponentDeliveryTest.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("sendRichMessage") && arguments != null && arguments.length > 0) {
                        messages.add(String.valueOf(arguments[0]));
                        return null;
                    }
                    if (method.getName().equals("sendMessage") && arguments != null && arguments.length > 0) {
                        messages.add(String.valueOf(arguments[0]));
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static CommandSender plainFallbackSender(List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(
                VolmitSenderComponentDeliveryTest.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("sendRichMessage")) {
                        throw new UnsupportedOperationException("rich delivery unavailable");
                    }
                    if (method.getName().equals("sendMessage") && arguments != null && arguments.length > 0) {
                        messages.add(String.valueOf(arguments[0]));
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return Boolean.FALSE;
        }
        if (returnType == int.class) {
            return Integer.valueOf(0);
        }
        if (returnType == long.class) {
            return Long.valueOf(0L);
        }
        return null;
    }
}
