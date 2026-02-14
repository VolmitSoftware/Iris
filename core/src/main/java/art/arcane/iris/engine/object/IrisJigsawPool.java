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

package art.arcane.iris.engine.object;

import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.iris.util.plugin.VolmitSender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor

@Desc("Represents a structure piece pool")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisJigsawPool extends IrisRegistrant {
    @RegistryListResource(IrisJigsawPiece.class)
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("A list of structure piece pools")
    private KList<String> pieces = new KList<>();

    @Override
    public String getFolderName() {
        return "jigsaw-pools";
    }

    @Override
    public String getTypeName() {
        return "Jigsaw Pool";
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
