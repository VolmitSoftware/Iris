package art.arcane.iris.modded.command;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.director.help.DirectorHelpMessages;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.LanguageCatalogMerge;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.TextKey;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class ModdedLanguageCommands {
    private ModdedLanguageCommands() {
    }

    static LiteralArgumentBuilder<NativeCommandSource> tree() {
        return NativeCommandRegistration.literal("language")
                .executes(ModdedCommandTree.localized(context -> open(context.getSource())))
                .then(scope("self", false))
                .then(scope("server", true).requires(NativeCommandRegistration.GAMEMASTERS))
                .then(NativeCommandRegistration.literal("update").requires(NativeCommandRegistration.GAMEMASTERS)
                        .executes(ModdedCommandTree.localized(context -> update(context.getSource()))));
    }

    private static LiteralArgumentBuilder<NativeCommandSource> scope(String name, boolean server) {
        return NativeCommandRegistration.literal(name)
                .then(NativeCommandRegistration.argument("locale", StringArgumentType.word())
                        .suggests((context, builder) -> NativeCommandRegistration.suggest(IrisLanguage.availableLocales(), builder))
                        .executes(ModdedCommandTree.localized(context -> select(context.getSource(), server, StringArgumentType.getString(context, "locale")))));
    }

    private static int open(NativeCommandSource source) {
        ModdedCommandFeedback.clear(source);
        ModdedCommandFeedback.send(source, ModdedCommandFeedback.banner("/iris language"));
        ModdedCommandFeedback.send(source, ModdedCommandFeedback.button(
                "〈 " + IrisLanguage.plain(DirectorHelpMessages.BACK), "/iris", "Iris", true));
        ModdedCommandFeedback.send(source, ModdedCommandFeedback.text(
                "Iris language: " + IrisLanguage.activeLocale(), ModdedCommandFeedback.DESCRIPTION));
        for (String locale : IrisLanguage.availableLocales()) {
            String name = IrisLanguage.displayName(locale);
            ModdedCommandFeedback.send(source, ModdedCommandFeedback.text("⇀ ", ModdedCommandFeedback.DARK_GREEN)
                    .append(ModdedCommandFeedback.button(name + " [me]", "/iris language self " + locale, "Use " + name, true))
                    .append(ModdedCommandTree.isGamemaster(source)
                            ? ModdedCommandFeedback.button(" [server]", "/iris language server " + locale, "Set the server default", true)
                            : NativeCommandText.empty()));
        }
        ModdedCommandFeedback.send(source, ModdedCommandFeedback.button(
                "Use server default", "/iris language self reset", "Clear your personal language", true));
        ModdedCommandFeedback.ok(source, ModdedCommandFeedback.footer());
        return 1;
    }

    private static int update(NativeCommandSource source) {
        source.sendSuccess(() -> NativeCommandText.literal(updateMessage(BukkitLanguageMessages.UPDATE_CHECKING)), false);
        IrisLanguage.checkUpstream().whenComplete((report, failure) -> source.execute(() -> reportUpdate(source, report, failure)));
        return 1;
    }

    private static void reportUpdate(NativeCommandSource source, LanguageUpstreamSync.SyncReport report, Throwable failure) {
        if (failure != null || report == null) {
            if (failure != null) {
                IrisLogging.reportError(failure);
            }
            source.sendFailure(NativeCommandText.literal(updateMessage(BukkitLanguageMessages.UPDATE_FAILED,
                    MessageArgument.untrusted("reason", failure == null ? "no report" : String.valueOf(failure.getMessage())))));
            return;
        }
        if (report.failure().isPresent()) {
            source.sendFailure(NativeCommandText.literal(updateMessage(BukkitLanguageMessages.UPDATE_FAILED,
                    MessageArgument.untrusted("reason", report.failure().get()))));
            return;
        }
        if (report.coolingDown()) {
            source.sendSuccess(() -> NativeCommandText.literal(updateMessage(BukkitLanguageMessages.UPDATE_COOLDOWN,
                    MessageArgument.untrusted("remaining", report.cooldownRemaining()))), false);
            return;
        }
        for (String locale : report.changedLocales()) {
            LanguageCatalogMerge.MergeResult result = report.merged().get(locale);
            source.sendSuccess(() -> NativeCommandText.literal(updateMessage(BukkitLanguageMessages.UPDATE_APPLIED,
                    MessageArgument.untrusted("locale", locale),
                    MessageArgument.untrusted("added", String.valueOf(result.added().size())),
                    MessageArgument.untrusted("updated", String.valueOf(result.updated().size() + result.removed().size())),
                    MessageArgument.untrusted("kept", String.valueOf(result.kept().size())))), false);
        }
        for (Map.Entry<String, String> entry : report.failures().entrySet()) {
            source.sendFailure(NativeCommandText.literal(updateMessage(BukkitLanguageMessages.UPDATE_LOCALE_FAILED,
                    MessageArgument.untrusted("locale", entry.getKey()),
                    MessageArgument.untrusted("reason", entry.getValue()))));
        }
        if (report.changedLocales().isEmpty() && report.failures().isEmpty()) {
            source.sendSuccess(() -> NativeCommandText.literal(updateMessage(BukkitLanguageMessages.UPDATE_UNCHANGED)), false);
        }
    }

    private static String updateMessage(TextKey key, MessageArgument... arguments) {
        MessageArgument[] resolved = new MessageArgument[arguments.length + 1];
        resolved[0] = MessageArgument.untrusted("plugin", "Iris");
        System.arraycopy(arguments, 0, resolved, 1, arguments.length);
        return IrisLanguage.plain(key, resolved);
    }

    private static int select(NativeCommandSource source, boolean server, String locale) {
        PluginLanguageService service = IrisLanguage.selections();
        if (service == null) {
            source.sendFailure(NativeCommandText.literal("Iris language service is unavailable."));
            return 0;
        }
        CompletableFuture<String> selection;
        if (server) {
            selection = service.selectDefault(locale).thenApply(ignored -> service.defaultLocale());
        } else {
            UUID playerId = source.playerId();
            if (playerId == null) {
                source.sendFailure(NativeCommandText.literal("Personal language selection requires a player."));
                return 0;
            }
            CompletableFuture<Void> personal = locale.equals("reset")
                    ? service.clearPlayer(playerId) : service.selectPlayer(playerId, locale);
            selection = personal.thenApply(ignored -> service.effectiveLocale(playerId));
        }
        selection.whenComplete((activeLocale, failure) -> source.execute(() -> {
            if (failure != null) {
                IrisLogging.reportError(failure);
                source.sendFailure(NativeCommandText.literal("Language selection failed. See the server log."));
            } else if (!locale.equals("reset")
                    && !locale.replace('-', '_').equalsIgnoreCase(activeLocale.replace('-', '_'))) {
                source.sendSuccess(() -> NativeCommandText.literal("Iris: " + locale
                        + " unavailable; using English (" + activeLocale + ")."), false);
            } else {
                source.sendSuccess(() -> NativeCommandText.literal("Language updated: " + activeLocale), false);
            }
        }));
        return 1;
    }
}
