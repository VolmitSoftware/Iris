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

    public void check()
    {
        if(fw.checkModified())
        {
            hotload.accept(fw.getCreated(), fw.getChanged(), fw.getDeleted());
        }
    }
}
