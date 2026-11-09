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

package art.arcane.iris.core.structure.tooling;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.service.JigsawStudioService;
import com.volmit.tooling.Sender;
import com.volmit.tooling.ToolPack;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * What the jigsaw commands need from the server that runs the Jigsaw Studio and that no api type offers: the studio
 * itself, the pack's data, whether a pack may open a studio, and the server's one open studio. The adapter that runs a
 * studio implements it; a platform without one has none, and the commands then say so.
 */
public interface JigsawStudioHost {
    /** The studio, which keeps the sessions of its players. */
    JigsawStudioService studio();

    /** The pack's data, for the resources the studio still reads through it. */
    IrisData dataOf(ToolPack pack);

    /** The key the studio files {@code pack} under. */
    String packKey(ToolPack pack);

    /** Tells {@code sender} why {@code packKey} may not open a studio; empty, and silent, when it may. */
    Optional<Throwable> reportAdmissionFailure(Sender sender, String packKey);

    /** Whether {@code failure} is the pack's validation denying the studio, which is the pack author's to fix and not an error of Iris. */
    boolean isExpectedOpenDenial(Throwable failure);

    /** Closes the studio the server has open; completes with why it failed, or empty when it closed. */
    CompletableFuture<Optional<Throwable>> closeStudio();
}
