/*
 *  Pedometer - Android App
 *  Copyright (C) 2009 Levente Bagi
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package stepmonitor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import name.bagi.levente.pedometer.R;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class Pedometer extends Activity {
	private static final String TAG = "Pedometer";
	private SharedPreferences mSettings;
	private PedometerSettings mPedometerSettings;
	private Utils mUtils;

	private TextView mStepValueView;
	private TextView mPaceValueView;
	private TextView mDistanceValueView;
	private TextView mSpeedValueView;
	private TextView mCaloriesValueView;
	TextView mDesiredPaceView;
	private int mStepValue;
	private int mPaceValue;
	private float mDistanceValue;
	private float mSpeedValue;
	private int mCaloriesValue;
	private float mDesiredPaceOrSpeed;
	private int mMaintain;
	private boolean mIsMetric;
	private float mMaintainInc;
	private boolean mQuitting = false; // Set when user selected Quit from menu,
										// can be used by onPause, onStop,
										// onDestroy

	/**
	 * True, when service is running.
	 */
	private boolean mIsRunning;

	private Timer mTimer;
	private long totalSteps;
	private static String uriAPI = "http://api.heclouds.com/devices/3784982/datapoints?type=3";
	private static HttpPost httpRequest;

	private void setTimerTask() {
		mTimer.schedule(new TimerTask() {
			@Override
			public void run() {

				httpRequest = new HttpPost(uriAPI);
				httpRequest
						.addHeader("api-key", "L6nSIaWrod1n6wzZEgnw=I6VfDg=");
				// params.add(new BasicNameValuePair("steps",""+msg.arg1));
				try {
					JSONObject obj = new JSONObject();
					obj.put("steps", totalSteps + "");
					httpRequest.setEntity(new StringEntity(obj.toString()));
					
					HttpResponse httpResponse = new DefaultHttpClient()
							.execute(httpRequest);
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				} catch (ClientProtocolException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}, 1000, 1000/* 表示1000毫秒之後，每隔1000毫秒執行一次 */);
	}

	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mTimer = new Timer();
		// start timer task
		setTimerTask();
		Log.i(TAG, "[ACTIVITY] onCreate");
		super.onCreate(savedInstanceState);

		mStepValue = 0;
		mPaceValue = 0;

		setContentView(R.layout.main);

		mUtils = Utils.getInstance();
	}

	@Override
	protected void onStart() {
		Log.i(TAG, "[ACTIVITY] onStart");
		super.onStart();
	}

	@Override
	protected void onResume() {
		Log.i(TAG, "[ACTIVITY] onResume");
		super.onResume();

		mSettings = PreferenceManager.getDefaultSharedPreferences(this);
		mPedometerSettings = new PedometerSettings(mSettings);

		mUtils.setSpeak(mSettings.getBoolean("speak", false));

		// Read from preferences if the service was running on the last onPause
		mIsRunning = mPedometerSettings.isServiceRunning();

		// Start the service if this is considered to be an application start
		// (last onPause was long ago)
		if (!mIsRunning && mPedometerSettings.isNewStart()) {
			startStepService();
			bindStepService();
		} else if (mIsRunning) {
			bindStepService();
		}

		mPedometerSettings.clearServiceRunning();

		mStepValueView = (TextView) findViewById(R.id.step_value);
		mPaceValueView = (TextView) findViewById(R.id.pace_value);
		mDesiredPaceView = (TextView) findViewById(R.id.desired_pace_value);

		mIsMetric = mPedometerSettings.isMetric();

		mMaintain = mPedometerSettings.getMaintainOption();
		((LinearLayout) this.findViewById(R.id.desired_pace_control))
				.setVisibility(mMaintain != PedometerSettings.M_NONE ? View.VISIBLE
						: View.GONE);
		if (mMaintain == PedometerSettings.M_PACE) {
			mMaintainInc = 5f;
			mDesiredPaceOrSpeed = (float) mPedometerSettings.getDesiredPace();
		} else if (mMaintain == PedometerSettings.M_SPEED) {
			mDesiredPaceOrSpeed = mPedometerSettings.getDesiredSpeed();
			mMaintainInc = 0.1f;
		}
		Button button1 = (Button) findViewById(R.id.button_desired_pace_lower);
		button1.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				mDesiredPaceOrSpeed -= mMaintainInc;
				mDesiredPaceOrSpeed = Math.round(mDesiredPaceOrSpeed * 10) / 10f;
				displayDesiredPaceOrSpeed();
				setDesiredPaceOrSpeed(mDesiredPaceOrSpeed);
			}
		});
		Button button2 = (Button) findViewById(R.id.button_desired_pace_raise);
		button2.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				mDesiredPaceOrSpeed += mMaintainInc;
				mDesiredPaceOrSpeed = Math.round(mDesiredPaceOrSpeed * 10) / 10f;
				displayDesiredPaceOrSpeed();
				setDesiredPaceOrSpeed(mDesiredPaceOrSpeed);
			}
		});
		if (mMaintain != PedometerSettings.M_NONE) {
			((TextView) findViewById(R.id.desired_pace_label))
					.setText(mMaintain == PedometerSettings.M_PACE ? R.string.desired_pace
							: R.string.desired_speed);
		}

		displayDesiredPaceOrSpeed();
	}

	private void displayDesiredPaceOrSpeed() {
		if (mMaintain == PedometerSettings.M_PACE) {
			mDesiredPaceView.setText("" + (int) mDesiredPaceOrSpeed);
		} else {
			mDesiredPaceView.setText("" + mDesiredPaceOrSpeed);
		}
	}

	@Override
	protected void onPause() {
		Log.i(TAG, "[ACTIVITY] onPause");
		if (mIsRunning) {
			unbindStepService();
		}
		if (mQuitting) {
			mPedometerSettings.saveServiceRunningWithNullTimestamp(mIsRunning);
		} else {
			mPedometerSettings.saveServiceRunningWithTimestamp(mIsRunning);
		}

		super.onPause();
		savePaceSetting();
	}

	@Override
	protected void onStop() {
		Log.i(TAG, "[ACTIVITY] onStop");
		super.onStop();
	}

	protected void onDestroy() {
		Log.i(TAG, "[ACTIVITY] onDestroy");
		super.onDestroy();
	}

	protected void onRestart() {
		Log.i(TAG, "[ACTIVITY] onRestart");
		super.onDestroy();
	}

	private void setDesiredPaceOrSpeed(float desiredPaceOrSpeed) {
		if (mService != null) {
			if (mMaintain == PedometerSettings.M_PACE) {
				mService.setDesiredPace((int) desiredPaceOrSpeed);
			}
		}
	}

	private void savePaceSetting() {
		mPedometerSettings.savePaceOrSpeedSetting(mMaintain,
				mDesiredPaceOrSpeed);
	}

	private StepService mService;

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = ((StepService.StepBinder) service).getService();

			mService.registerCallback(mCallback);
			mService.reloadSettings();

		}

		public void onServiceDisconnected(ComponentName className) {
			mService = null;
		}
	};

	private void startStepService() {
		if (!mIsRunning) {
			Log.i(TAG, "[SERVICE] Start");
			mIsRunning = true;
			startService(new Intent(Pedometer.this, StepService.class));
		}
	}

	private void bindStepService() {
		Log.i(TAG, "[SERVICE] Bind");
		bindService(new Intent(Pedometer.this, StepService.class), mConnection,
				Context.BIND_AUTO_CREATE + Context.BIND_DEBUG_UNBIND);
	}

	private void unbindStepService() {
		Log.i(TAG, "[SERVICE] Unbind");
		unbindService(mConnection);
	}

	private void stopStepService() {
		Log.i(TAG, "[SERVICE] Stop");
		if (mService != null) {
			Log.i(TAG, "[SERVICE] stopService");
			stopService(new Intent(Pedometer.this, StepService.class));
		}
		mIsRunning = false;
	}

	private void resetValues(boolean updateDisplay) {
		if (mService != null && mIsRunning) {
			mService.resetValues();
		} else {
			mStepValueView.setText("0");
			mPaceValueView.setText("0");
			mDistanceValueView.setText("0");
			mSpeedValueView.setText("0");
			mCaloriesValueView.setText("0");
			SharedPreferences state = getSharedPreferences("state", 0);
			SharedPreferences.Editor stateEditor = state.edit();
			if (updateDisplay) {
				stateEditor.putInt("steps", 0);
				stateEditor.putInt("pace", 0);
				stateEditor.putFloat("distance", 0);
				stateEditor.putFloat("speed", 0);
				stateEditor.putFloat("calories", 0);
				stateEditor.commit();
			}
		}
	}

	private static final int MENU_SETTINGS = 8;
	private static final int MENU_QUIT = 9;

	private static final int MENU_PAUSE = 1;
	private static final int MENU_RESUME = 2;
	private static final int MENU_RESET = 3;

	/* Creates the menu items */
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		if (mIsRunning) {
			menu.add(0, MENU_PAUSE, 0, R.string.pause)
					.setIcon(android.R.drawable.ic_media_pause)
					.setShortcut('1', 'p');
		} else {
			menu.add(0, MENU_RESUME, 0, R.string.resume)
					.setIcon(android.R.drawable.ic_media_play)
					.setShortcut('1', 'p');
		}
		menu.add(0, MENU_RESET, 0, R.string.reset)
				.setIcon(android.R.drawable.ic_menu_close_clear_cancel)
				.setShortcut('2', 'r');
		menu.add(0, MENU_SETTINGS, 0, R.string.settings)
				.setIcon(android.R.drawable.ic_menu_preferences)
				.setShortcut('8', 's')
				.setIntent(new Intent(this, Settings.class));
		menu.add(0, MENU_QUIT, 0, R.string.quit)
				.setIcon(android.R.drawable.ic_lock_power_off)
				.setShortcut('9', 'q');
		return true;
	}

	/* Handles item selections */
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_PAUSE:
			unbindStepService();
			stopStepService();
			return true;
		case MENU_RESUME:
			startStepService();
			bindStepService();
			return true;
		case MENU_RESET:
			resetValues(true);
			return true;
		case MENU_QUIT:
			resetValues(false);
			unbindStepService();
			stopStepService();
			mQuitting = true;
			finish();
			return true;
		}
		return false;
	}

	// TODO: unite all into 1 type of message
	private StepService.ICallback mCallback = new StepService.ICallback() {
		public void stepsChanged(int value) {
			mHandler.sendMessage(mHandler.obtainMessage(STEPS_MSG, value, 0));
		}

		public void paceChanged(int value) {
			mHandler.sendMessage(mHandler.obtainMessage(PACE_MSG, value, 0));
		}

		public void distanceChanged(float value) {
			mHandler.sendMessage(mHandler.obtainMessage(DISTANCE_MSG,
					(int) (value * 1000), 0));
		}

		public void speedChanged(float value) {
			mHandler.sendMessage(mHandler.obtainMessage(SPEED_MSG,
					(int) (value * 1000), 0));
		}

		public void caloriesChanged(float value) {
			mHandler.sendMessage(mHandler.obtainMessage(CALORIES_MSG,
					(int) (value), 0));
		}
	};

	private static final int STEPS_MSG = 1;
	private static final int PACE_MSG = 2;
	private static final int DISTANCE_MSG = 3;
	private static final int SPEED_MSG = 4;
	private static final int CALORIES_MSG = 5;

	private Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case STEPS_MSG:
				mStepValue = (int) msg.arg1;

				totalSteps = (int) msg.arg1;

				mStepValueView.setText("" + mStepValue);
				break;
			case PACE_MSG:
				httpRequest = new HttpPost(uriAPI);
				httpRequest
						.addHeader("api-key", "L6nSIaWrod1n6wzZEgnw=I6VfDg=");
				try {
					JSONObject obj = new JSONObject();
					obj.put("frequency", msg.arg1 + "");
					httpRequest.setEntity(new StringEntity(obj.toString()));
					HttpResponse httpResponse = new DefaultHttpClient()
							.execute(httpRequest);
					Log.i("response", httpResponse + "");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ClientProtocolException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				mPaceValue = msg.arg1;
				if (mPaceValue <= 0) {
					mPaceValueView.setText("0");
				} else {
					mPaceValueView.setText("" + (int) mPaceValue);
				}
				break;
			case DISTANCE_MSG:
				mDistanceValue = ((int) msg.arg1) / 1000f;
				if (mDistanceValue <= 0) {
					mDistanceValueView.setText("0");
				} else {
					mDistanceValueView
							.setText(("" + (mDistanceValue + 0.000001f))
									.substring(0, 5));
				}
				break;
			case SPEED_MSG:
				mSpeedValue = ((int) msg.arg1) / 1000f;
				if (mSpeedValue <= 0) {
					mSpeedValueView.setText("0");
				} else {
					mSpeedValueView.setText(("" + (mSpeedValue + 0.000001f))
							.substring(0, 4));
				}
				break;
			case CALORIES_MSG:
				mCaloriesValue = msg.arg1;
				if (mCaloriesValue <= 0) {
					mCaloriesValueView.setText("0");
				} else {
					mCaloriesValueView.setText("" + (int) mCaloriesValue);
				}
				break;
			default:
				super.handleMessage(msg);
			}
		}

	};

}