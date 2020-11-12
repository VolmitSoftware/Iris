package com.volmit.iris.object;

import com.volmit.iris.manager.IrisDataManager;
import lombok.Data;

import java.io.File;

@Data
public class IrisRegistrant
{
	private transient IrisDataManager loader;

	private transient String loadKey;

	private transient File loadFile;
}
