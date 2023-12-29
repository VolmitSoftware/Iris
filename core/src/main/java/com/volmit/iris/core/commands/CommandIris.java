/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.commands;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.core.tools.IrisBenchmarking;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.core.safeguard.UtilsSFG;
import com.volmit.iris.engine.object.IrisWorld;
import com.volmit.iris.engine.platform.BukkitChunkGenerator;
import com.volmit.iris.engine.platform.DummyChunkGenerator;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.decree.specialhandlers.NullablePlayerHandler;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.volmit.iris.Iris.service;
import static com.volmit.iris.core.service.EditSVC.deletingWorld;
import static com.volmit.iris.core.tools.IrisBenchmarking.inProgress;
import static com.volmit.iris.core.safeguard.IrisSafeguard.unstablemode;
import static com.volmit.iris.core.safeguard.ServerBootSFG.incompatibilities;

@Decree(name = "iris", aliases = {"ir", "irs"}, description = "Basic Command")
public class CommandIris implements DecreeExecutor {
    private CommandStudio studio;
    private CommandPregen pregen;
    private CommandLazyPregen lazyPregen;
    private CommandSettings settings;
    private CommandObject object;
    private CommandJigsaw jigsaw;
    private CommandWhat what;
    private CommandEdit edit;
    private CommandFind find;
    private CommandDeveloper developer;

    public static @Getter String BenchDimension;
    public static boolean worldCreation = false;
    String WorldToLoad;
    String WorldEngine;
    String worldNameToCheck = "YourWorldName";
    VolmitSender sender = Iris.getSender();

    @Decree(description = "Create a new world", aliases = {"+", "c"})
    public void create(
            @Param(aliases = "world-name", description = "The name of the world to create")
            String name,
            @Param(aliases = "dimension", description = "The dimension type to create the world with", defaultValue = "default")
            IrisDimension type,
            @Param(description = "The seed to generate the world with", defaultValue = "1337")
            long seed
    ) {
        if(sender() instanceof Player) {
            if (incompatibilities.get("Multiverse-Core")) {
                sender().sendMessage(C.RED + "Your server has an incompatibility that may corrupt all worlds on the server if not handled properly.");
                sender().sendMessage(C.RED + "it is strongly advised for you to take action. see log for full detail");
                sender().sendMessage(C.RED + "----------------------------------------------------------------");
                sender().sendMessage(C.RED + "Command ran: /iris create");
                sender().sendMessage(C.RED + UtilsSFG.MSGIncompatibleWarnings());
                sender().sendMessage(C.RED + "----------------------------------------------------------------");
            }
            if (unstablemode && !incompatibilities.get("Multiverse-Core")) {
                sender().sendMessage(C.RED + "Your server is experiencing an incompatibility with the Iris plugin.");
                sender().sendMessage(C.RED + "Please rectify this problem to avoid further complications.");
                sender().sendMessage(C.RED + "----------------------------------------------------------------");
                sender().sendMessage(C.RED + "Command ran: /iris create");
                sender().sendMessage(C.RED + UtilsSFG.MSGIncompatibleWarnings());
                sender().sendMessage(C.RED + "----------------------------------------------------------------");
            }
        }
            if (name.equals("iris")) {
                sender().sendMessage(C.RED + "You cannot use the world name \"iris\" for creating worlds as Iris uses this directory for studio worlds.");
                sender().sendMessage(C.RED + "May we suggest the name \"IrisWorld\" instead?");
                return;
            }
            if (name.equals("Benchmark")) {
                sender().sendMessage(C.RED + "You cannot use the world name \"Benchmark\" for creating worlds as Iris uses this directory for Benchmarking Packs.");
                sender().sendMessage(C.RED + "May we suggest the name \"IrisWorld\" instead?");
                return;
            }

        if (new File(Bukkit.getWorldContainer(), name).exists()) {
            sender().sendMessage(C.RED + "That folder already exists!");
            return;
        }

        try {
            worldCreation = true;
            IrisToolbelt.createWorld()
                    .dimension(type.getLoadKey())
                    .name(name)
                    .seed(seed)
                    .sender(sender())
                    .studio(false)
                    .create();
        } catch (Throwable e) {
            sender().sendMessage(C.RED + "Exception raised during creation. See the console for more details.");
            Iris.error("Exception raised during world creation: " + e.getMessage());
            Iris.reportError(e);
            worldCreation = false;
            return;
        }
        worldCreation = false;
        sender().sendMessage(C.GREEN + "Successfully created your world!");
    }

