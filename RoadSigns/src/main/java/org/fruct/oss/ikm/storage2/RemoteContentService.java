package org.fruct.oss.ikm.storage2;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.fruct.oss.ikm.utils.bind.BindHelperBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteContentService extends Service {
	private static final Logger log = LoggerFactory.getLogger(RemoteContentService.class);

	private final LocalBinder binder = new LocalBinder();

	public class LocalBinder extends BindHelperBinder {
		@Override
		public RemoteContentService getService() {
			return RemoteContentService.this;
		}
	}

	public RemoteContentService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		log.info("CREATED");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		log.info("DESTROYED");
	}
}
