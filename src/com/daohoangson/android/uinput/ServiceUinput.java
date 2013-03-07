package com.daohoangson.android.uinput;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class ServiceUinput extends Service {

	public final static String INTENT_OPEN_ACTION = "com.daohoangson.android.uinput.ServiceUinput.OPEN";
	public final static String INTENT_CLOSE_ACTION = "com.daohoangson.android.uinput.ServiceUinput.CLOSE";

	private final static String TAG = "ServiceUinput";

	protected NotificationCompat.Builder mBuilderForeground = null;
	protected boolean mIsOpened = false;
	private final ExecutorService mExecutors;

	public ServiceUinput() {
		mExecutors = Executors.newFixedThreadPool(1);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final Intent intentFinal = intent;

		mExecutors.execute(new Runnable() {
			@Override
			public void run() {
				handleIntent(intentFinal);
			}
		});

		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		// always close when this service is destroyed
		Log.v(TAG, "onDestroy -> close()");
		close();
	}

	private void handleIntent(Intent intent) {
		if (intent != null) {
			String action = intent.getAction();

			if (INTENT_OPEN_ACTION.equals(action)) {
				open();
			} else if (INTENT_CLOSE_ACTION.equals(action)) {
				close();
			}
		}
	}

	private synchronized void open() {
		if (mIsOpened) {
			// nothing to do here
			return;
		}

		mIsOpened = NativeMethods.open();
		Log.d(TAG,
				String.format("open -> NativeMethods.open() = %s", mIsOpened));

		if (mIsOpened) {
			setupForeground();
		}
	}

	private synchronized void close() {
		if (!mIsOpened) {
			// nothing to do here
			return;
		}

		boolean closeResult = NativeMethods.close();
		Log.d(TAG, String.format("close -> NativeMethods.close() = %s",
				closeResult));

		mIsOpened = false;
		disableForeground();
	}

	private void setupForeground() {
		if (mBuilderForeground == null) {
			mBuilderForeground = new NotificationCompat.Builder(this)
					.setSmallIcon(android.R.drawable.ic_menu_info_details)
					.setContentTitle(getText(R.string.app_name))
					.setContentText(getText(R.string.service_uinput_is_running));
			Intent resultIntent = new Intent(this, ActivityMain.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
					resultIntent, 0);
			mBuilderForeground.setContentIntent(pendingIntent);
		}

		Notification notification = mBuilderForeground.build();
		Log.d(TAG, String.format("notification = %s", notification));

		Log.v(TAG, "setupForeground -> startForeground()");
		startForeground(Constant.NOTIFICATION_ID_SERVICE_UINPUT_FOREGROUND,
				notification);
	}

	private void disableForeground() {
		Log.v(TAG, "disableForeground -> stopForeground()");
		stopForeground(true);
	}
}
