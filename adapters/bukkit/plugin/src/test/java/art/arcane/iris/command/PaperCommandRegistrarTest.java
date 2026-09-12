package art.arcane.iris.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PaperCommandRegistrarTest {
    @Test
    public void paperCommandDelegatesExecutionAndSuggestions() {
        RecordingCommandService service = new RecordingCommandService();
        BasicCommand command = PaperCommandRegistrar.command(service);
        CommandSender sender = sender();
        CommandSourceStack source = new TestCommandSourceStack(sender);
        String[] arguments = {"create", "world"};

        command.execute(source, arguments);
        assertSame(sender, service.sender);
        assertEquals("iris", service.label);
        assertArrayEquals(arguments, service.arguments);
        assertEquals(List.of("first", "second"), command.suggest(source, new String[]{"cr"}));
        assertTrue(command.canUse(sender));
    }

    private static CommandSender sender() {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> primitiveDefault(method.getReturnType())
        );
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    private static final class RecordingCommandService extends CommandSVC {
        private CommandSender sender;
        private String label;
        private String[] arguments;

        @Override
        public void executeRoot(CommandSender sender, String label, String[] args) {
            this.sender = sender;
            this.label = label;
            this.arguments = args;
        }

        @Override
        public List<String> tabCompleteRoot(CommandSender sender, String alias, String[] args) {
            return List.of("first", "second");
        }
    }

    private record TestCommandSourceStack(CommandSender sender) implements CommandSourceStack {
        @Override
        public Location getLocation() {
            return null;
        }

        @Override
        public CommandSender getSender() {
            return sender;
        }

        @Override
        public Entity getExecutor() {
            return null;
        }

        @Override
        public CommandSourceStack withLocation(Location location) {
            return this;
        }

        @Override
        public CommandSourceStack withExecutor(Entity executor) {
            return this;
        }
    }
}
