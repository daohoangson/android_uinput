package com.daohoangson.android.uinput;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.graphics.Point;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import fi.iki.elonen.nanohttpd.NanoHTTPD;

public class ServiceUinput extends Service {

	public final static String INTENT_OPEN_ACTION = "com.daohoangson.android.uinput.ServiceUinput.OPEN";
	public final static String INTENT_CLOSE_ACTION = "com.daohoangson.android.uinput.ServiceUinput.CLOSE";

	private final static String TAG = "ServiceUinput";

	private boolean mIsOpened = false;
	private boolean mFlagSkipNativeGrabScreenshot = false;
	private int mScreenWidth = 0;
	private int mScreenHeight = 0;
	private ByteBuffer mScreenFb = null;
	private ByteBuffer mNormalFb = null;
	private ExecutorService mExecutors = null;
	private HttpServer mHttpServer = null;
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

	public boolean requestPointer(final int x, final int y,
			final String btnTouch, final String btnLeft) {
		if (!mIsOpened) {
			return false;
		}

		mExecutors.execute(new Runnable() {
			@Override
			public void run() {
				boolean setResult = NativeMethods.pointerSet(x, y);
				Log.v(TAG,
						String.format(
								"requestPointer -> NativeMethods.pointerSet(%d, %d) = %s",
								x, y, setResult));

				if ("down".equals(btnTouch)) {
					boolean touchResult = NativeMethods
							.keyDown(NativeMethods.BTN_TOUCH);
					Log.v(TAG, String.format(
							"requestPointer -> NativeMethods.keyDown(%d) = %s",
							NativeMethods.BTN_TOUCH, touchResult));
				} else if ("up".equals(btnTouch)) {
					boolean touchResult = NativeMethods
							.keyUp(NativeMethods.BTN_TOUCH);
					Log.v(TAG, String.format(
							"requestPointer -> NativeMethods.keyUp(%d) = %s",
							NativeMethods.BTN_TOUCH, touchResult));
				}

				if ("down".equals(btnLeft)) {
					boolean touchResult = NativeMethods
							.keyDown(NativeMethods.BTN_LEFT);
					Log.v(TAG, String.format(
							"requestPointer -> NativeMethods.keyDown(%d) = %s",
							NativeMethods.BTN_LEFT, touchResult));
				} else if ("up".equals(btnLeft)) {
					boolean touchResult = NativeMethods
							.keyUp(NativeMethods.BTN_LEFT);
					Log.v(TAG, String.format(
							"requestPointer -> NativeMethods.keyUp(%d) = %s",
							NativeMethods.BTN_LEFT, touchResult));
				}
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

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private void getScreenSize17(Display display) {
		Point size = new Point();
		display.getRealSize(size);
		mScreenWidth = size.x;
		mScreenHeight = size.y;
		Log.v(TAG, String.format("getScreenSize v17 ok: w=%d, h=%d",
				mScreenWidth, mScreenHeight));
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	private void getScreenSize13(Display display) {
		Point size = new Point();
		display.getSize(size);
		mScreenWidth = size.x;
		mScreenHeight = size.y;
		Log.v(TAG, String.format("getScreenSize v13 ok: w=%d, h=%d",
				mScreenWidth, mScreenHeight));
	}

	@SuppressWarnings("deprecation")
	private void getScreenSize1(Display display) {
		mScreenWidth = display.getWidth();
		mScreenHeight = display.getHeight();
		Log.v(TAG, String.format("getScreenSize v1 ok: w=%d, h=%d",
				mScreenWidth, mScreenHeight));
	}

	private synchronized void open() {
		if (mIsOpened) {
			// nothing to do here
			return;
		}

		WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			getScreenSize17(display);
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
			getScreenSize13(display);
		} else {
			getScreenSize1(display);
		}

		// width*height*3 should be more than enough to store png screenshot
		// TODO: move this block of code to constructor
		mScreenFb = ByteBuffer.allocateDirect(mScreenWidth * mScreenHeight * 3);
		mScreenFb.mark();
		if (!mScreenFb.hasArray()) {
			mNormalFb = ByteBuffer.allocate(mScreenFb.capacity());
			mNormalFb.mark();
		}

		mIsOpened = NativeMethods.open(true, mScreenWidth, mScreenHeight);
		Log.v(TAG, String.format("open -> NativeMethods.open(%d,%d) = %s",
				mScreenWidth, mScreenHeight, mIsOpened));

		// initialize variables
		mFlagSkipNativeGrabScreenshot = false;

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

	private synchronized long grabScreenShot() {
		long size = 0;

		if (!mFlagSkipNativeGrabScreenshot) {
			mScreenFb.reset();
			size = NativeMethods.grabScreenShot(mScreenFb);
			Log.v(TAG, String.format("grabScreenShot -> "
					+ "NativeMethods.grabScreenShot() = %d", size));
		}

		if (size < 10000) {
			// png data less than 10KB?
			// try using shell scripts
			mScreenFb.reset();
			size = ShellScripts.execScreencap(mScreenFb);
			Log.v(TAG, String.format("grabScreenShot -> "
					+ "ShellScripts.execScreencap() = %d", size));

			// also mark the flag to skip native method next time
			mFlagSkipNativeGrabScreenshot = true;
		}

		return size;
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
			mFileMapping.put("/interactive", R.raw.interactive);
			mFileMapping.put("/js/uinput.js", R.raw.uinput);
			mFileMapping.put("/js/keycode.js", R.raw.keycode);
			mFileMapping.put("/launcher.png", R.drawable.ic_launcher);

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
			} else if ("/pointer".equals(uri)) {
				if ("post".equalsIgnoreCase(method)) {
					boolean pointerResult = servePointer(header, params, files);
					if (pointerResult) {
						return new Response(HTTP_OK, MIME_PLAINTEXT, HTTP_OK);
					} else {
						return new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
								HTTP_FORBIDDEN);
					}
				}
			} else if ("/screen.png".equals(uri)) {
				String ifModSince = header
						.getProperty("HTTP_IF_MODIFIED_SINCE");
				if (ifModSince != null && !ifModSince.isEmpty()) {
					Response lastMod = new Response(HTTP_NOTMODIFIED,
							MIME_PLAINTEXT, HTTP_NOTMODIFIED);
					lastMod.addHeader("Last-Modified", ifModSince);
					return lastMod;
				}

				long pngSize = grabScreenShot();
				if (pngSize > 0) {
					byte[] array = null;

					if (mNormalFb == null) {
						array = mScreenFb.array();
					} else {
						// mNormalFb is required because
						// directly allocated byte buffer doesn't support
						// ByteBuffer.array prior to ICS
						mNormalFb.reset();
						mNormalFb.put(mScreenFb);
						array = mNormalFb.array();
					}

					Response response = new Response(HTTP_OK, "image/png",
							new ByteArrayInputStream(array, 0, (int) pngSize));
					response.addHeader("Cache-Control",
							"private, max-age=10800, pre-check=10800");
					response.addHeader("Pragma", "private");

					SimpleDateFormat rfc822 = new SimpleDateFormat(
							"EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z", Locale.US);
					Date date = new Date();
					Calendar calendar = Calendar.getInstance();
					calendar.setTime(date);
					calendar.add(Calendar.YEAR, 1);
					Date dateFuture = calendar.getTime();
					response.addHeader("Expires", rfc822.format(dateFuture));

					return response;
				} else {
					return new Response(HTTP_INTERNALERROR, MIME_PLAINTEXT,
							HTTP_INTERNALERROR);
				}
			} else if ("/favicon.ico".equals(uri)) {
				Response res = new Response(HTTP_REDIRECT, MIME_PLAINTEXT, "");
				res.addHeader("Location", "/launcher.png");
				
				return res;
			} else if ("/device_info.json".equals(uri)) {
				JSONObject jsonObject = new JSONObject();
				buildDeviceInfo(jsonObject);

				String response = String.format("var deviceInfo = %s;",
						jsonObject.toString());
				return new Response(HTTP_OK, "application/json", response);
			}

			return super.serve(uri, method, header, params, files);
		}

		@Override
		public Response serveFile(String uri, Properties header, File homeDir,
				boolean allowDirectoryListing) {
			Response res = null;

			if (mFileMapping.containsKey(uri)) {
				int resId = mFileMapping.get(uri);
				String mimeType = MIME_HTML;

				if (uri.endsWith(".js")) {
					mimeType = "text/javascript";
				} else if (uri.endsWith(".png")) {
					mimeType = "image/png";
				}

				try {
					InputStream is = getResources().openRawResource(resId);
					res = new Response(HTTP_OK, mimeType, is);
				} catch (NotFoundException e) {
					Log.e(TAG, String.format("serveFile -> "
							+ "openRawResource(%d) failed", resId), e);
				}
			}

			if (res == null) {
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

		private boolean servePointer(Properties header, Properties params,
				Properties files) {
			int x = parseParamInt(params, "x");
			int y = parseParamInt(params, "y");
			String btnTouch = params.getProperty("btn_touch");
			String btnLeft = params.getProperty("btn_left");

			return requestPointer(x, y, btnTouch, btnLeft);
		}

		private void buildDeviceInfo(JSONObject jsonObject) {
			try {
				jsonObject.put("screenWidth", mScreenWidth);
				jsonObject.put("screenHeight", mScreenHeight);
			} catch (JSONException e) {
				// ignore
			}
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
	}
}
