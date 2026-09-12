package art.arcane.iris.modded.command;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.director.help.DirectorHelpMessages;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.VolmitLocales;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class ModdedLanguageCommands {
    private ModdedLanguageCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("language")
                .executes(ModdedCommandTree.localized(context -> open(context.getSource())))
                .then(scope("self", false))
                .then(scope("server", true).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> scope(String name, boolean server) {
        return Commands.literal(name)
                .then(Commands.argument("locale", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(IrisLanguage.availableLocales(), builder))
                        .executes(ModdedCommandTree.localized(context -> select(context.getSource(), server, StringArgumentType.getString(context, "locale")))));
    }

    private static int open(CommandSourceStack source) {
        ModdedCommandFeedback.clear(source);
        ModdedCommandFeedback.send(source, ModdedCommandFeedback.banner("/iris language"));
        ModdedCommandFeedback.send(source, ModdedCommandFeedback.button(
                "〈 " + IrisLanguage.plain(DirectorHelpMessages.BACK), "/iris", "Iris", true));
        ModdedCommandFeedback.send(source, ModdedCommandFeedback.text(
                "Iris language: " + IrisLanguage.activeLocale(), ModdedCommandFeedback.DESCRIPTION));
        for (String locale : IrisLanguage.availableLocales()) {
            String name = VolmitLocales.displayName(locale).orElse(locale);
            ModdedCommandFeedback.send(source, ModdedCommandFeedback.text("⇀ ", ModdedCommandFeedback.DARK_GREEN)
                    .append(ModdedCommandFeedback.button(name + " [me]", "/iris language self " + locale, "Use " + name, true))
                    .append(ModdedCommandTree.isGamemaster(source)
                            ? ModdedCommandFeedback.button(" [server]", "/iris language server " + locale, "Set the server default", true)
                            : Component.empty()));
        }
        ModdedCommandFeedback.send(source, ModdedCommandFeedback.button(
                "Use server default", "/iris language self reset", "Clear your personal language", true));
        ModdedCommandFeedback.ok(source, ModdedCommandFeedback.footer());
        return 1;
    }

    private static int select(CommandSourceStack source, boolean server, String locale) {
        PluginLanguageService service = IrisLanguage.selections();
        if (service == null) {
            source.sendFailure(Component.literal("Iris language service is unavailable."));
            return 0;
        }
        CompletableFuture<String> selection;
        if (server) {
            selection = service.selectDefault(locale).thenApply(ignored -> service.defaultLocale());
        } else {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.literal("Personal language selection requires a player."));
                return 0;
            }
            UUID playerId = player.getUUID();
            CompletableFuture<Void> personal = locale.equals("reset")
                    ? service.clearPlayer(playerId) : service.selectPlayer(playerId, locale);
            selection = personal.thenApply(ignored -> service.effectiveLocale(playerId));
        }
        selection.whenComplete((activeLocale, failure) -> source.getServer().execute(() -> {
            if (failure != null) {
                IrisLogging.reportError(failure);
                source.sendFailure(Component.literal("Language selection failed. See the server log."));
            } else if (!locale.equals("reset")
                    && !locale.replace('-', '_').equalsIgnoreCase(activeLocale.replace('-', '_'))) {
                source.sendSuccess(() -> Component.literal("Iris: " + locale
                        + " unavailable; using English (" + activeLocale + ")."), false);
            } else {
                source.sendSuccess(() -> Component.literal("Language updated: " + activeLocale), false);
            }
        }));
        return 1;
    }
}
