package cselp.sensortrack.util;

import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cselp.sensortrack.Const;
import cselp.sensortrack.bean.LocationData;
import cselp.sensortrack.bean.PhoneInfo;
import cselp.sensortrack.bean.RotationVector;
import cselp.sensortrack.bean.SensorData;
import cselp.sensortrack.bean.TimeData;
import cselp.sensortrack.bean.TrackData;
import cselp.sensortrack.bean.TrackEvent;
import cselp.sensortrack.bean.TrackPart;

/**
 * This class contains utility methods for track data processing.
 */
public class DataUtil {
    private static final String TAG = "DataUtil";

    /**
     * Generates JSON presentation of TrackPart object
     * @param part TrackPart object
     * @return JSONObject representing of specified TrackPart object
     */
    public static JSONObject generateTrackPart(TrackPart part) {
        Gson gson = new GsonBuilder().setExclusionStrategies(new LocationExclusionStrategy()).
                registerTypeAdapter(Float.class, new JsonFloatSerializer(4)).
                create();
        JSONObject res = new JSONObject();
        try {
            res.put("uuid", part.uuid);
            res.put("start", part.start);
            res.put("end", part.end);
            res.put("partNum", part.partNum);
            res.put("partStart", part.partStart);
            res.put("partEnd", part.partEnd);
            res.put("partCount", part.partCount);
            res.put("sensorDataRate", part.sensorDataRate);
            res.put("accelerationDelta", part.accelerationDelta);
            res.put("gyroscopeDelta", part.gyroscopeDelta);
            res.put("compassDelta", part.compassDelta);
            res.put("rotationDelta", part.rotationDelta);
            res.put("gravityDelta", part.gravityDelta);
            res.put("altitudeDelta", part.altitudeDelta);
            res.put("locationDelta", part.locationDelta);

            putArray(part.acceleration, "acceleration", gson, res);
            putArray(part.gyroscope, "gyroscope", gson, res);
            putArray(part.compass, "compass", gson, res);
            putArray(part.rotation, "rotation", gson, res);
            putArray(part.gravity, "gravity", gson, res);
            putArray(part.altitude, "altitude", gson, res);
            putArray(part.location, "location", gson, res);
            putArray(part.events, "events", gson, res);
            //phone info;
            JSONObject terminal = new JSONObject(gson.toJson(part.terminal));
            res.put("terminal", terminal);
        }
        catch (Exception e) {
            Log.e(TAG, "generateTrackPart error", e);
        }
        return res;
    }

    private static void putArray(List<? extends TimeData> list, String key, Gson gson, JSONObject res)
            throws JSONException {
        if (!list.isEmpty()) {
            JSONArray eventsJson = new JSONArray(gson.toJson(list));
            res.put(key, eventsJson);
        }
    }

    /**
     * Generates String (JSON) presentation of TrackData object
     * @param trackData TrackData object
     * @return JSON presentation
     */
    public static String generateTrack(TrackData trackData) {
        Gson gson = new GsonBuilder().setExclusionStrategies(new LocationExclusionStrategy()).
                registerTypeAdapter(Float.class, new JsonFloatSerializer(4)).
                create();
        JSONObject res = new JSONObject();
        try {
            res.put("uuid", trackData.uuid);
            res.put("start", trackData.start);
            res.put("end", trackData.end);
            res.put("partCount", trackData.partCount);
            res.put("sensorDataRate", trackData.sensorDataRate);
            res.put("accelerationDelta", trackData.accelerationDelta);
            res.put("gyroscopeDelta", trackData.gyroscopeDelta);
            res.put("compassDelta", trackData.compassDelta);
            res.put("rotationDelta", trackData.rotationDelta);
            res.put("altitudeDelta", trackData.altitudeDelta);
            res.put("locationDelta", trackData.locationDelta);
            res.put("gravityDelta", trackData.gravityDelta);

            putArray(trackData.linearAcc, "linearAcc", gson, res);

            putArray(trackData.acceleration, "acceleration", gson, res);
            putArray(trackData.gyroscope, "gyroscope", gson, res);
            putArray(trackData.compass, "compass", gson, res);
            putArray(trackData.rotation, "rotation", gson, res);
            putArray(trackData.gravity, "gravity", gson, res);
            putArray(trackData.altitude, "altitude", gson, res);
            putArray(trackData.location, "location", gson, res);
            putArray(trackData.events, "events", gson, res);
            //phone info;
            JSONObject terminal = new JSONObject(gson.toJson(trackData.terminal));
            res.put("terminal", terminal);
        }
        catch (Exception e) {
            Log.e(TAG, "generateTrack error", e);
        }
        return res.toString();
    }

    /**
     * Multiplication of matrix 3*3
     * @param a left matrix
     * @param b right matrix
     * @return result matrix
     */
    public static float[] matrixMultiplication(float[] a, float[] b) {
        float[] result = new float[9];

        result[0] = a[0] * b[0] + a[1] * b[3] + a[2] * b[6];
        result[1] = a[0] * b[1] + a[1] * b[4] + a[2] * b[7];
        result[2] = a[0] * b[2] + a[1] * b[5] + a[2] * b[8];

        result[3] = a[3] * b[0] + a[4] * b[3] + a[5] * b[6];
        result[4] = a[3] * b[1] + a[4] * b[4] + a[5] * b[7];
        result[5] = a[3] * b[2] + a[4] * b[5] + a[5] * b[8];

        result[6] = a[6] * b[0] + a[7] * b[3] + a[8] * b[6];
        result[7] = a[6] * b[1] + a[7] * b[4] + a[8] * b[7];
        result[8] = a[6] * b[2] + a[7] * b[5] + a[8] * b[8];

        return result;
    }

