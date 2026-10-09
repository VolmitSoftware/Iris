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

package art.arcane.iris.util.common.director.handlers;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.entity.IrisEntity;
import art.arcane.iris.engine.spawn.SpawnResources;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.common.director.specialhandlers.RegistrantHandler;

public class EntityHandler extends RegistrantHandler<IrisEntity> {

    public EntityHandler() {
        super(IrisEntity.class, false);
    }

    @Override
    protected KList<IrisEntity> loadAll(IrisData data) {
        KList<IrisEntity> entities = new KList<>();
        for (String key : SpawnResources.entityKeys(data)) {
            IrisEntity entity = SpawnResources.entity(data, key);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }

    @Override
    public String getRandomDefault() {
        return "entity";
    }
}
