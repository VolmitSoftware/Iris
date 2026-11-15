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

package art.arcane.iris.studio.bridge;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.spi.IrisPlatforms;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeSet;

/** What a setting naming a block or a resource of the pack may be typed as, for the studio to suggest while it is typed. */
final class StudioSuggestions {
    private StudioSuggestions() {
    }

    /**
     * The entries of the registry {@code referenceName} names, sorted; blocks are the pack's own and the platform's.
     *
     * @throws NoSuchElementException when there is no such reference
     */
    static List<String> ofReference(IrisData data, String referenceName) {
        if (!SettingsTypes.BLOCK_REFERENCE.equals(referenceName)) {
            throw new NoSuchElementException("No reference named " + referenceName);
        }
        TreeSet<String> blocks = new TreeSet<>(Arrays.asList(data.getBlockLoader().getPossibleKeys()));
        blocks.addAll(IrisPlatforms.get().registries().blockTypeKeys());
        return List.copyOf(blocks);
    }

    /**
     * The keys of the pack's resources in the folder {@code folder}, sorted.
     *
     * @throws NoSuchElementException when the pack has no resources of that folder
     */
    static List<String> ofResource(IrisData data, String folder) {
        ResourceLoader<?> loader = data.getLoaders().values().stream()
                .filter(candidate -> folder.equals(candidate.getFolderName()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("The pack has no " + folder));
        return Arrays.stream(loader.getPossibleKeys()).sorted().distinct().toList();
    }
}