    @Decree(description = "Teleport to another world", aliases = {"tp"}, sync = true)
    public void teleport(
            @Param(description = "World to teleport to")
            World world,
            @Param(description = "Player to teleport", defaultValue = "---", customHandler = NullablePlayerHandler.class)
            Player player
    ) {
        if (player == null && sender().isPlayer())
            player = sender().player();

        final Player target = player;
        if (target == null) {
            sender().sendMessage(C.RED + "The specified player does not exist.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                target.teleport(world.getSpawnLocation());
                new VolmitSender(target).sendMessage(C.GREEN + "You have been teleported to " + world.getName() + ".");
            }
        }.runTask(Iris.instance);
    }

    @Decree(description = "Print version information")
    public void version() {
        sender().sendMessage(C.GREEN + "Iris v" + Iris.instance.getDescription().getVersion() + " by Volmit Software");
    }

    //todo Move to React
    @Decree(description = "Benchmark your server", origin = DecreeOrigin.CONSOLE)
    public void serverbenchmark() throws InterruptedException {
        if(!inProgress) {
            IrisBenchmarking.runBenchmark();
        } else {
            Iris.info(C.RED + "Benchmark already is in progress.");
        }
    }

    /*
    /todo Fix PREGEN
    @Decree(description = "Benchmark a pack", origin = DecreeOrigin.CONSOLE)
    public void packbenchmark(
            @Param(description = "Dimension to benchmark")
            IrisDimension type
    ) throws InterruptedException {

         BenchDimension = type.getLoadKey();

        IrisPackBenchmarking.runBenchmark();
    } */

    /*  /todo Different approach this feels useless atm
    @Decree(description = "Check for instabilities", origin = DecreeOrigin.CONSOLE)
    public void fixunstable() throws InterruptedException {
        if (unstablemode){
            sender().sendMessage(C.RED + "Incompatibilities are posted in console..");

            Iris.info(C.RED + "Your server is experiencing an incompatibility with the Iris plugin.");
            Iris.info(C.RED + "Please rectify this problem to avoid further complications.");
            Iris.info(C.RED + "----------------------------------------------------------------");
            Iris.info(C.RED + "Command ran: /iris fixunstable");
            UtilsSFG.printIncompatibleWarnings();
            Iris.info(C.RED + "----------------------------------------------------------------");
     } else {
            Iris.info(C.BLUE + "Iris is running stable..");
            sender().sendMessage("Iris is running stable..");
        }
    } */

    @Decree(description = "Print world height information", origin = DecreeOrigin.PLAYER)
    public void height() {
        sender().sendMessage(C.GREEN + "" + sender().player().getWorld().getMinHeight() + " to " + sender().player().getWorld().getMaxHeight());
        sender().sendMessage(C.GREEN + "Total Height: " + (sender().player().getWorld().getMaxHeight() - sender().player().getWorld().getMinHeight()));
    }

    @Decree(description = "QOL command to open a overworld studio world.", sync = true)
    public void so() {
        sender().sendMessage(C.GREEN + "Opening studio for the \"Overworld\" pack (seed: 1337)");
        Iris.service(StudioSVC.class).open(sender(), 1337, "overworld");
    }

