package com.volmit.iris.server.util;

import com.volmit.iris.server.IrisConnection;

public interface ConnectionHolder {

    IrisConnection getConnection();
}
