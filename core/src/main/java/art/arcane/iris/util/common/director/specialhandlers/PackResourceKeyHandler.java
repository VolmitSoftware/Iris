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

package art.arcane.iris.util.common.director.specialhandlers;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.PackModulesException;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.util.common.director.DirectorParameterHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import com.volmit.resource.ResourceId;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The keys of one kind of pack resources a module declares, named by the kind's id, offered to a command parameter: the
 * open pack's, then those of the packs in the packs folder. A subclass names the kind and what a command calls one of
 * its resources; Iris needs no class of the module for it.
 */
public abstract class PackResourceKeyHandler implements DirectorParameterHandler<String> {
    private final ResourceId kind;
    private final String noun;

    /**
     * @param kind the kind's id, such as {@code iris:spawn/entity}
     * @param noun what the kind's resources are called in the error for a key that no pack has, such as {@code Entity}
     */
    protected PackResourceKeyHandler(ResourceId kind, String noun) {
        this.kind = kind;
        this.noun = noun;
    }

    @Override
    public KList<String> getPossibilities() {
        Set<String> keys = new LinkedHashSet<>();
        IrisData activeData = data();
        if (activeData != null) {
            addKeys(keys, activeData);
        }
        for (File pack : PackDirectoryResolver.listVisiblePackDirectories(
                IrisPlatforms.get().packsFolder())) {
            addKeys(keys, IrisData.get(pack));
        }
        return new KList<>(keys);
    }

    @Override
    public String toString(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String parse(String input, boolean force) throws DirectorParsingException {
        for (String option : getPossibilities(input)) {
            if (option.equalsIgnoreCase(input)) {
                return option;
            }
        }
        throw new DirectorParsingException("Unable to find " + noun + " \"" + input + "\"");
    }

    @Override
    public boolean supports(Class<?> type) {
        return type == String.class;
    }

    /** A pack whose modules fail to load has no resources to offer; its failure was reported when it loaded. */
    private void addKeys(Set<String> keys, IrisData data) {
        try {
            data.getPackResources().ids(kind).forEach(id -> keys.add(id.path()));
        } catch (PackModulesException ignored) {
        }
    }
}
