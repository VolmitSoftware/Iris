package com.volmit.iris.object;

import java.io.File;

import lombok.Data;

@Data
public class IrisRegistrant
{
	private transient String loadKey;

	private transient File loadFile;
}
