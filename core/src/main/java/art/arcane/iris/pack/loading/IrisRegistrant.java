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

package art.arcane.iris.pack.loading;

import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.validation.ContentGate;
import com.google.gson.GsonBuilder;
import art.arcane.iris.spi.IrisLogging;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.awt.Desktop;
import java.io.File;

@Data
public abstract class IrisRegistrant {
    @EqualsAndHashCode.Exclude
    private transient IrisData loader;

    private transient String loadKey;

    private transient File loadFile;
    /** Version-content gate verdict; excluded registrants must be filtered out of every pool that could pick them. */
    @EqualsAndHashCode.Exclude
    private transient CompatStatus compat = CompatStatus.OK;

    /**
     * Version-content gate hook, called once per load from {@code IrisData.preprocessObject} before the registrant is
     * cached. The default runs the gate's annotated-field walker; subclasses whose exclusion also depends on their pools
     * (spawners, loot tables, regions, the dimension, jigsaw pieces) override, call {@code super}, and combine.
     */
    public CompatStatus evaluateCompat(ContentGate gate) {
        return gate.evaluate(this);
    }

    public boolean isCompatExcluded() {
        CompatStatus status = compat;
        return status != null && status.excluded();
    }

    public abstract String getFolderName();

    public abstract String getTypeName();

    public void registerTypeAdapters(GsonBuilder builder) {

    }

    public File openInVSCode() {
        try {
            Desktop.getDesktop().open(getLoadFile());
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }

        return getLoadFile();
    }
}
