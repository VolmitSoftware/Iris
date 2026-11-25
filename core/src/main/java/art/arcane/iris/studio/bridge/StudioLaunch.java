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

package art.arcane.iris.studio.bridge;

import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.volmlib.util.localization.TextKey;

/** How a request to show the studio window ended; every outcome but {@link #OPENED} leaves the bridge link as the only way in. */
public enum StudioLaunch {
    OPENED(null),
    DOWNLOAD_OFF(RuntimeUiMessages.STUDIO_APP_DOWNLOAD_OFF),
    NO_DISPLAY(RuntimeUiMessages.STUDIO_APP_NO_DISPLAY),
    DOWNLOADING(RuntimeUiMessages.STUDIO_APP_DOWNLOADING),
    UNAVAILABLE(RuntimeUiMessages.STUDIO_APP_UNAVAILABLE),
    NOT_BUILT(RuntimeUiMessages.STUDIO_APP_NOT_BUILT),
    FAILED_TO_START(RuntimeUiMessages.STUDIO_APP_FAILED_TO_START);

    private final TextKey failureMessage;

    StudioLaunch(TextKey failureMessage) {
        this.failureMessage = failureMessage;
    }

    public boolean opened() {
        return this == OPENED;
    }

    public TextKey failureMessage() {
        return failureMessage;
    }
}