    @Decree(description = "Remove an Iris world", aliases = {"del", "rm", "delete"}, sync = true)
    public void remove(
            @Param(description = "The world to remove")
            World world,
            @Param(description = "Whether to also remove the folder (if set to false, just does not load the world)", defaultValue = "true")
            boolean delete
    ) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "This is not an Iris world. Iris worlds: " + String.join(", ", Bukkit.getServer().getWorlds().stream().filter(IrisToolbelt::isIrisWorld).map(World::getName).toList()));
            return;
        }
        sender().sendMessage(C.GREEN + "Removing world: " + world.getName());
        try {
            if (IrisToolbelt.removeWorld(world)) {
                sender().sendMessage(C.GREEN + "Successfully removed " + world.getName() + " from bukkit.yml");
            } else {
                sender().sendMessage(C.YELLOW + "Looks like the world was already removed from bukkit.yml");
            }
        } catch (IOException e) {
            sender().sendMessage(C.RED + "Failed to save bukkit.yml because of " + e.getMessage());
            e.printStackTrace();
        }
        IrisToolbelt.evacuate(world, "Deleting world");
        deletingWorld = true;
        Bukkit.unloadWorld(world, false);
        int retries = 12;
        if (delete) {
            if (deleteDirectory(world.getWorldFolder())) {
                sender().sendMessage(C.GREEN + "Successfully removed world folder");
            } else {
                while(true){
                    if (deleteDirectory(world.getWorldFolder())){
                        sender().sendMessage(C.GREEN + "Successfully removed world folder");
                        break;
                    }
                    retries--;
                    if (retries == 0){
                        sender().sendMessage(C.RED + "Failed to remove world folder");
                        break;
                    }
                    J.sleep(3000);
                }
            }
        }
        deletingWorld = false;
    }

    public static boolean deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDirectory(children[i]);
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }

    @Decree(description = "Set aura spins")
    public void aura(
            @Param(description = "The h color value", defaultValue = "-20")
            int h,
            @Param(description = "The s color value", defaultValue = "7")
            int s,
            @Param(description = "The b color value", defaultValue = "8")
            int b
    ) {
        IrisSettings.get().getGeneral().setSpinh(h);
        IrisSettings.get().getGeneral().setSpins(s);
        IrisSettings.get().getGeneral().setSpinb(b);
        IrisSettings.get().forceSave();
        sender().sendMessage("<rainbow>Aura Spins updated to " + h + " " + s + " " + b);
    }

    @Decree(description = "Bitwise calculations")
    public void bitwise(
            @Param(description = "The first value to run calculations on")
            int value1,
            @Param(description = "The operator: | & ^ ≺≺ ≻≻ ％")
            String operator,
            @Param(description = "The second value to run calculations on")
            int value2
    ) {
        Integer v = null;
        switch (operator) {
            case "|" -> v = value1 | value2;
            case "&" -> v = value1 & value2;
            case "^" -> v = value1 ^ value2;
            case "%" -> v = value1 % value2;
            case ">>" -> v = value1 >> value2;
            case "<<" -> v = value1 << value2;
        }
        if (v == null) {
            sender().sendMessage(C.RED + "The operator you entered: (" + operator + ") is invalid!");
            return;
        }
        sender().sendMessage(C.GREEN + "" + value1 + " " + C.GREEN + operator.replaceAll("<", "≺").replaceAll(">", "≻").replaceAll("%", "％") + " " + C.GREEN + value2 + C.GREEN + " returns " + C.GREEN + v);
    }

    @Decree(description = "Toggle debug")
    public void debug(
            @Param(name = "on", description = "Whether or not debug should be on", defaultValue = "other")
            Boolean on
    ) {
        boolean to = on == null ? !IrisSettings.get().getGeneral().isDebug() : on;
        IrisSettings.get().getGeneral().setDebug(to);
        IrisSettings.get().forceSave();
        sender().sendMessage(C.GREEN + "Set debug to: " + to);
    }

    @Decree(description = "Download a project.", aliases = "dl")
    public void download(
            @Param(name = "pack", description = "The pack to download", defaultValue = "overworld", aliases = "project")
            String pack,
            @Param(name = "branch", description = "The branch to download from", defaultValue = "main")
            String branch,
            @Param(name = "trim", description = "Whether or not to download a trimmed version (do not enable when editing)", defaultValue = "false")
            boolean trim,
            @Param(name = "overwrite", description = "Whether or not to overwrite the pack with the downloaded one", aliases = "force", defaultValue = "false")
            boolean overwrite
    ) {
        sender().sendMessage(C.GREEN + "Downloading pack: " + pack + "/" + branch + (trim ? " trimmed" : "") + (overwrite ? " overwriting" : ""));
        if (pack.equals("overworld")) {
            String url = "https://github.com/IrisDimensions/overworld/releases/download/" + Iris.OVERWORLD_TAG + "/overworld.zip";
            Iris.service(StudioSVC.class).downloadRelease(sender(), url, trim, overwrite);
        } else {
            Iris.service(StudioSVC.class).downloadSearch(sender(), "IrisDimensions/" + pack + "/" + branch, trim, overwrite);
        }
    }

    @Decree(description = "Get metrics for your world", aliases = "measure", origin = DecreeOrigin.PLAYER)
    public void metrics() {
        if (!IrisToolbelt.isIrisWorld(world())) {
            sender().sendMessage(C.RED + "You must be in an Iris world");
            return;
        }
        sender().sendMessage(C.GREEN + "Sending metrics...");
        engine().printMetrics(sender());
    }

    @Decree(description = "Reload configuration file (this is also done automatically)")
    public void reload() {
        IrisSettings.invalidate();
        IrisSettings.get();
        sender().sendMessage(C.GREEN + "Hotloaded settings");
    }

    @Decree(description = "Update the pack of a world (UNSAFE!)", name = "^world", aliases = "update-world")
    public void updateWorld(
            @Param(description = "The world to update", contextual = true)
            World world,
            @Param(description = "The pack to install into the world", contextual = true, aliases = "dimension")
            IrisDimension pack,
            @Param(description = "Make sure to make a backup & read the warnings first!", defaultValue = "false", aliases = "c")
            boolean confirm,
            @Param(description = "Should Iris download the pack again for you", defaultValue = "false", name = "fresh-download", aliases = {"fresh", "new"})
            boolean freshDownload
    ) {
        if (!confirm) {
            sender().sendMessage(new String[]{
                    C.RED + "You should always make a backup before using this",
                    C.YELLOW + "Issues caused by this can be, but are not limited to:",
                    C.YELLOW + " - Broken chunks (cut-offs) between old and new chunks (before & after the update)",
                    C.YELLOW + " - Regenerated chunks that do not fit in with the old chunks",
                    C.YELLOW + " - Structures not spawning again when regenerating",
                    C.YELLOW + " - Caves not lining up",
                    C.YELLOW + " - Terrain layers not lining up",
                    C.RED + "Now that you are aware of the risks, and have made a back-up:",
                    C.RED + "/iris ^world " + world.getName() + " " + pack.getLoadKey() + " confirm=true"
            });
            return;
        }

        File folder = world.getWorldFolder();
        folder.mkdirs();

        if (freshDownload) {
            Iris.service(StudioSVC.class).downloadSearch(sender(), pack.getLoadKey(), false, true);
        }

        Iris.service(StudioSVC.class).installIntoWorld(sender(), pack.getLoadKey(), folder);
    }

    @Decree(description = "Unload an Iris World", origin = DecreeOrigin.PLAYER, sync = true)
    public void unloadWorld(
            @Param(description = "The world to unload")
            World world
    ) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "This is not an Iris world. Iris worlds: " + String.join(", ", Bukkit.getServer().getWorlds().stream().filter(IrisToolbelt::isIrisWorld).map(World::getName).toList()));
            return;
        }
        sender().sendMessage(C.GREEN + "Unloading world: " + world.getName());
        try {
            IrisToolbelt.evacuate(world);
            Bukkit.unloadWorld(world, false);
            sender().sendMessage(C.GREEN + "World unloaded successfully.");
        } catch (Exception e) {
            sender().sendMessage(C.RED + "Failed to unload the world: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Decree(description = "Load an Iris World", origin = DecreeOrigin.PLAYER, sync = true, aliases = {"import"})
    public void loadWorld(
            @Param(description = "The name of the world to load")
            String world
    ) {
        World worldloaded = Bukkit.getWorld(world);
        worldNameToCheck = world;
        boolean worldExists = doesWorldExist(worldNameToCheck);
        WorldEngine = world;

        if (!worldExists) {
            sender().sendMessage(C.YELLOW + world + " Doesnt exist on the server.");
            return;
        }
        WorldToLoad = world;
        File BUKKIT_YML = new File("bukkit.yml");
        String pathtodim = world + "\\iris\\pack\\dimensions\\";
        File directory = new File(Bukkit.getWorldContainer(), pathtodim);

        String dimension = null;
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String fileName = file.getName();
                        if (fileName.endsWith(".json")) {
                            dimension = fileName.substring(0, fileName.length() - 5);
                            sender().sendMessage(C.BLUE + "Generator: " + dimension);
                        }
                    }
                }
            }
        } else {
            sender().sendMessage(C.GOLD + world + " is not an iris world.");
            return;
        }
        sender().sendMessage(C.GREEN + "Loading world: " + world);

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(BUKKIT_YML);
        String gen = "Iris:" + dimension;
        ConfigurationSection section = yml.contains("worlds") ? yml.getConfigurationSection("worlds") : yml.createSection("worlds");
        if (!section.contains(world)) {
            section.createSection(world).set("generator", gen);
            try {
                yml.save(BUKKIT_YML);
                Iris.info("Registered \"" + world + "\" in bukkit.yml");
            } catch (IOException e) {
                Iris.error("Failed to update bukkit.yml!");
                e.printStackTrace();
            }
        }
        checkForBukkitWorlds();
        sender().sendMessage(C.GREEN + world + " loaded successfully.");
    }
    @Decree(description = "Evacuate an iris world", origin = DecreeOrigin.PLAYER, sync = true)
    public void evacuate(
            @Param(description = "Evacuate the world")
            World world
    ) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "This is not an Iris world. Iris worlds: " + String.join(", ", Bukkit.getServer().getWorlds().stream().filter(IrisToolbelt::isIrisWorld).map(World::getName).toList()));
            return;
        }
        sender().sendMessage(C.GREEN + "Evacuating world" + world.getName());
        IrisToolbelt.evacuate(world);
    }

    boolean doesWorldExist(String worldName) {
        File worldContainer = Bukkit.getWorldContainer();
        File worldDirectory = new File(worldContainer, worldName);
        return worldDirectory.exists() && worldDirectory.isDirectory();
    }
    private void checkForBukkitWorlds() {
        FileConfiguration fc = new YamlConfiguration();
        try {
            fc.load(new File("bukkit.yml"));
            ConfigurationSection section = fc.getConfigurationSection("worlds");
            if (section == null) {
                return;
            }

            List<String> worldsToLoad = Collections.singletonList(WorldToLoad);

            for (String s : section.getKeys(false)) {
                if (!worldsToLoad.contains(s)) {
                    continue;
                }
                ConfigurationSection entry = section.getConfigurationSection(s);
                if (!entry.contains("generator", true)) {
                    continue;
                }
                String generator = entry.getString("generator");
                if (generator.startsWith("Iris:")) {
                    generator = generator.split("\\Q:\\E")[1];
                } else if (generator.equalsIgnoreCase("Iris")) {
                    generator = IrisSettings.get().getGenerator().getDefaultWorldType();
                } else {
                    continue;
                }
                Iris.info("2 World: %s | Generator: %s", s, generator);
                if (Bukkit.getWorlds().stream().anyMatch(w -> w.getName().equals(s))) {
                    continue;
                }
                Iris.info(C.LIGHT_PURPLE + "Preparing Spawn for " + s + "' using Iris:" + generator + "...");
                new WorldCreator(s)
                        .generator(getDefaultWorldGenerator(s, generator))
                        .environment(IrisData.loadAnyDimension(generator).getEnvironment())
                        .createWorld();
                Iris.info(C.LIGHT_PURPLE + "Loaded " + s + "!");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        Iris.debug("Default World Generator Called for " + worldName + " using ID: " + id);
        if (worldName.equals("test")) {
            try {
                throw new RuntimeException();
            } catch (Throwable e) {
                Iris.info(e.getStackTrace()[1].getClassName());
                if (e.getStackTrace()[1].getClassName().contains("com.onarandombox.MultiverseCore")) {
                    Iris.debug("MVC Test detected, Quick! Send them the dummy!");
                    return new DummyChunkGenerator();
                }
            }
        }
        IrisDimension dim;
        if (id == null || id.isEmpty()) {
            dim = IrisData.loadAnyDimension(IrisSettings.get().getGenerator().getDefaultWorldType());
        } else {
            dim = IrisData.loadAnyDimension(id);
        }
        Iris.debug("Generator ID: " + id + " requested by bukkit/plugin");

        if (dim == null) {
            Iris.warn("Unable to find dimension type " + id + " Looking for online packs...");

            service(StudioSVC.class).downloadSearch(new VolmitSender(Bukkit.getConsoleSender()), id, true);
            dim = IrisData.loadAnyDimension(id);

            if (dim == null) {
                throw new RuntimeException("Can't find dimension " + id + "!");
            } else {
                Iris.info("Resolved missing dimension, proceeding with generation.");
            }
        }
        Iris.debug("Assuming IrisDimension: " + dim.getName());
        IrisWorld w = IrisWorld.builder()
                .name(worldName)
                .seed(1337)
                .environment(dim.getEnvironment())
                .worldFolder(new File(Bukkit.getWorldContainer(), worldName))
                .minHeight(dim.getMinHeight())
                .maxHeight(dim.getMaxHeight())
                .build();
        Iris.debug("Generator Config: " + w.toString());
        File ff = new File(w.worldFolder(), "iris/pack");
        if (!ff.exists() || ff.listFiles().length == 0) {
            ff.mkdirs();
            service(StudioSVC.class).installIntoWorld(sender, dim.getLoadKey(), ff.getParentFile());
        }
        return new BukkitChunkGenerator(w, false, ff, dim.getLoadKey());
    }
}
