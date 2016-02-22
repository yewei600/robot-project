package com.wei.bluetoothapp;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.os.Message;
import android.os.Handler;


//////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////
/*
    THE BLUETOOTH CLIENT APP FOR THE ROBOT PROJECT
    features:
    1) robot direction control
    2) turn up robot speed
    3) robot mode select  (i.e. use IR to avoid obstable mode)

    phone tilt code : http://blog.samsandberg.com/2015/05/10/tilt-for-android/
    from http://stackoverflow.com/questions/10397809/how-to-detect-phone-tilt


    //Todo:
    1) fix UI
    2) what to do when tilt and click a go backward/forward button simultaneously?
       - just tilt, then just do a direction change.
    3) more handshaking between Arduino and  phone?
    4) different layout for landscape & portrait. ****************** PERHAPS DIFFERENT ACTIVITY....
      - portrait:  maybe option to enter different robot mode (go in a circle mode)
                   once select hand controller mode, switch to landscape layout

    5) the logic for increasing motor speed   (also need to consider control logic)
       - hold down on the button to go forward or backward . release button, car stops
       - error checking... (hold down both go forward and backward)

    6) STILL UTILIZE THE IR: warn that there's objects ahead!!!!


 */
//////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////


public class MainActivity extends Activity implements SensorEventListener {

    private static final String TAG = "BluetoothActivity";
    private final String address= "98:D3:31:90:63:E4";  //98:D3:31:90:63:E4
    BluetoothThread btThread;
    Handler writeHandler;

    //accelerometer stuff
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private long startTimestamp = 0;
    private float[] mGravity;
    private float[] mGeomagnetic;
    private TextView tiltDataTV;
    private String orientStrOld;
    private String orientStr;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //start off in the portrait layout
        setContentView(R.layout.activity_main);

        Button b = (Button)findViewById(R.id.writeButton);
        b.setEnabled(false);

        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        tiltDataTV = (TextView)findViewById(R.id.tiltInfo);

        orientStrOld="Flat";
    }

    @Override
    public void onResume() {
        super.onResume();

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(btThread !=null){
            btThread.interrupt();
            btThread=null;
        }

        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.values==null){
            Log.w(TAG,"event.values is null");
            return;
        }

        int sensorType = event.sensor.getType();
        switch(sensorType){
            case Sensor.TYPE_ACCELEROMETER:
                mGravity=event.values;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mGeomagnetic=event.values;
                break;
            default:
                Log.w(TAG,"GARBAGE sensor data from "+sensorType);
        }

        if (mGravity == null) {
            Log.w(TAG, "mGravity is null");
            return;
        }
        if (mGeomagnetic == null) {
            Log.w(TAG, "mGeomagnetic is null");
            return;
        }

        //Tilt determining logic
        float R[]=new float[9];
        if(!SensorManager.getRotationMatrix(R,null,mGravity,mGeomagnetic)){
            Log.w(TAG,"getRotationMatrix() failed :(");
            return;
        }

        float orientation[]=new float[3];
        SensorManager.getOrientation(R, orientation);
        // Orientation contains: azimuth, pitch and roll - we'll use roll
        float pitch=orientation[1];
        int pitchDeg= (int)Math.round(Math.toDegrees(pitch));
       // int power = degreesToPower(pitchDeg);

        //tilt right, number become more -ve
        //tilt left, number become more +ve
        //use +- 30 deg to gauge phone orientation

        if(pitchDeg<-30 && pitchDeg>-90){
            orientStr ="Right";
        }
        else if (pitchDeg>30 && pitchDeg<90){
            orientStr ="Left";
        }
        else{
            orientStr ="Flat";
        }

        if(orientStr != orientStrOld){
            tiltDataTV.setText("Tilting "+orientStr);
            orientStrOld=orientStr;

            //Everytime orientation changed, should send signal via Bluetooth
            Message msg = Message.obtain();
            msg.obj = orientStr;
            writeHandler.sendMessage(msg);

        }
    }

    // Convert degrees to absolute tilt value between 0-100
    private int degreesToPower(int degrees) {
        // Tilted back towards user more than -90 deg
        if (degrees < -90) {
            degrees = -90;
        }
        // Tilted forward past 0 deg
        else if (degrees > 0) {
            degrees = 0;
        }
        // Normalize into a positive value
        degrees *= -1;
        // Invert from 90-0 to 0-90
        degrees = 90 - degrees;
        // Convert to scale of 0-100
        float degFloat = degrees / 90f * 100f;
        return (int) degFloat;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //don't do anything
    }

    public void goForwardButtonPressed(View v){
        Log.v(TAG,"goForward button pressed.");

        //send going forward command to Arduino
        //there's a field for the button, that specifies onClicked method.

    }

    public void goBackwardButtonPressed(View v){
        Log.v(TAG,"goBackward button pressed.");

        //send going backward command to Arduino


    }


    public void connectButtonPressed(View v){
        Log.v(TAG,"connect button pressed.");

        if(btThread !=null){
            Log.w(TAG,"Already connected!");
            return;
        }

        // Initialize the Bluetooth thread, passing in a MAC address
        // and a Handler that will receive incoming messages
        btThread = new BluetoothThread(address, new Handler() {

            @Override
            public void handleMessage(Message message) {

                String s = (String) message.obj;

                // Do something with the message
                if (s.equals("CONNECTED")) {
                    TextView tv = (TextView) findViewById(R.id.statusText);
                    tv.setText("Connected.");
                    Button b = (Button) findViewById(R.id.writeButton);
                    b.setEnabled(true);
                } else if (s.equals("DISCONNECTED")) {
                    TextView tv = (TextView) findViewById(R.id.statusText);
                    Button b = (Button) findViewById(R.id.writeButton);
                    b.setEnabled(false);
                    tv.setText("Disconnected.");
                } else if (s.equals("CONNECTION FAILED")) {
                    TextView tv = (TextView) findViewById(R.id.statusText);
                    tv.setText("Connection failed!");
                    btThread = null;
                } else {
                    TextView tv = (TextView) findViewById(R.id.readField);
                    tv.setText(s);
                }
            }
        });

        writeHandler=btThread.getWriteHandler();

        btThread.start();

        TextView tv= (TextView)findViewById(R.id.statusText);
        tv.setText("Connecting...");

    }

    public void disconnectButtonPressed(View v){
        Log.v(TAG, "Disconnect button pressed.");

        if(btThread!=null){
            btThread.interrupt();
            btThread=null;
        }
    }

    public void writeButtonPressed(View v){
        Log.v(TAG, "write button pressed.");

        TextView tv=(TextView)findViewById(R.id.writeField);
        String data = tv.getText().toString();

        Message msg = Message.obtain();
        msg.obj = data;
        writeHandler.sendMessage(msg);
    }




}
