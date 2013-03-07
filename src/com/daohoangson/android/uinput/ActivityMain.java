package com.daohoangson.android.uinput;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class ActivityMain extends Activity implements OnClickListener {

	private static final String TAG = "ActivityMain";

	protected Button btnOpen;
	protected Button btnClose;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		btnOpen = (Button) findViewById(R.id.btn_open);
		btnOpen.setOnClickListener(this);

		btnClose = (Button) findViewById(R.id.btn_close);
		btnClose.setOnClickListener(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public void onClick(View view) {
		if (view == btnOpen) {
			Intent intentOpen = new Intent(ServiceUinput.INTENT_OPEN_ACTION);

			Log.v(TAG, String.format("btnOpen.onClick -> startService(%s)",
					intentOpen.getAction()));
			startService(intentOpen);
		} else if (view == btnClose) {
			Intent intentClose = new Intent(ServiceUinput.INTENT_CLOSE_ACTION);
			
			Log.v(TAG, String.format("btnClose.onClick -> startService(%s)",
					intentClose.getAction()));
			startService(intentClose);
		}
	}

}
