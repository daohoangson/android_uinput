package com.daohoangson.android.uinput;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Locale;

import android.os.Environment;
import android.util.Log;

public class ShellScripts {

	private static final String TAG = "ShellScripts";

	public static long execScreencap(ByteBuffer bb) {
		File dir = Environment.getExternalStorageDirectory();
		File file = new File(dir, "screen.png");

		int execResult = execAndWait(String.format(Locale.US,
				"screencap -p %s", file.getPath()));

		if (execResult == 0) {
			try {
				if (file.length() > bb.capacity()) {
					throw new IOException("ByteBuffer is too small");
				}

				FileInputStream fis = new FileInputStream(file);
				int bytesRead = 0;

				if (bb.hasArray()) {
					// try to read directly to ByteBuffer array if possible
					bytesRead = fis.read(bb.array());
				} else {
					byte bytes[] = new byte[(int) file.length()];
					bytesRead = fis.read(bytes);
					bb.put(bytes, 0, bytesRead);
				}

				if (bytesRead > 0) {
					Log.v(TAG, String.format("execScreencap ok: bytesRead=%d",
							bytesRead));
					return bytesRead;
				} else {
					Log.e(TAG, "execScreencap -> read() = 0");
				}
			} catch (FileNotFoundException e) {
				Log.e(TAG, "execScreencap -> new FileInputStream() failed", e);
			} catch (IOException e) {
				Log.e(TAG, "execScreencap -> read() failed", e);
			}
		} else {
			Log.e(TAG, "grabScreenShot -> exec() failed");
		}

		return 0;
	}

	private static int execAndWait(String cmd) {
		int exitValue = -1;

		try {
			long timestampStart = new Date().getTime();
			Log.v(TAG, String.format("execAndWait -> exec(%s)", cmd));

			Process p = Runtime.getRuntime().exec("su");

			DataOutputStream stdin = new DataOutputStream(p.getOutputStream());
			stdin.writeBytes(cmd);
			stdin.writeBytes("\nexit\n");
			stdin.flush();

			exitValue = p.waitFor();
			if (exitValue != 0) {
				InputStream stdout = p.getInputStream();
				InputStream stderr = p.getErrorStream();

				String line = null;
				InputStreamReader reader = new InputStreamReader(stdout);
				BufferedReader bufferedReader = new BufferedReader(reader);

				while ((line = bufferedReader.readLine()) != null) {
					Log.w(TAG, String.format("stdout > %s", line));
				}

				reader = new InputStreamReader(stderr);
				bufferedReader = new BufferedReader(reader);
				while ((line = bufferedReader.readLine()) != null) {
					Log.w(TAG, String.format("stderr > %s", line));
				}
			}

			long timestampFinish = new Date().getTime();
			Log.v(TAG, String.format("execAndWait() = %d (%d ms)", exitValue,
					timestampFinish - timestampStart));
		} catch (IOException e) {
			Log.e(TAG, "execAndWait -> exec() failed", e);
		} catch (InterruptedException e) {
			Log.e(TAG, "execAndWait -> waitFor() failed", e);
		}

		return exitValue;
	}
}
