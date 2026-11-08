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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;

import java.util.List;
import java.util.Objects;

/**
 * The service providers of one pack's modules, as {@code PackWorld.services}, {@code ValidationContext.services} and
 * {@code ToolPack.services} answer them. Asking a pack whose modules fail to load throws its {@link PackModulesException}.
 */
@FunctionalInterface
public interface PackServices {
    /**
     * Every provider of {@code service}, in module id order; empty when no module provides it.
     *
     * @throws IllegalArgumentException when {@code service} is not an interface marked as a service
     */
    <S> List<S> providers(Class<S> service);

    /** The providers of the modules of the pack {@code data} holds, looked up whenever they are asked for. */
    static PackServices of(IrisData data) {
        Objects.requireNonNull(data, "data");
        return new PackServices() {
            @Override
            public <S> List<S> providers(Class<S> service) {
                return data.getModules().pack().catalog().services(service);
            }
        };
    }
}
