package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class BrigadierSourceMapperTest {
    @Test
    public void preservesArgumentsSourceIdentityPermissionsAndAliasRedirects() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        Object allowed = new Object();
        List<Object> observed = new ArrayList<>();
        BrigadierSourceMapper<Source, Object> mapper = mapper(dispatcher);
        LiteralCommandNode<Source> root = mapper.register(LiteralArgumentBuilder.<Source>literal("terrain")
                .requires(source -> source.handle() == allowed)
                .then(RequiredArgumentBuilder.<Source, Integer>argument("count", IntegerArgumentType.integer())
                        .executes(context -> {
                            observed.add(context.getSource().handle());
                            assertEquals("count", context.getNodes().getLast().getNode().getName());
                            return IntegerArgumentType.getInteger(context, "count");
                        })));
        mapper.register(LiteralArgumentBuilder.<Source>literal("ter").redirect(root));

        assertEquals(7, dispatcher.execute("terrain 7", allowed));
        assertEquals(13, dispatcher.execute("ter 13", allowed));
        assertEquals(2, observed.size());
        assertSame(allowed, observed.getFirst());
        assertSame(allowed, observed.getLast());
        assertEquals(0, dispatcher.parse("terrain 7", new Object()).getContext().getNodes().size());
    }

    @Test
    public void mapsSuggestionsAndForkedSources() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        Object original = new Object();
        Object redirected = new Object();
        BrigadierSourceMapper<Source, Object> mapper = mapper(dispatcher);
        LiteralCommandNode<Source> root = mapper.register(LiteralArgumentBuilder.<Source>literal("terrain")
                .then(RequiredArgumentBuilder.<Source, String>argument("pack", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            assertSame(original, context.getSource().handle());
                            return builder.suggest("forest").buildFuture();
                        })
                        .executes(context -> {
                            assertSame(redirected, context.getSource().handle());
                            assertEquals("forest", StringArgumentType.getString(context, "pack"));
                            return 3;
                        })));
        mapper.register(LiteralArgumentBuilder.<Source>literal("forked")
                .fork(root, context -> List.of(new Source(redirected))));

        Suggestions suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("terrain f", original)).get();
        assertEquals("forest", suggestions.getList().getFirst().getText());
        assertEquals(1, dispatcher.execute("forked forest", original));
    }

    private static BrigadierSourceMapper<Source, Object> mapper(CommandDispatcher<Object> dispatcher) {
        return new BrigadierSourceMapper<>(dispatcher,
                new BrigadierSourceMapper.SourceMapping<>(Source::new, Source::handle));
    }

    private record Source(Object handle) {
    }
}
