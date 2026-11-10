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

package art.arcane.iris.core.pack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Usage: {@code PackWeightConverterCli [--dry-run] <pack folder>...} */
public final class PackWeightConverterCli {
    private static final int EXIT_USAGE = 2;
    private static final int EXIT_REFUSED = 1;

    private PackWeightConverterCli() {
    }

    public static void main(String[] arguments) throws IOException {
        boolean dryRun = false;
        List<Path> packs = new ArrayList<>();
        for (String argument : arguments) {
            if (argument.equals("--dry-run")) {
                dryRun = true;
            } else {
                packs.add(Path.of(argument));
            }
        }

        if (packs.isEmpty()) {
            System.err.println("Usage: PackWeightConverterCli [--dry-run] <pack folder>...");
            System.exit(EXIT_USAGE);
        }

        for (Path folder : packs) {
            try {
                PackWeightConverter.Result result = PackWeightConverter.convert(folder, dryRun);
                result.changedFiles().forEach(System.out::println);
                result.warnings().forEach(warning -> System.out.println("warning: " + warning));
                System.out.println(folder + ": " + result.changedFiles().size() + " files " + (dryRun ? "would change" : "changed"));
            } catch (PackWeightConverter.ConversionException refusal) {
                System.err.println(folder + ": " + refusal.getMessage());
                System.exit(EXIT_REFUSED);
            }
        }
    }
}
