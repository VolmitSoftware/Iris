package com.volmit.iris.util;

import java.io.File;

public class ReactiveFolder
{
    private File folder;
    private Consumer3<KList<File>, KList<File>, KList<File>> hotload;
    private FolderWatcher fw;

    public ReactiveFolder(File folder, Consumer3<KList<File>, KList<File>, KList<File>> hotload)
    {
        this.folder = folder;
        this.hotload = hotload;
        this.fw = new FolderWatcher(folder);
        fw.checkModified();
    }

    public void checkIgnore()
    {
        fw = new FolderWatcher(folder);
    }

    public void check()
    {
        boolean modified = false;

        if(fw.checkModified())
        {
            for(File i : fw.getCreated())
            {
                if(i.getName().endsWith(".iob") || i.getName().endsWith(".json"))
                {
                    modified = true;
                    break;
                }
            }

            if(!modified)
            {
                for(File i : fw.getChanged())
                {
                    if(i.getName().endsWith(".iob") || i.getName().endsWith(".json"))
                    {
                        modified = true;
                        break;
                    }
                }
            }

            if(!modified)
            {
                for(File i : fw.getDeleted())
                {
                    if(i.getName().endsWith(".iob") || i.getName().endsWith(".json"))
                    {
                        modified = true;
                        break;
                    }
                }
            }
        }

        if(modified)
        {
            hotload.accept(fw.getCreated(), fw.getChanged(), fw.getDeleted());
        }

        fw.checkModified();
    }
}
