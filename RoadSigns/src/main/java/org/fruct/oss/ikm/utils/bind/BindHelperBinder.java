package org.fruct.oss.ikm.utils.bind;

import android.app.Service;
import android.os.Binder;

public abstract class BindHelperBinder extends Binder {
	public abstract Service getService();
}
