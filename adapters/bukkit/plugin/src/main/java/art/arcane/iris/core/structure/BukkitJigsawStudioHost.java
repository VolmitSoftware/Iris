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

package art.arcane.iris.core.structure;

import art.arcane.iris.Iris;
import art.arcane.iris.core.pack.BrokenPackException;
import art.arcane.iris.core.runtime.StudioOpenCoordinator;
import art.arcane.iris.core.service.JigsawStudioService;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.core.structure.tooling.JigsawStudioHost;
import art.arcane.iris.core.tooling.IrisToolPack;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.util.common.plugin.VolmitSender;
import com.volmit.tooling.Sender;
import com.volmit.tooling.ToolPack;
import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** The Jigsaw Studio of this plugin, and the Bukkit server services the jigsaw commands need around it. */
public final class BukkitJigsawStudioHost implements JigsawStudioHost {
    static final StudioOpenCoordinator.StudioOpenKind STUDIO_OPEN_KIND = StudioOpenCoordinator.StudioOpenKind.TOOL;
    private static final String UNKNOWN_CLOSE_FAILURE = "unknown failure";

    @Override
    public JigsawStudioService studio() {
        return Iris.jigsawStudio();
    }

    @Override
    public String packKey(ToolPack pack) {
        return IrisToolPack.dataOf(pack).getDataFolder().getName();
    }

    @Override
    public Optional<Throwable> reportAdmissionFailure(Sender sender, String packKey) {
        VolmitSender messages = sender.extension(CommandSender.class).map(VolmitSender::new).orElseGet(BukkitPlatform::console);
        return Optional.ofNullable(StudioSVC.reportPackAdmissionFailure(messages, packKey, STUDIO_OPEN_KIND));
    }

    @Override
    public boolean isExpectedOpenDenial(Throwable failure) {
        return unwrapCompletionFailure(failure) instanceof BrokenPackException;
    }

    @Override
    public CompletableFuture<Optional<Throwable>> closeStudio() {
        return Iris.service(StudioSVC.class).close().thenApply(result -> {
            Throwable failure = result == null ? new IllegalStateException(UNKNOWN_CLOSE_FAILURE) : result.failureCause();
            return Optional.ofNullable(failure);
        });
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
