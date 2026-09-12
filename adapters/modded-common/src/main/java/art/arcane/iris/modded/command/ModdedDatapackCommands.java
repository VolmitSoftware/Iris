/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded.command;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.bukkit.nms.datapack.DataVersion;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedServerLevels;
import art.arcane.volmlib.util.collection.KList;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
public final class ModdedDatapackCommands {
    private static final Predicate<CommandSourceStack> GATE = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    private static final String WORLD_PACK_NAME = "iris";

    private ModdedDatapackCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(GATE);

        root.executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), name)));

        root.then(Commands.literal("status")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> status(context.getSource()))));

        root.then(Commands.literal("install")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> install(context.getSource()))));

        root.then(Commands.literal("list")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> list(context.getSource()))));
        root.then(Commands.literal("ls")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> list(context.getSource()))));

        root.then(message("ingest", "Managed Modrinth datapack ingest is Bukkit-only. On modded servers install the datapack folder or zip in world/datapacks, enable it, restart, then use /iris datapack list to confirm it is enabled. Registered structures generate natively in Iris dimensions."));
        root.then(message("pull", "Managed Modrinth datapack ingest is Bukkit-only. On modded servers install the datapack folder or zip in world/datapacks, enable it, restart, then use /iris datapack list to confirm it is enabled. Registered structures generate natively in Iris dimensions."));

        root.then(message("remove", "Managed datapack removal is Bukkit-only. On modded servers disable the pack, delete its folder or zip from world/datapacks, and restart."));
        root.then(message("rm", "Managed datapack removal is Bukkit-only. On modded servers disable the pack, delete its folder or zip from world/datapacks, and restart."));

        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> message(String name, String text) {
        return Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> {
                    IrisModdedCommands.fail(context.getSource(), text);
                    return 0;
                }))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> {
                            IrisModdedCommands.fail(context.getSource(), text);
                            return 0;
                        })));
    }

    private static File worldDatapacksFolder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR).toFile();
    }

    private static File overrideFile(MinecraftServer server, String dimensionKey) {
        return new File(worldDatapacksFolder(server), WORLD_PACK_NAME + "/data/irisworldgen/dimension_type/" + IrisDimension.sanitizeDimensionTypeKeyValue(dimensionKey) + ".json");
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int irisLevels = 0;
        int mismatches = 0;
        for (ServerLevel level : ModdedServerLevels.levels(server)) {
            if (!(level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator irisGenerator)) {
                continue;
            }
            irisLevels++;
            String dimensionId = level.dimension().identifier().toString();
            String typeKey = level.dimensionTypeRegistration().unwrapKey()
                    .map((net.minecraft.resources.ResourceKey<DimensionType> key) -> key.identifier().toString())
                    .orElse("inline");
            DimensionType active = level.dimensionType();
            int activeMin = active.minY();
            int activeMax = active.minY() + active.height();

            Engine engine = IrisModdedCommands.engineFor(level);
            if (engine == null || engine.getDimension() == null) {
                IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_TYPE_ACTIVE_LOGICAL_PACK_ENGINE_NOT_STARTED_PACK_HEIGHTS_UNKNOWN, MessageArgument.untrusted("dimensionId", dimensionId), MessageArgument.untrusted("typeKey", typeKey), MessageArgument.untrusted("activeMin", activeMin), MessageArgument.untrusted("activeMax", activeMax), MessageArgument.untrusted("value", active.logicalHeight()), MessageArgument.untrusted("value2", irisGenerator.dimensionKey())));
                continue;
            }
            IrisDimension dimension = engine.getDimension();
            int packMin = dimension.getMinHeight();
            int packMax = dimension.getMaxHeight();
            int packLogical = dimension.getLogicalHeight();
            boolean matches = packMin == activeMin && packMax == activeMax && packLogical == active.logicalHeight();
            File override = overrideFile(server, irisGenerator.dimensionKey());
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_TYPE_ACTIVE_LOGICAL_PACK_WANTS_LOGICAL, MessageArgument.untrusted("dimensionId", dimensionId), MessageArgument.untrusted("typeKey", typeKey), MessageArgument.untrusted("activeMin", activeMin), MessageArgument.untrusted("activeMax", activeMax), MessageArgument.untrusted("value", active.logicalHeight()), MessageArgument.untrusted("value2", dimension.getLoadKey()), MessageArgument.untrusted("packMin", packMin), MessageArgument.untrusted("packMax", packMax), MessageArgument.untrusted("packLogical", packLogical), MessageArgument.trusted("value3", IrisLanguage.plain(matches ? RuntimeUiMessages.STATUS_MATCH : RuntimeUiMessages.STATUS_MISMATCH)), MessageArgument.trusted("value4", override.isFile() ? IrisLanguage.plain(RuntimeUiMessages.DATAPACK_OVERRIDE_SUFFIX) : "")));
            if (!matches) {
                mismatches++;
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_WARNING_ACTIVE_DIMENSION_TYPE_DOES_NOT_MATCH_PACK_IRIS_WILL));
            }
        }
        if (irisLevels == 0) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_NO_IRIS_DIMENSIONS_ARE_LOADED));
            return 0;
        }
        if (mismatches == 0) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_ALL_IRIS_DIMENSION_S_MATCH_THEIR_PACK_HEIGHT_RANGES, MessageArgument.untrusted("irisLevels", irisLevels)));
        }
        return 1;
    }

    private static int install(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<String> written = new ArrayList<>();
        for (ServerLevel level : ModdedServerLevels.levels(server)) {
            if (!(level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator irisGenerator)) {
                continue;
            }
            Engine engine = IrisModdedCommands.engineFor(level);
            if (engine == null || engine.getDimension() == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_ENGINE_NOT_STARTED_CANNOT_DERIVE_ITS_DIMENSION_TYPE_YET, MessageArgument.untrusted("value", level.dimension().identifier())));
                continue;
            }
            IrisDimension dimension = engine.getDimension();
            String json;
            try {
                json = dimension.getDimensionType().toJson(DataVersion.getLatest().get());
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris dimension type generation failed for {}", dimension.getLoadKey(), e);
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_DIMENSION_TYPE_GENERATION_FAILED, MessageArgument.untrusted("value", level.dimension().identifier()), MessageArgument.untrusted("value2", String.valueOf(e.getMessage()))));
                continue;
            }
            File output = overrideFile(server, irisGenerator.dimensionKey());
            try {
                output.getParentFile().mkdirs();
                Files.writeString(output.toPath(), json, StandardCharsets.UTF_8);
                written.add(output.getPath());
            } catch (IOException e) {
                ModdedIrisLog.error("Iris dimension type write failed for {}", output, e);
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_FAILED_WRITE, MessageArgument.untrusted("output", output), MessageArgument.untrusted("value", String.valueOf(e.getMessage()))));
            }
        }
        if (written.isEmpty()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_NO_IRIS_DIMENSIONS_WITH_RUNNING_ENGINES_FOUND_NOTHING_WAS_INSTALLED));
            return 0;
        }

        File mcmeta = new File(worldDatapacksFolder(server), WORLD_PACK_NAME + "/pack.mcmeta");
        int packFormat = DataVersion.getLatest().getPackFormat();
        String meta = "{\n"
                + "  \"pack\": {\n"
                + "    \"description\": \"Iris dimension types derived from the installed Iris packs.\",\n"
                + "    \"pack_format\": " + packFormat + ",\n"
                + "    \"min_format\": " + packFormat + ",\n"
                + "    \"max_format\": " + packFormat + "\n"
                + "  }\n"
                + "}\n";
        try {
            mcmeta.getParentFile().mkdirs();
            Files.writeString(mcmeta.toPath(), meta, StandardCharsets.UTF_8);
            written.add(mcmeta.getPath());
        } catch (IOException e) {
            ModdedIrisLog.error("Iris pack.mcmeta write failed for {}", mcmeta, e);
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_FAILED_WRITE_2, MessageArgument.untrusted("mcmeta", mcmeta), MessageArgument.untrusted("value", String.valueOf(e.getMessage()))));
            return 0;
        }

        for (String path : written) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_WROTE, MessageArgument.untrusted("path", path)));
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_WORLD_DATAPACK_DIMENSION_TYPE_OVERRIDES_INSTALLED_RESTART_SERVER_DIMENSION_TYPES, MessageArgument.untrusted("WORLDPACKNAME", WORLD_PACK_NAME)));
        return 1;
    }

    private static int list(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        LinkedHashSet<String> configured = new LinkedHashSet<>();
        File packsRoot = ModdedPackCommands.packsRoot();
        for (File pack : PackDirectoryResolver.listVisiblePackDirectories(packsRoot)) {
            if (!new File(pack, "dimensions").isDirectory()) {
                continue;
            }
            try {
                IrisData data = IrisData.get(pack);
                for (IrisDimension dimension : data.getDimensionLoader().loadAll(data.getDimensionLoader().getPossibleKeys())) {
                    if (dimension == null || dimension.getDatapackImports() == null) {
                        continue;
                    }
                    for (String url : dimension.getDatapackImports()) {
                        if (url != null && !url.isBlank()) {
                            configured.add(url.trim());
                        }
                    }
                }
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris datapack import scan failed for pack {}", pack.getName(), e);
            }
        }

        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_CONFIGURED_DATAPACK_IMPORTS, MessageArgument.untrusted("value", configured.size())));
        for (String url : configured) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_MESSAGE, MessageArgument.untrusted("url", url)));
        }
        if (!configured.isEmpty()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_MODRINTH_INGEST_IS_BUKKIT_ONLY_INSTALL_THESE_MANUALLY_INTO_WORLD));
        }

        File datapacks = worldDatapacksFolder(server);
        File[] installed = datapacks.isDirectory()
                ? datapacks.listFiles(file -> file.isDirectory() || file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".zip"))
                : null;
        Set<String> availableIds = new HashSet<>(server.getPackRepository().getAvailableIds());
        Set<String> selectedIds = new HashSet<>(server.getPackRepository().getSelectedIds());
        KList<String> names = new KList<>();
        if (installed != null) {
            for (File installedPack : installed) {
                String name = installedPack.getName();
                String repositoryId = resolveRepositoryId(name, availableIds);
                String state;
                if (repositoryId == null) {
                    state = "unavailable";
                } else if (selectedIds.contains(repositoryId)) {
                    state = "enabled";
                } else {
                    state = "disabled";
                }
                names.add(name + " [" + state + "]");
            }
        }
        Collections.sort(names);
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_INSTALLED_WORLD_DATAPACKS, MessageArgument.untrusted("value", names.size())));
        for (String name : names) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_MESSAGE_2, MessageArgument.untrusted("name", name)));
        }
        return 1;
    }

    private static String resolveRepositoryId(String filename, Set<String> availableIds) {
        String direct = "file/" + filename;
        if (availableIds.contains(direct)) {
            return direct;
        }
        for (String availableId : availableIds) {
            if (availableId.equals(filename) || availableId.endsWith("/" + filename)) {
                return availableId;
            }
        }
        return null;
    }
}
