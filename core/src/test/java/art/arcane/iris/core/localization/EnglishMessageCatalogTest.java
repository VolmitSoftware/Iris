package art.arcane.iris.core.localization;

import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.localization.MessageKey;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnglishMessageCatalogTest {
    private static final List<Class<?>> MESSAGE_CLASSES = List.of(
            IrisMessages.class,
            BukkitCommandMessages.class,
            BukkitCommandMessagesExtended.class,
            ModdedCommandMessages.class,
            DirectorCommandMessages.class,
            ModdedHelpMessages.class,
            RuntimeUiMessages.class,
            BukkitRuntimeMessages.class,
            RuntimeProgressMessages.class,
            PackDownloadMessages.class,
            ClientUiMessages.class,
            BukkitUiMessages.class,
            DesktopUiMessages.class
    );

    @Test
    public void everyEnglishMessageHasExactlyOneConstant() throws Exception {
        Set<String> declaredIds = new HashSet<>();
        for (Class<?> messageClass : MESSAGE_CLASSES) {
            for (MessageKey key : publicKeys(messageClass)) {
                assertTrue("Duplicate constant for " + key.id(), declaredIds.add(key.id()));
            }
        }

        Set<String> englishIds = new TreeSet<>(IrisMessages.catalog().ids());
        DirectorMessages.keys().forEach(key -> englishIds.remove(key.id()));
        Set<String> orphanedIds = new TreeSet<>(englishIds);
        orphanedIds.removeAll(declaredIds);

        assertEquals("Messages without a constant", Set.of(), orphanedIds);
        assertEquals(englishIds, new TreeSet<>(declaredIds));
    }

    private static List<MessageKey> publicKeys(Class<?> messageClass) throws IllegalAccessException {
        List<MessageKey> keys = new ArrayList<>();
        for (Field field : messageClass.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && MessageKey.class.isAssignableFrom(field.getType())) {
                keys.add((MessageKey) field.get(null));
            }
        }
        return keys;
    }
}