    /**
     * Returns square of value
     * @param val specified value
     * @return square of value
     */
    public static double sq(double val) {
        return val * val;
    }

    public static double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(sq(x1 - x2) + sq(y1 - y2));
    }

    /**
     * Returns distance between 2 vectors x1,y1,z1 ans x2,y2,z2
     * @param x1 coordinates of vector 1
     * @param y1 coordinates of vector 1
     * @param z1 coordinates of vector 1
     * @param x2 coordinates of vector 2
     * @param y2 coordinates of vector 2
     * @param z2 coordinates of vector 2
     * @return distance
     */
    public static double distance(float x1, float y1, float z1, float x2, float y2, float z2) {
        return Math.sqrt(sq(x1 - x2) + sq(y1 - y2) + sq(z1 - z2));
    }

    public static double distance(float[] p1, float[] p2, int dimension) {
        float sum = 0;
        for (int i = 0; i < dimension; i++) {
            sum += sq(p1[i] - p2[i]);
        }
        return Math.sqrt(sum);
    }

    /**
     * Returns short phone description
     * @param tm TelephonyManager
     * @return phone description
     */
    public static PhoneInfo getPhoneInfo(TelephonyManager tm) {
        PhoneInfo res = new PhoneInfo(Build.VERSION.RELEASE, Build.ID, Build.MODEL, Build.MANUFACTURER);
        if (tm != null) {
            res.setDeviceId(tm.getDeviceId());
        }
        return res;
    }

    /**
     * Register specified SensorEventListener for defined sensor type in SensorManager
     * @param sensorManager SensorManager
     * @param sensorType sensor type
     * @param listener SensorEventListener
     * @param samplingPeriodUs The desired delay between two consecutive events in microseconds.
     * @param maxReportLatencyUs Maximum time in microseconds that events can be delayed before
     *            being reported to the application.
     * @return true if the sensor is supported and successfully enabled.
     */
    public static boolean registerListener(SensorManager sensorManager,
                                           int sensorType, SensorEventListener listener,
                                           int samplingPeriodUs, int maxReportLatencyUs) {
        Sensor sensor = sensorManager.getDefaultSensor(sensorType);
        if (sensor != null) {
            boolean res = sensorManager.registerListener(listener, sensor, samplingPeriodUs, maxReportLatencyUs);
            if (!res) {
                Log.w(TAG, "Sensor type " + sensorType + " register fail.");
            }
            return res;
        }
        else {
            Log.w(TAG, "Sensor type " + sensorType + " unavailable");
            return false;
        }
    }

    /**
     * Generates track parts from TrackData, to limits data packets sent to server.
     * Side effect - filled TrackData partCount field
     * @param track TrackData value
     * @return list of track parts
     */
    @NonNull
    public static List<TrackPart> getTrackParts(TrackData track) {
        //Track to parts
        int altIdx = 0, gyroIdx = 0, gravIdx = 0, rotIdx = 0, cmpIdx = 0, locIdx = 0, eventIdx = 0;
        int partId = 0;
        List<TrackPart> trackParts = new ArrayList<>();
        for (int i = 0; i < track.acceleration.size(); i = i + Const.TRACK_PART_STEP) {
            TrackPart part = new TrackPart();
            part.initFrom(track);
            int end = Math.min(i + Const.TRACK_PART_STEP, track.acceleration.size());
            boolean last = (end == track.acceleration.size());
            part.acceleration.addAll(track.acceleration.subList(i, end));
            long startTime = track.acceleration.get(i).t;
            long endTime = track.acceleration.get(end - 1).t;
            part.partStart = startTime;
            part.partEnd = endTime;
            part.partNum = partId;
            partId++;
            //get slices from start to end time. first part use startTime = trackData.start
            List<SensorData> res = getDataPart(altIdx, endTime, last, track.altitude);
            altIdx += res.size();
            part.altitude.addAll(res);
            res = getDataPart(gyroIdx, endTime, last, track.gyroscope);
            gyroIdx += res.size();
            part.gyroscope.addAll(res);
            res = getDataPart(gravIdx, endTime, last, track.gravity);
            gravIdx += res.size();
            part.gravity.addAll(res);
            res = getDataPart(cmpIdx, endTime, last, track.compass);
            cmpIdx += res.size();
            part.compass.addAll(res);
            List<RotationVector> rot = getDataPart(rotIdx, endTime, last, track.rotation);
            rotIdx += rot.size();
            part.rotation.addAll(rot);
            List<LocationData> loc = getDataPart(locIdx, endTime, last, track.location);
            locIdx += loc.size();
            part.location.addAll(loc);
            List<TrackEvent> events = getDataPart(eventIdx, endTime, last, track.events);
            eventIdx += events.size();
            part.events.addAll(events);
            trackParts.add(part);
        }
        track.partCount = trackParts.size();
        for (TrackPart p : trackParts) {
            p.partCount = trackParts.size();
        }
        return trackParts;
    }

    /**
     * Extract slices from specified list, using start index idx and end marker endTime.
     * @param idx start index of slice
     * @param endTime end marker, all objects with timestamp < endTime included to slice
     * @param last is this slice last
     * @param list input list
     * @param <T> class of processed objects, extends TimeData
     * @return sub list of input
     */
    private static <T extends TimeData> List<T> getDataPart(int idx, long endTime, boolean last, List<T> list) {
        list = list.subList(idx, list.size());
        List<T> res = new ArrayList<>();
        if (last) {
            res.addAll(list);
        }
        else {
            for (T sd : list) {
                if (sd.t <= endTime) {
                    res.add(sd);
                }
            }
        }
        return res;
    }
}
