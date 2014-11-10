package org.fruct.oss.ikm;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class DataService extends org.fruct.oss.mapcontent.content.DataService {
	private IBinder binder = new Binder();

	@Override
    public IBinder onBind(Intent intent) {
		return binder;
    }

	public class Binder extends android.os.Binder {
		public Service getService() {
			return DataService.this;
		}
	}
}
