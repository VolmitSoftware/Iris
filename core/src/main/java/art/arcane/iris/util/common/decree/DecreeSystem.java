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

package art.arcane.iris.util.decree;

import art.arcane.volmlib.util.decree.DecreeSystemSupport;
import art.arcane.iris.Iris;
import art.arcane.volmlib.util.collection.KList;
public final class DecreeSystem {
    public static final KList<DecreeParameterHandler<?>> handlers = Iris.initialize("art.arcane.iris.util.decree.handlers", null).convert((i) -> (DecreeParameterHandler<?>) i);

    private DecreeSystem() {
    }

    /**
     * Get the handler for the specified type
     *
     * @param type The type to handle
     * @return The corresponding {@link DecreeParameterHandler}, or null
     */
    public static DecreeParameterHandler<?> getHandler(Class<?> type) {
        DecreeParameterHandler<?> handler = DecreeSystemSupport.getHandler(handlers, type, (h, t) -> h.supports(t));
        if (handler != null) {
            return handler;
        }

        Iris.error("Unhandled type in Decree Parameter: " + type.getName() + ". This is bad!");
        return null;
    }
}
