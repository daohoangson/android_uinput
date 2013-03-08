package com.daohoangson.android.uinput;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.util.ByteArrayBuffer;

import fi.iki.elonen.nanohttpd.NanoHTTPD;
import fi.iki.elonen.nanohttpd.NanoHTTPD.Response;

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

	private boolean mIsOpened = false;
	private ExecutorService mExecutors;
	private HttpServer mHttpServer;
	private NotificationCompat.Builder mBuilderForeground = null;

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

	public boolean requestKeyPress(int key, boolean shift, boolean alt) {
		if (!mIsOpened) {
			return false;
		}

		final int keyFinal = key;
		final boolean shiftFinal = shift;
		final boolean altFinal = alt;

		mExecutors.execute(new Runnable() {
			@Override
			public void run() {
				boolean keyPressResult = NativeMethods.keyPress(keyFinal,
						shiftFinal, altFinal);
				Log.v(TAG,
						String.format(
								"requestKeyPress(%d,%s,%s) -> NativeMethods.keyPress() = %s",
								keyFinal, shiftFinal, altFinal, keyPressResult));
			}
		});

		return true;
	}

	public boolean requestPointerTab(int x, int y) {
		if (!mIsOpened) {
			return false;
		}

		final int xFinal = x;
		final int yFinal = y;

		mExecutors.execute(new Runnable() {
			@Override
			public void run() {
				boolean pointerTapResult = NativeMethods.pointerTap(xFinal,
						yFinal);
				Log.v(TAG,
						String.format(
								"requestPointerTab(%d,%d) -> NativeMethods.pointerTap() = %s",
								xFinal, yFinal, pointerTapResult));
			}
		});

		return true;
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
		Log.v(TAG,
				String.format("open -> NativeMethods.open() = %s", mIsOpened));

		if (mIsOpened) {
			try {
				mHttpServer = new HttpServer(Constant.HTTP_PORT);
			} catch (IOException e) {
				Log.e(TAG, "open -> new HttpServer() failed", e);
			}

			setupForeground();
		}
	}

	private synchronized void close() {
		if (!mIsOpened) {
			// nothing to do here
			return;
		}

		boolean closeResult = NativeMethods.close();
		Log.v(TAG, String.format("close -> NativeMethods.close() = %s",
				closeResult));

		if (mHttpServer != null) {
			mHttpServer.stop();
		}

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
		startForeground(Constant.NOTIFICATION_ID_SERVICE_UINPUT_FOREGROUND,
				notification);

		Log.v(TAG, "setupForeground ok");
	}

	private void disableForeground() {
		stopForeground(true);

		Log.v(TAG, "disableForeground ok");
	}

	class HttpServer extends NanoHTTPD {

		private final static String TAG = "HttpServer";

		Map<String, Integer> mFileMapping = new HashMap<String, Integer>();

		HttpServer(int port) throws IOException {
			super(port, new File(Constant.HTTP_WWW_ROOT));

			mFileMapping.put("/", R.raw.index);
			mFileMapping.put("/js/uinput.js", R.raw.uinput);

			Log.v(TAG, String.format("new HttpServer(%d) ok", port));
		}

		@Override
		public void stop() {
			super.stop();
			Log.v(TAG, "stop ok");
		}

		@Override
		public Response serve(String uri, String method, Properties header,
				Properties params, Properties files) {
			if ("/key/press".equals(uri)) {
				if ("post".equalsIgnoreCase(method)) {
					boolean keyPressResult = serveKeyPress(header, params,
							files);
					if (keyPressResult) {
						return new Response(HTTP_OK, MIME_PLAINTEXT, HTTP_OK);
					} else {
						return new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
								HTTP_FORBIDDEN);
					}
				}
			} else if ("/pointer/tap".equals(uri)) {
				if ("post".equalsIgnoreCase(method)) {
					boolean pointerTapResult = servePointerTap(header, params,
							files);
					if (pointerTapResult) {
						return new Response(HTTP_OK, MIME_PLAINTEXT, HTTP_OK);
					} else {
						return new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
								HTTP_FORBIDDEN);
					}
				}
			}

			return super.serve(uri, method, header, params, files);
		}

		@Override
		public Response serveFile(String uri, Properties header, File homeDir,
				boolean allowDirectoryListing) {
			Response res = null;

			if (mFileMapping.containsKey(uri)) {
				String fileHtml = getRaw(mFileMapping.get(uri));
				res = new Response(HTTP_OK, MIME_HTML, fileHtml);
			} else {
				// no file found
				res = new Response(HTTP_NOTFOUND, MIME_PLAINTEXT, HTTP_NOTFOUND);
			}

			return res;
		}

		private boolean serveKeyPress(Properties header, Properties params,
				Properties files) {
			int key = parseParamInt(params, "key");
			if (key == 0) {
				return false;
			}

			boolean shift = parseParamInt(params, "shift") == 1;
			boolean alt = parseParamInt(params, "alt") == 1;

			return requestKeyPress(key, shift, alt);
		}

		private boolean servePointerTap(Properties header, Properties params,
				Properties files) {
			int x = parseParamInt(params, "x");
			int y = parseParamInt(params, "y");
			
			return requestPointerTab(x, y);
		}

		private int parseParamInt(Properties params, String name) {
			Object value = params.get(name);
			int valueInt = 0;

			if (value != null) {
				try {
					valueInt = Integer.parseInt(String.valueOf(value));
					Log.v(TAG, String.format("parseParamInt(%s) = %d", name,
							valueInt));
				} catch (NumberFormatException nfe) {
					Log.w(TAG, String.format(
							"parseParamInt(%s) -> Integer.parseInt(%s) failed",
							name, value), nfe);
				}
			} else {
				Log.w(TAG, String.format("parseParamInt(%s) param not found",
						name));
			}

			return valueInt;
		}

		private String getRaw(int resId) {
			InputStream is = getResources().openRawResource(resId);
			BufferedInputStream bis = new BufferedInputStream(is);
			ByteArrayBuffer baf = new ByteArrayBuffer(50);

			int current = 0;
			try {
				while ((current = bis.read()) != -1) {
					baf.append((byte) current);
				}
			} catch (IOException e) {
				Log.e(TAG, "getRaw -> bis.read() failed", e);
			}

			return new String(baf.toByteArray());
		}
	}
}
