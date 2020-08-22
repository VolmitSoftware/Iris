package com.volmit.iris.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface Writable
{
	public void write(DataOutputStream o) throws IOException;

	public void read(DataInputStream i) throws IOException;
}
