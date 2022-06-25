package com.volmit.iris.util.uniques;

import java.io.File;

public class U {
    public static void main(String[] a)
    {
        UniqueRenderer r = new UniqueRenderer("helloworld", 2560     , 1440);

        r.writeCollectionFrames(new File("collection"), 1, 1024);

        for(int i = 1; i <= 1024; i++)
        {
            r.writeAnimation(new File("collection/animation"), 2, 0, 32, 1);
        }


        System.exit(0);
    }
}
