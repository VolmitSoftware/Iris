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

package art.arcane.iris.core.tooling;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.iris.spi.IrisPlatforms;

import java.io.File;
import java.util.List;
import java.util.Optional;

/** The packs a line may name with {@code pack=<name>}. */
public interface PackDirectory {
    /** The pack folder named {@code name}; empty when there is none. */
    Optional<ServedPack> find(String name);

    /** The names of the pack folders, sorted. */
    List<String> names();

    /** The packs below the packs folder of the bound platform; the folder the exports go to is no pack. */
    static PackDirectory ofBoundPlatform() {
        return new PackDirectory() {
            @Override
            public Optional<ServedPack> find(String name) {
                File folder = PackDirectoryResolver.resolveExisting(IrisPlatforms.get().packsFolderNoCreate(), name);
                if (folder == null || folder.getName().equals(IrisToolPack.EXPORTS_FOLDER)) {
                    return Optional.empty();
                }
                return Optional.of(new IrisServedPack(IrisData.get(folder)));
            }

            @Override
            public List<String> names() {
                return PackDirectoryResolver.listVisiblePackDirectories(IrisPlatforms.get().packsFolderNoCreate()).stream()
                        .map(File::getName)
                        .filter(name -> !name.equals(IrisToolPack.EXPORTS_FOLDER))
                        .toList();
            }
        };
    }
}
