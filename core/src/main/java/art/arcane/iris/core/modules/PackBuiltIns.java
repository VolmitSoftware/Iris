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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The built-ins jar this build of Iris carries, which Iris never loads itself: a pack loads the copy in its own modules
 * folder, so an Iris update does not change the modules a pack, or a world's copy of it, generates with.
 */
public final class PackBuiltIns {
    private static final String RESOURCE = "/" + MissingBuiltInModules.JAR_NAME;

    private PackBuiltIns() {
    }

    /**
     * Copies the jar into the modules folder of {@code packFolder}, which has none yet.
     *
     * @return false when this build carries no built-ins jar, as in Iris's own tests
     */
    public static boolean copyInto(Path packFolder) throws IOException {
        try (InputStream jar = PackBuiltIns.class.getResourceAsStream(RESOURCE)) {
            if (jar == null) {
                return false;
            }
            Path modules = Files.createDirectories(packFolder.resolve(PackModuleSet.MODULES_FOLDER));
            Files.copy(jar, modules.resolve(MissingBuiltInModules.JAR_NAME));
            return true;
        }
    }
}
