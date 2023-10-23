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

package com.volmit.iris.util.scheduling.jobs;

import com.volmit.iris.util.network.DL;
import com.volmit.iris.util.network.DownloadMonitor;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class DownloadJob implements Job {
    private final DL.Download download;
    private int tw;
    private int cw;

    public DownloadJob(String url, File destination) throws MalformedURLException {
        tw = 1;
        cw = 0;
        download = new DL.Download(new URL(url), destination, DL.DownloadFlag.CALCULATE_SIZE);
        download.monitor(new DownloadMonitor() {
            @Override
            public void onUpdate(DL.DownloadState state, double progress, long elapsed, long estimated, long bps, long iobps, long size, long downloaded, long buffer, double bufferuse) {
                if (size == -1) {
                    tw = 1;
                } else {
                    tw = (int) (size / 100);
                    cw = (int) (downloaded / 100);
                }
            }
        });
    }

    @Override
    public String getName() {
        return "Downloading";
    }

    @Override
    public void execute() {
        try {
            download.start();
            while (download.isState(DL.DownloadState.DOWNLOADING)) {
                download.downloadChunk();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        cw = tw;
    }

    @Override
    public void completeWork() {

    }

    @Override
    public int getTotalWork() {
        return tw;
    }

    @Override
    public int getWorkCompleted() {
        return cw;
    }
}
