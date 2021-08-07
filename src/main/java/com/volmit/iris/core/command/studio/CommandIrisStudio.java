/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.Command;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

public class CommandIrisStudio extends MortarCommand {

    @Command
    private CommandIrisStudioBeautify beautify;

    @Command
    private CommandIrisStudioClose close;

    @Command
    private CommandIrisStudioConvert convert;

    @Command
    private CommandIrisStudioCreate create;

    @Command
    private CommandIrisStudioEditBiome editbiome;

    @Command
    private CommandIrisStudioExplorer exp;

    @Command
    private CommandIrisStudioExplorerGenerator generator;

    @Command
    private CommandIrisStudioGoto got0;

    @Command
    private CommandIrisStudioHotload hotload;

    @Command
    private CommandIrisStudioLoot loot;

    @Command
    private CommandIrisStudioMap map;

    @Command
    private CommandIrisStudioOpen open;

    @Command
    private CommandIrisStudioPackage pkg;

    @Command
    private CommandIrisStudioProfile profile;

    @Command
    private CommandIrisStudioSummon summon;

    @Command
    private CommandIrisStudioTPStudio tps;

    @Command
    private CommandIrisStudioUpdate update;

    public CommandIrisStudio() {
        super("studio", "std", "s");
        requiresPermission(Iris.perm.studio);
        setCategory("Studio");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
            return true;
        }

        sender.sendMessage("Iris Studio Commands:");
        printHelp(sender);
        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[subcommand]";
    }
}
