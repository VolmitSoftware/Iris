package com.volmit.iris.gen.v2.scaffold.hunk.io;

import com.volmit.iris.gen.v2.scaffold.hunk.Hunk;
import com.volmit.iris.util.CompoundTag;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.NBTInputStream;
import com.volmit.iris.util.NBTOutputStream;
import lombok.Data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

@Data
public class HunkRegion
{
    private final File folder;
    private CompoundTag compound;
    private final int x;
    private final int z;

    public HunkRegion(File folder, int x, int z, CompoundTag compound) {
        this.compound = compound;
        this.folder = folder;
        this.x = x;
        this.z = z;
        folder.mkdirs();
    }

    public HunkRegion(File folder, int x, int z) {
        this(folder, x, z, new CompoundTag(x + "." + z, new KMap<>()));
        File f = getFile();

        if(f.exists())
        {
            try
            {
                NBTInputStream in = new NBTInputStream(new FileInputStream(f));
                compound = (CompoundTag) in.readTag();
                in.close();
            }

            catch(Throwable e)
            {

            }
        }
    }

    public File getFile()
    {
        return new File(folder, x + "." + z + ".dat");
    }

    public void save() throws IOException
    {
        synchronized (compound)
        {
            File f = getFile();
            FileOutputStream fos = new FileOutputStream(f);
            NBTOutputStream out = new NBTOutputStream(fos);
            out.writeTag(compound);
            out.close();
        }
    }
}
