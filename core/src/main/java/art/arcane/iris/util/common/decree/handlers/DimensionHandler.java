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

package art.arcane.iris.util.decree.handlers;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import art.arcane.iris.util.decree.specialhandlers.RegistrantHandler;

import java.util.Locale;

public class DimensionHandler extends RegistrantHandler<IrisDimension> {
    public DimensionHandler() {
        super(IrisDimension.class, false);
    }

    @Override
    public IrisDimension parse(String in, boolean force) throws DirectorParsingException {
        String key = in.trim();
        if (key.equalsIgnoreCase("default")) {
            key = IrisSettings.get().getGenerator().getDefaultWorldType();
        }

        try {
            return super.parse(key, force);
        } catch (DirectorParsingException ignored) {
            String normalized = key.toLowerCase(Locale.ROOT);
            IrisDimension resolved = IrisToolbelt.getDimension(normalized);
            if (resolved != null) {
                return resolved;
            }

            if (!normalized.equals(key)) {
                resolved = IrisToolbelt.getDimension(key);
                if (resolved != null) {
                    return resolved;
                }
            }

            throw ignored;
        }
    }

    @Override
    public KList<IrisDimension> getPossibilities(String input) {
        KList<IrisDimension> possibilities = super.getPossibilities();
        String normalizedInput = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (normalizedInput.isEmpty()) {
            return possibilities;
        }

        KList<IrisDimension> filtered = new KList<>();
        for (IrisDimension dimension : possibilities) {
            if (dimension != null && dimension.getLoadKey() != null) {
                String key = dimension.getLoadKey().toLowerCase(Locale.ROOT);
                if (key.startsWith(normalizedInput)) {
                    filtered.add(dimension);
                }
            }
        }

        return filtered;
    }

    @Override
    public String getRandomDefault() {
        return "dimension";
    }
}
