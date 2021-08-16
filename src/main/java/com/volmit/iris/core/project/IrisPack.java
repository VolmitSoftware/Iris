/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.project;

import com.volmit.iris.Iris;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.util.data.IrisPackRepository;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.Data;

import java.io.File;
import java.net.MalformedURLException;

@Data
public class IrisPack {
    private final File folder;

    public IrisPack(File folder)
    {
        this.folder = folder;
    }

    public void delete()
    {
        IO.delete(folder);
        folder.delete();
    }

    public static IrisPack from(VolmitSender sender, String url) throws MalformedURLException {
        return from(sender, IrisPackRepository.from(url));
    }

    public static IrisPack from(VolmitSender sender, IrisPackRepository repo) throws MalformedURLException {
        String name = repo.getRepo();
        String url = repo.toURL();
        repo.install(sender);

        return new IrisPack(Iris.instance.getDataFolder(StudioSVC.WORKSPACE_NAME, repo.getRepo()));
    }
}
