package com.ic.banking.glass.poc2_sendmoney;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;

import java.util.List;

public class SendMoneyActivity extends Activity {

    private static final String TAG = SendMoneyActivity.class.getSimpleName();

    private static final String CURRENCY_SYMBOL = "U$S";
    private static final String TRANSACTION_MESSAGE = "Send to %s,\nfor %s";

    private static final int MAX_AMOUNT = 10000;
    private static final int MIN_AMOUNT = 0;

    private SensorManager sensorManager;
    private SensorEventListener sensorEventListener;
    private GestureDetector gestureDetector;
    private String receiverName;
    private float currentAngle;
    private Thread angleThread;
    private boolean activityVisible;

    private SensorEventListener createSensorEventListener() {
        Log.i(TAG, "Creating SensorEventListener...");
        SensorEventListener sensorEventListener = new SensorEventListener() {

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }

            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                    float angle = computeOrientation(event);
                    currentAngle = angle;
                }
            }

            private float computeOrientation(SensorEvent event) {
                float angle = (float) -Math.atan(event.values[0]
                        / Math.sqrt(event.values[1] * event.values[1] + event.values[2] * event.values[2]));

                return angle;
            }
        };
        return sensorEventListener;
    }

    private GestureDetector createGestureDetector(Context context) {
        Log.i(TAG, "Creating GestureDetector...");
        GestureDetector gestureDetector = new GestureDetector(context);
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                if (gesture == Gesture.TWO_TAP) {
                    Toast.makeText(getApplicationContext(), "DOBLE", Toast.LENGTH_SHORT).show();
                    return true;
                }
                else if (gesture == Gesture.TAP) {
                    if (receiverName == null) {
                        finish();
                    }
                    else {
                        openOptionsMenu();
                    }
                }
                return false;
            }
        });
        gestureDetector.setScrollListener(new GestureDetector.ScrollListener() {
            @Override
            public boolean onScroll(float displacement, float delta, float velocity) {
                TextView tvInfo = (TextView) findViewById(R.id.transfer_amount);

                String oldAmount = tvInfo.getText().toString();
                String newAmount = calculateNewAmountFromSwipe(displacement, oldAmount);

                tvInfo.setText(newAmount);

                return true;
            }
        });
        return gestureDetector;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (gestureDetector != null) {
            return gestureDetector.onMotionEvent(event);
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.gestureDetector = createGestureDetector(this);

        asignReceiverNameFromVoiceResult();

        if (this.receiverName == null) {
            setContentView(R.layout.error_message);
        }
        else {
            setContentView(R.layout.send_money);
            createAndRegisterSensors();
            setInitialValues();
        }
    }

    private void startAngleThread() {
        this.angleThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (activityVisible) {
                    try {
                        Log.i(TAG, "Angle: " + currentAngle);
                        Thread.sleep(500);
                        handleCurrentAngle();
                    }
                    catch (InterruptedException e) {
                        Log.e(TAG, "Exception on AngleThread.Sleep");
                        break;
                    }
                }
            }
        });
        this.angleThread.start();
    }

    private void handleCurrentAngle() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tvInfo = (TextView) findViewById(R.id.transfer_amount);

                String oldAmount = tvInfo.getText().toString();
                String newAmount = calculateNewAmountFromSensor(oldAmount);

                tvInfo.setText(newAmount);
            }
        });
    }

    private void createAndRegisterSensors() {
        this.sensorEventListener = createSensorEventListener();
        this.sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        this.sensorManager.registerListener(sensorEventListener,
                this.sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void setInitialValues() {
        this.setTextViewText(R.id.transfer_info, buildTransactionMessage());
        this.setTextViewText(R.id.transfer_amount, String.valueOf(MIN_AMOUNT));
    }

    @Override
    public void onResume() {
        this.activityVisible = true;
        startAngleThread();
        super.onResume();
    }

    private void asignReceiverNameFromVoiceResult() {
        try {
            List<String> voiceResults = getIntent().getExtras().getStringArrayList(RecognizerIntent.EXTRA_RESULTS);
            this.receiverName = voiceResults.get(0);
            Log.i(TAG, "Name:" + this.receiverName);
        }
        catch (Exception e) {
            Log.e(TAG, "No voice result in intent");
        }
    }

    protected void onPause() {
        super.onPause();
        if (this.sensorEventListener != null) {
            this.sensorManager.unregisterListener(this.sensorEventListener);
        }
        this.activityVisible = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.send_money, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_finish) {
            finish();
            return true;
        }
        if (id == R.id.action_transfer) {
            Toast.makeText(getApplicationContext(), "Money transfered", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String buildTransactionMessage() {
        return String.format(TRANSACTION_MESSAGE, this.receiverName, CURRENCY_SYMBOL);
    }

    private static String calculateNewAmountFromSwipe(float displacement, String amountString) {
        int amount = Integer.parseInt(amountString);

        if (displacement > 500) {
            amount += 1000;
        }
        else if (displacement > 250) {
            amount += 500;
        }
        else if (displacement > 50) {
            amount += 100;
        }
        else if (displacement < -500) {
            amount -= 1000;
        }
        else if (displacement < -250) {
            amount -= 500;
        }
        else if (displacement < -50) {
            amount -= 100;
        }

        return String.valueOf(getValidAmount(amount));
    }

    private String calculateNewAmountFromSensor(String amountString) {
        float angle = this.currentAngle;
        int amount = Integer.parseInt(amountString);

        if (angle > 0.25) {
            amount += 1000;
        }
        else if (angle > 0.2) {
            amount += 500;
        }
        else if (angle > 0.1) {
            amount += 100;
        }
        else if (angle < -0.25) {
            amount -= 1000;
        }
        else if (angle < -0.2) {
            amount -= 500;
        }
        else if (angle < -0.1) {
            amount -= 100;
        }

        return String.valueOf(getValidAmount(amount));
    }

    private static int getValidAmount(int amount) {
        if (amount < MIN_AMOUNT) {
            return MIN_AMOUNT;
        }
        if (amount > MAX_AMOUNT) {
            return MAX_AMOUNT;
        }
        return amount;
    }

    private void setTextViewText(int textViewId, String text) {
        TextView tv = (TextView) findViewById(textViewId);
        tv.setText(text);
    }
}
