package cselp.sensortrack.sensor;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import cselp.sensortrack.Const;
import cselp.sensortrack.R;
import cselp.sensortrack.bean.DataHolder;
import cselp.sensortrack.bean.LocationData;
import cselp.sensortrack.bean.RotationVector;
import cselp.sensortrack.bean.SensorData;
import cselp.sensortrack.bean.TimeData;
import cselp.sensortrack.bean.TrackData;
import cselp.sensortrack.bean.TrackPart;
import cselp.sensortrack.bean.TrackPosition;
import cselp.sensortrack.util.DataUtil;

/**
 * The service class to process data from mobile device sensors,
 * track data changes and save to device storage.
 */

public class SensorService extends Service implements ISensorConsumer,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "SensorService";

    private static final SimpleDateFormat fTime = new SimpleDateFormat("hh:mm:ss", Locale.US);

    public static final String TRACK_FILE_FORMAT = "bin";

    private final LocalBinder binder = new LocalBinder();
    private final ThreadLocal<SimpleDateFormat> sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        }
    };

    private int sensorDelayValue = SensorManager.SENSOR_DELAY_NORMAL;
    private float accDeltaValue = Const.ACCELERATION_DELTA;
    private SensorListener sensorListener = null;

    private GoogleApiClient googleApiClient;

    private LocalBroadcastManager broadcastManager;
    private RequestQueue requestQueue;
    private SuccessListener successListener = new SuccessListener();
    private ErrorListener errorListener = new ErrorListener();
    private String serverUrl = null;

    private TrackData trackData;
    private TrackPosition trackPosition;
    private boolean trackStarted = false;
    private boolean sendToServer = false;
    private long accCount = 0;

    private DataHolder lAccHolder = new DataHolder(Const.ACCELERATION_DELTA, Const.SENSOR_TIME_DELTA);
    private DataHolder accHolder = new DataHolder(Const.ACCELERATION_DELTA, Const.SENSOR_TIME_DELTA);
    private DataHolder cLAccHolder = new DataHolder(Const.ACCELERATION_DELTA, Const.SENSOR_TIME_DELTA);
    private DataHolder gravHolder = new DataHolder(Const.GRAVITY_DELTA, Const.SENSOR_TIME_DELTA);
    private DataHolder cGravHolder = new DataHolder(Const.GRAVITY_DELTA, Const.SENSOR_TIME_DELTA);

    private float altitudeZero = 0;
    private float altitudeZeroCount = 0;

    private final Object lock = new Object();
    private PowerManager.WakeLock wakeLock;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Returns the instance of the service
     */
    public class LocalBinder extends Binder {
        public SensorService getServiceInstance() {
            return SensorService.this;
        }
    }

    /**
     * Creates new SensorListener, LocalBroadcastManager instance
     * and googleApiClient with location service api.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        if (sensorListener == null) {
            sensorListener = new SensorListener(this);
        }
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).build();
        broadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        final SharedPreferences sPref = getSharedPreferences(Const.APPLICATION_SETTINGS, MODE_PRIVATE);
        sensorDelayValue = sPref.getInt(Const.SENSOR_DELAY_KEY, SensorManager.SENSOR_DELAY_NORMAL);
        registerSensorsEventListener();
        accDeltaValue = sPref.getFloat(Const.ACCELERATION_DELTA_KEY, Const.ACCELERATION_DELTA);
        accHolder.setDelta(accDeltaValue);
        connectLocationClient();

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Checks if GPS is enabled, and connects to google location service
     */
    public void connectLocationClient() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        Log.i(TAG, "GPS_PROVIDER enabled : " + enabled);
        if (enabled) {
            sendGpsStateMsg(getString(R.string.gps_enabled));
        } else {
            sendGpsStateMsg(getString(R.string.gps_disabled));
        }
        if (enabled && !googleApiClient.isConnected()) {
            googleApiClient.connect();
            Log.d(TAG, "Resume - googleApiClient connect.");
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        releaseWakeLock();
        unregisterListener();
        unregisterLocationListener();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved");
        releaseWakeLock();
        unregisterListener();
        unregisterLocationListener();
    }

    /**
     * Registers sensorListener instance for required sensor types.
     */
    private void registerSensorsEventListener() {
        // Get the default sensor for the sensor type from the SenorManager
        SensorManager mgr = (SensorManager) getSystemService(Activity.SENSOR_SERVICE);

        DataUtil.registerListener(mgr, Sensor.TYPE_ACCELEROMETER, sensorListener, sensorDelayValue, 0);
        DataUtil.registerListener(mgr, Sensor.TYPE_GYROSCOPE, sensorListener, sensorDelayValue, 0);
        DataUtil.registerListener(mgr, Sensor.TYPE_MAGNETIC_FIELD, sensorListener, sensorDelayValue, 0);
        DataUtil.registerListener(mgr, Sensor.TYPE_ROTATION_VECTOR, sensorListener, sensorDelayValue, 0);
        DataUtil.registerListener(mgr, Sensor.TYPE_LINEAR_ACCELERATION, sensorListener, sensorDelayValue, 0);
        DataUtil.registerListener(mgr, Sensor.TYPE_PRESSURE, sensorListener, sensorDelayValue, 0);
        DataUtil.registerListener(mgr, Sensor.TYPE_GRAVITY, sensorListener, SensorManager.SENSOR_DELAY_NORMAL, 0);
        Log.d(TAG, "registerSensorsEventListener, sensorDelayValue=" + sensorDelayValue);
    }

    /**
     * Unregisters registered listener
     */
    private void unregisterListener() {
        SensorManager sensorManager =
                (SensorManager) getSystemService(Activity.SENSOR_SERVICE);
        sensorManager.unregisterListener(sensorListener);
        Log.d(TAG, "Sensor listener unregistered.");
    }

    /**
     * Unregister SensorListener for location service, disconnect googleApiClient.
     */
    private void unregisterLocationListener() {
        if (googleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, sensorListener);
            googleApiClient.disconnect();
            Log.d(TAG, "googleApiClient disconnect.");
        }
    }

    /**
     * Implementation of ISensorConsumer method, to process values from TYPE_ACCELEROMETER sensor.
     * Values array contains : 0-2 - initial values, 3-5 - calculated linear_acceleration, 6-8 - calculated gravity
     * Units are m/s^2.
     * @param timestamp event timestamp, ms
     * @param values array of acceleration values
     */
    @Override
    public void setAccelerationValues(long timestamp, float[] values) {
        //values : 0-2 - initial values, 3-5 - linear_acceleration, 6-8 - gravity
        if (trackStarted) {
            accCount++;
            //todo synchronize
            accHolder.setLastData(timestamp, values);
            if (accHolder.shouldStore()) {
                SensorData[] data = accHolder.valuesToStore();
                for (SensorData sd : data) {
                    trackData.acceleration.add(sd);
                    //status message - recording progress
                    if (trackData.acceleration.size() % 10 == 0) {
                        sendStatusMsg("Recorded " + trackData.acceleration.size() + " acceleration points.");
                        Log.d(TAG, fTime.format(new Date()) + " - Recorded " + trackData.acceleration.size() + " acceleration points.");
                        int ratio = (int) (100.0 * trackData.acceleration.size() / accCount);
                        sendFilterRatioMsg(ratio + "%");
                    }
                }
            }
            //check acceleration list size, if > predefined value - stop track
            if (trackData.acceleration.size() > Const.MAX_ACCELERATION_LIST_SIZE) {
                Log.d(TAG, "Stop track recording: acceleration list size " + trackData.acceleration.size());
                stopTrack();
                Timer t = new Timer("restart_track");
                t.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        startTrack(sendToServer);
                    }
                }, 1000);
                Log.d(TAG, "Restart track recording");
            } else if (sendToServer && (trackData.acceleration.size() - trackPosition.accIdx.get()) >= Const.TRACK_PART_STEP) {
                sendNextPart(trackData, trackPosition, false);
            }
        }
    }

    /**
     * Implementation of ISensorConsumer method, to process values from magnetic field sensor.
     * All values are in micro-Tesla (uT) and measure the ambient magnetic field
     * in the X, Y and Z axis.
     * @param timestamp event timestamp, ms
     * @param values magnetic field in the X, Y and Z axis
     */
    @Override
    public void setGeomagneticValues(long timestamp, float[] values) {
        if (trackStarted) {
            List<SensorData> list = trackData.compass;
            if (!list.isEmpty()) {
                SensorData last = list.get(list.size() - 1);
                //check if new sensor values > (previous + delta) or time interval exceed specified delta
                if (DataUtil.distance(last.x, last.y, last.z, values[0], values[1], values[2]) > Const.COMPASS_DELTA ||
                        (timestamp - last.t) > Const.SENSOR_TIME_DELTA) {
                    list.add(new SensorData(timestamp, values[0], values[1], values[2]));
                }
            } else {
                list.add(new SensorData(System.currentTimeMillis(), values[0], values[1], values[2]));
            }
        }
    }

    /**
     * Implementation of ISensorConsumer method, to process values from TYPE_GRAVITY sensor.
     * A three dimensional vector indicating the direction and magnitude of gravity.
     * Units are m/s^2.
     * @param timestamp event timestamp, ms
     * @param values array of gravity values
     */
    @Override
    public void setGravityValues(long timestamp, float[] values) {
        if (trackStarted) {
            //todo synchronize
            gravHolder.setLastData(timestamp, values);
            if (gravHolder.shouldStore()) {
                Collections.addAll(trackData.gravity, gravHolder.valuesToStore());
            }
        }
    }

    /**
     * Implementation of ISensorConsumer method, to process values from gyroscope sensor.
     * Gyroscope values recalculated into orientation values by SensorListener.
     * @param timestamp event timestamp, ms
     * @param values array of orientation values.
     */
    @Override
    public void setGyroscopeValues(long timestamp, float[] values) {
        if (trackStarted) {
            //exclude "NaN" values
            for (float value : values) {
                if (Float.isNaN(value) || Float.isInfinite(value)) {
                    return;
                }
            }
            List<SensorData> list = trackData.gyroscope;
            if (!list.isEmpty()) {
                SensorData last = list.get(list.size() - 1);
                //Gyroscope format: values[0] - Z, values[1] - X, values[2] - Y
                if (DataUtil.distance(last.x, last.y, last.z, values[1], values[2], values[0]) > Const.GYROSCOPE_DELTA ||
                        (timestamp - last.t) > Const.SENSOR_TIME_DELTA) {
                    list.add(new SensorData(System.currentTimeMillis(), values[1], values[2], values[0]));
                }
            } else {
                list.add(new SensorData(System.currentTimeMillis(), values[1], values[2], values[0]));
            }
        }
    }

    /**
     * Implementation of ISensorConsumer method, to process values from TYPE_LINEAR_ACCELERATION sensor.
     * A three dimensional vector indicating acceleration along each device axis, not including
     * gravity. Units are m/s^2.
     * @param timestamp event timestamp, ms
     * @param values array of linear acceleration values
     */
    @Override
    public void setLinearAccelerationValues(long timestamp, float[] values) {
        if (trackStarted) {
            //todo synchronize
            lAccHolder.setLastData(timestamp, values);
            if (lAccHolder.shouldStore()) {
                Collections.addAll(trackData.linearAcc, lAccHolder.valuesToStore());
            }
        }
    }

    /**
     * Implementation of ISensorConsumer method, to process location updates.
     * @param timestamp event timestamp, ms
     * @param value Location object
     */
    @Override
    public void setLocationValue(long timestamp, Location value) {
        sendLocationMsg(value);
        if (trackStarted) {
            //store values to track data
            if (trackData.location.isEmpty()) {
                trackData.location.add(new LocationData(timestamp, value));
            } else {
                //check delta changes
                LocationData last = trackData.location.get(trackData.location.size() - 1);
                if ((Math.abs(value.distanceTo(last.l)) > Const.LOCATION_DELTA) ||
                        ((timestamp - last.t) > Const.SENSOR_TIME_DELTA)) {
                    trackData.location.add(new LocationData(timestamp, value));
                }
            }
        }
    }

    /**
     * Implementation of ISensorConsumer method, to process values from TYPE_PRESSURE sensor.
     * Recalculates into SensorData values:
     * x = altitudeDelta , altitude changes from point of track recording start
     * y = altitude, in meters, calculates from the atmospheric pressure and the
     * pressure at sea level.
     * z = Atmospheric pressure in hPa
     * @param timestamp event timestamp, ms
     * @param value Atmospheric pressure in hPa (millibar)
     */
    @Override
    public void setPressureValue(long timestamp, float value) {
        if (trackStarted) {
            //value is pressure in hPa
            //altitude in meters
            float altitude = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE, value);
            if (altitudeZeroCount < Const.ALTITUDE_MEAN_COUNT) {
                altitudeZero += altitude;
                altitudeZeroCount++;
                if (altitudeZeroCount >= Const.ALTITUDE_MEAN_COUNT) {
                    altitudeZero /= Const.ALTITUDE_MEAN_COUNT;
                }
            } else {
                float altitudeDelta = altitude - altitudeZero;
                List<SensorData> list = trackData.altitude;
                if (!list.isEmpty()) {
                    SensorData last = list.get(list.size() - 1);
                    if ((Math.abs(last.x - altitudeDelta) > Const.ALTITUDE_DELTA) ||
                            ((timestamp - last.t) > Const.SENSOR_TIME_DELTA)) {
                        list.add(new SensorData(timestamp, altitudeDelta, altitude, value));
                    }
                } else {
                    list.add(new SensorData(timestamp, altitudeDelta, altitude, value));
                }
            }
        }
    }

    /**
     * Implementation of ISensorConsumer method, to process values from TYPE_ROTATION_VECTOR sensor.
     * array of values :
     * <li> values[0]: x*sin(&#952/2) </li>
     * <li> values[1]: y*sin(&#952/2) </li>
     * <li> values[2]: z*sin(&#952/2) </li>
     * <li> values[3]: cos(&#952/2) </li>
     * <li> values[4]: estimated heading Accuracy (in radians) (-1 if unavailable)</li>
     * @param timestamp event timestamp, ms
     * @param values array of rotation vector values
     */
    @Override
    public void setRotationVector(long timestamp, float[] values) {
        if (trackStarted) {
            List<RotationVector> list = trackData.rotation;
            if (!list.isEmpty()) {
                RotationVector last = list.get(list.size() - 1);
                if (DataUtil.distance(last.x, last.y, last.z, values[0], values[1], values[2]) > Const.ROTATION_DELTA ||
                        (timestamp - last.t) > Const.SENSOR_TIME_DELTA) {
                    list.add(new RotationVector(System.currentTimeMillis(),
                            values[0], values[1], values[2], values[3], values[4]));
                }
            } else {
                list.add(new RotationVector(System.currentTimeMillis(),
                        values[0], values[1], values[2], values[3], values[4]));
            }
        }
    }

    /**
     * Implementation of GoogleApiClient.ConnectionCallbacks method.
     * Called when the connect request has successfully completed.
     * @param  connectionHint  contents are defined by the specific services.
     */
    @Override
    public void onConnected(@Nullable Bundle connectionHint) {
        // Get last known recent location.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.w(TAG, "Permissions ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION required");
            Toast.makeText(getApplicationContext(),
                    "Permissions ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION required", Toast.LENGTH_SHORT).show();
            return;
        }
        Location currentLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        // Note that this can be NULL if last location isn't already known.
        if (currentLocation != null) {
            // Print current location if not null
            Log.i(TAG, "current location: " + currentLocation.toString());
            sendLocationMsg(currentLocation);
        }
        sendGpsStateMsg(getString(R.string.gps_connected));
        Log.i(TAG, "GPS connected");
        // Begin polling for new location updates.
        startLocationUpdates();
    }

    /**
     * Broadcast the given message as GPS state
     * @param msg message
     */
    private void sendGpsStateMsg(String msg) {
        Intent intent = new Intent(Const.Actions.GPS);
        intent.putExtra("msg", msg);
        broadcastManager.sendBroadcast(intent);
        Log.v(TAG, "sendGpsStateMsg: " + msg);
    }

    /**
     * Broadcast the given message as status
     * @param msg status string
     */
    private void sendStatusMsg(String msg) {
        Intent intent = new Intent(Const.Actions.STATUS);
        intent.putExtra("msg", msg);
        broadcastManager.sendBroadcast(intent);
    }

    private void sendFilterRatioMsg(String msg) {
        Intent intent = new Intent(Const.Actions.FILTER_RATIO);
        intent.putExtra("msg", msg);
        broadcastManager.sendBroadcast(intent);
    }

    /**
     * Broadcast the given message as command
     * @param command command
     */
    private void sendCommand(String command) {
        Intent intent = new Intent(Const.Actions.COMMAND);
        intent.putExtra("command", command);
        broadcastManager.sendBroadcast(intent);
        Log.v(TAG, "sendCommand: " + command);
    }

    /**
     * Broadcast the given message as location data
     * @param loc current location
     */
    private void sendLocationMsg(Location loc) {
        Intent intent = new Intent(Const.Actions.LOCATION);
        intent.putExtra("latitude", String.format(Locale.US, "%.5f", loc.getLatitude()));
        intent.putExtra("longitude", String.format(Locale.US, "%.5f", loc.getLongitude()));
        if (loc.hasSpeed()) {
            intent.putExtra("speed", String.format(Locale.US, "%.5f", loc.getSpeed()));
        }
        if (loc.hasAltitude()) {
            intent.putExtra("altitude", String.format(Locale.US, "%.5f", loc.getAltitude()));
        }
        broadcastManager.sendBroadcast(intent);
        Log.v(TAG, "sendLocationMsg");
    }

    /**
     * Makes a request for new location updates at required interval
     */
    protected void startLocationUpdates() {
        // Create the location request
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(Const.LOCATION_UPDATE_INTERVAL)
                .setFastestInterval(Const.LOCATION_FASTEST_INTERVAL);
        // Request location updates
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient,
                locationRequest, sensorListener);
    }

    /**
     * Implementation of GoogleApiClient.ConnectionCallbacks method.
     * Called when the client is temporarily in a disconnected state.
     * @param cause suspend cause
     */
    @Override
    public void onConnectionSuspended(int cause) {
        if (cause == CAUSE_SERVICE_DISCONNECTED) {
            Toast.makeText(getApplicationContext(), "Location: Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();
        } else if (cause == CAUSE_NETWORK_LOST) {
            Toast.makeText(getApplicationContext(), "Location: Network lost. Please re-connect.", Toast.LENGTH_SHORT).show();
        }
        sendGpsStateMsg(getString(R.string.gps_disconnected));
    }

    /**
     * Implementation of GoogleApiClient.OnConnectionFailedListener interface
     * @param connectionResult result of connect request completion
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(getApplicationContext(), "Location: Connection Failed", Toast.LENGTH_SHORT).show();
        sendGpsStateMsg(getString(R.string.gps_conn_fail, connectionResult.getErrorCode()));
    }

    public void setSensorDelayValue(int delay) {
        if (sensorDelayValue != delay) {
            sensorDelayValue = delay;
        }
        if (!trackStarted) {
            unregisterListener();
            registerSensorsEventListener();
        }
    }

    public void setAccDelta(float delta) {
        if (accDeltaValue != delta) {
            accDeltaValue = delta;
        }
        if (!trackStarted) {
            accHolder.setDelta(accDeltaValue);
        }
    }

    /**
     * Starts the track recording.
     */
    public void startTrack(boolean sendTrack) {
        //todo 'sendToServer' can be rewritten by next call
        if (trackStarted) {
            //send status - ignore, trackStarted
            sendStatusMsg("Track recording already started");
            return;
        }
        this.sendToServer = sendTrack;
        acquireWakeLock();
        //clear some stuff
        altitudeZero = 0;
        altitudeZeroCount = 0;
        serverUrl = null;

        accCount = 0;

        lAccHolder.clear();
        cLAccHolder.clear();
        accHolder.clear();
        gravHolder.clear();
        cGravHolder.clear();

        trackData = new TrackData(DataUtil.getPhoneInfo((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE)));
        trackData.start = System.currentTimeMillis();
        trackPosition = new TrackPosition();
        trackData.sensorDataRate = sensorDelayValue;
        trackStarted = true;
        //send command - track stopped
        sendCommand(Const.Command.TRACK_START);
    }

    /**
     * Ends the track recording, stores track to file and send it to external server.
     */
    public void stopTrack() {
        sendCommand(Const.Command.TRACK_STOPPING);
        try {
            final TrackData track;
            TrackPosition trackPos;
            synchronized (lock) {
                trackStarted = false;
                if (trackData == null) {
                    //send command
                    sendCommand(Const.Command.TRACK_STOP);
                    Log.w(TAG, "trackData is null, track recording already stopped");
                    return;
                }
                trackData.end = System.currentTimeMillis();
                track = trackData;
                trackData = null;
                trackPos = trackPosition;
                trackPosition = null;
            }

            track.partCount = trackPos.partNum.get() + 1;
            //write data to file
            //todo save file on bg thread
            saveTrackToFile(track);
            if (sendToServer) {
                sendNextPart(track, trackPos, true);
                String msg = "Sent track to server: " + track.uuid;
                sendStatusMsg(msg);
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "stopTrack error", e);
        }
        finally {
            releaseWakeLock();
        }
        //send command - track stopped
        sendCommand(Const.Command.TRACK_STOP);
    }



    /**
     * Converts track to json object or binary object and save it to file.
     * @param track TrackData object
     */
    private void saveTrackToFile(final TrackData track) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (TRACK_FILE_FORMAT.equals("json")) {
                    saveTrackAsJson(track);
                }
                else {
                    Log.d(TAG, "-----saveTrackAsBinary");
                    saveTrackAsBinary(track);
                }
                ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME);
                toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400);
            }
        };
        Thread t = new Thread(r);
        t.start();
    }

    /**
     * Save track to file as binary object.
     * @param track TrackData object
     */
    private void saveTrackAsBinary(TrackData track) {
        String filename = "sensorTrack_" + sdf.get().format(new Date()) + ".bin";
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            Log.d(TAG, "-----write to file " + file.getName());
            oos.writeObject(track);
            oos.flush();
            oos.close();
            fos.close();
            Log.d(TAG, "------close file ");
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
            postToastMessage("File " + file.getPath() + " stored.");
        }
        catch (Exception e) {
            Log.e(TAG, "FileOutputStream creation error", e);
            postToastMessage("File " + filename + " store exception. " + e.getLocalizedMessage());
        }
    }

    /**
     * Save track to file as json object.
     * @param track TrackData object
     */
    private void saveTrackAsJson(TrackData track) {
        String filename = "sensorTrack_" + sdf.get().format(new Date()) + ".json";
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename);
        try (FileWriter fw = new FileWriter(file);
             BufferedWriter writer = new BufferedWriter(fw)) {
            String sTrack = DataUtil.generateTrack(track);
            writer.write(sTrack);
            writer.flush();
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
            postToastMessage("File " + file.getPath() + " stored.");
        } catch (IOException e) {
            Log.e(TAG, "FileWriter creation error", e);
            postToastMessage("File " + filename + " store exception. " + e.getLocalizedMessage());
        }
    }

    /**
     * Creates standard toast message.
     * @param message The text to show.
     */
    public void postToastMessage(final String message) {
        Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String getServerUrl() {
        if (serverUrl == null) {
            SharedPreferences sPref = getSharedPreferences(Const.APPLICATION_SETTINGS, MODE_PRIVATE);
            try {
                serverUrl = sPref.getString(Const.TRACK_SERVER_URL_KEY, "");
            } catch (Exception e) {
                Log.e(TAG, "get TRACK_SERVER_URL_KEY value error", e);
            }
        }
        return serverUrl;
    }

    /**
     * Requests Wake lock level: Ensures that the CPU is running;
     * when screen turned off, sensor services should remain active.
     */
    private void acquireWakeLock() {
        final PowerManager powerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        releaseWakeLock();
        //Acquire new wake lock
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PARTIAL_WAKE_LOCK");
        wakeLock.acquire();
    }

    /**
     * Releases wakelock with PARTIAL_WAKE_LOCK level.
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    /**
     * Returns instance of the Volley request dispatch queue, uses singleton pattern.
     * @return RequestQueue entity
     */
    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(getApplicationContext());
        }
        return requestQueue;
    }

    private void sendNextPart(final TrackData data, TrackPosition trackPos, boolean last) {
        String url = getServerUrl();
        if (url == null || url.trim().length() == 0) {
            Toast.makeText(getApplicationContext(), "Server url undefined, use setup screen.", Toast.LENGTH_LONG).show();
        } else {
            //test ping then send part
            TrackPart part = getTrackPart(data, trackPos, last);
            if (part != null) {
                String pingUrl = url + Const.URL_PING;
                String addPartUrl = url + Const.URL_ADD_TRACK_PART;
                PingSuccessListener pingSuccess = new PingSuccessListener(part, addPartUrl);
                PingErrorListener pingError = new PingErrorListener(part);
                JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, pingUrl, null, pingSuccess, pingError);
                getRequestQueue().add(request);
            }

        }
    }

    /**
     * Creates TrackPart object from supplied TrackData object.
     * @param data TrackData object
     * @param trackPos track position object, defines start positions for TrackData inner lists.
     * @param last if true it's part is last
     * @return TrackPart object
     */
    private TrackPart getTrackPart(TrackData data, TrackPosition trackPos, boolean last) {
        TrackPart part = new TrackPart();
        part.initFrom(data);
        //if it is last part, copy all values to part
        if (trackPos.accIdx.get() < data.acceleration.size() || last) {
            trackPos.copyData(data.acceleration, part.acceleration, trackPos.accIdx);
            trackPos.copyData(data.altitude, part.altitude, trackPos.altIdx);
            trackPos.copyData(data.compass, part.compass, trackPos.cmpIdx);
            trackPos.copyData(data.gravity, part.gravity, trackPos.gravIdx);
            trackPos.copyData(data.gyroscope, part.gyroscope, trackPos.gyroIdx);
            trackPos.copyData(data.location, part.location, trackPos.locIdx);
            trackPos.copyData(data.rotation, part.rotation, trackPos.rotIdx);
            trackPos.copyData(data.events, part.events, trackPos.eventIdx);

            part.partNum = trackPos.partNum.getAndIncrement();
            setTrackPartStartEnd(part);
        }
        else {
            return null;
        }
        if (last) {
            //always send last part, to ensure partCount
            part.partCount = part.partNum + 1;
        }
        return part;
    }

    /**
     * Sets start and end times for specified TrackPart object.
     * @param part TrackPart object
     */
    private void setTrackPartStartEnd(TrackPart part) {
        long start = Long.MAX_VALUE;
        start = getStart(start, part.acceleration);
        start = getStart(start, part.altitude);
        start = getStart(start, part.compass);
        start = getStart(start, part.gravity);
        start = getStart(start, part.gyroscope);
        start = getStart(start, part.location);
        start = getStart(start, part.rotation);
        start = getStart(start, part.events);
        if (start == Long.MAX_VALUE) {
            start = System.currentTimeMillis();
        }
        long end = 0;
        end = getEnd(end, part.acceleration);
        end = getEnd(end, part.altitude);
        end = getEnd(end, part.compass);
        end = getEnd(end, part.gravity);
        end = getEnd(end, part.gyroscope);
        end = getEnd(end, part.location);
        end = getEnd(end, part.rotation);
        end = getEnd(end, part.events);
        if (end == 0) {
            end = System.currentTimeMillis();
        }
        part.partStart = start;
        part.partEnd = end;
    }

    public <T extends TimeData> long getStart(long current, List<T> list) {
        if (!list.isEmpty()) {
            long s = list.get(0).t;
            return (s < current) ? s : current;
        }
        return current;
    }

    public <T extends TimeData> long getEnd(long current, List<T> list) {
        if (!list.isEmpty()) {
            long e = list.get(list.size() - 1).t;
            return (e > current) ? e : current;
        }
        return current;
    }

    /**
     * Implementation of Response.Listener to create TJsonRequest entity
     */
    class SuccessListener implements Response.Listener<JSONObject> {

        @Override
        public void onResponse(final JSONObject response) {
            String msg = "Sent track to server: " + response;
            sendStatusMsg(msg);
            //Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            Log.d(TAG, "Sent track to server: " + response);
        }
    }

    /**
     * Implementation of Response.ErrorListener to create TJsonRequest entity
     */
    class ErrorListener implements Response.ErrorListener {

        @Override
        public void onErrorResponse(final VolleyError error) {
            // Handle error
            String msg = "Error: send track to server. ";
            NetworkResponse response = error.networkResponse;
            if (response != null) {
                msg += "Status: " + response.statusCode + ". ";
            }
            msg += error.getLocalizedMessage();
            sendStatusMsg(msg);
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error: send track to server", error);
        }
    }

    /**
     * Implementation of Response.Listener for ping request
     */
    class PingSuccessListener implements Response.Listener<JSONObject> {
        private TrackPart part;
        private String url;

        public PingSuccessListener(TrackPart part, String url) {
            this.part = part;
            this.url = url;
        }

        @Override
        public void onResponse(final JSONObject response) {
            Log.d(TAG, "Server ping success: " + response);
            if (part != null) {
                JSONObject jsonPart = DataUtil.generateTrackPart(part);
                JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, jsonPart, successListener, errorListener);
                getRequestQueue().add(request);
            }
        }
    }

    /**
     * * Implementation of Response.ErrorListener for ping request
     */
    class PingErrorListener implements Response.ErrorListener {
        private TrackPart part;

        public PingErrorListener(TrackPart part) {
            this.part = part;
        }

        @Override
        public void onErrorResponse(final VolleyError error) {
            // Handle error
            Log.e(TAG, "Server ping error. Part [" + part.partNum + ":" + part.uuid + "] do not sent", error);
            String msg = "Server ping error. Part ["  + part.partNum + ":" + part.uuid + "] " + error.getLocalizedMessage();
            sendStatusMsg(msg);
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            //todo set part to queue to send later
        }
    }
}
