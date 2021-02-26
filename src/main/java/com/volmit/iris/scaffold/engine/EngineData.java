package com.volmit.iris.scaffold.engine;

import com.google.gson.Gson;
import com.volmit.iris.object.IrisPosition;
import com.volmit.iris.util.IO;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Data
public class EngineData {
    private String dimension;
    private String lastVersion;
    private List<IrisPosition> strongholdPositions;

    public void save(File f)
    {
        try {
            f.getParentFile().mkdirs();
            IO.writeAll(f, new Gson().toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static EngineData load(File f)
    {
        try
        {
            f.getParentFile().mkdirs();
            return new Gson().fromJson(IO.readAll(f), EngineData.class);
        }

        catch(Throwable e)
        {

        }

        return new EngineData();
    }
}
