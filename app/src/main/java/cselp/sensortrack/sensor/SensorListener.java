package cselp.sensortrack.sensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;

import com.google.android.gms.location.LocationListener;

import java.util.Arrays;

import cselp.sensortrack.util.DataUtil;

/**
 * Implementation of SensorEventListener and LocationListener,
 * to process Sensors API and Location API calls.
 * Data from accelerometer, gyroscope, pressure, GPS and magnetic sensors
 * are processed and sent to attached consumer.
 */
public class SensorListener implements SensorEventListener, LocationListener {
    private static final String TAG = "SensorEventListener";

    private ISensorConsumer consumer;
    private float[] gravity = new float[3];
    private float[] linear_acceleration = new float[3];
    private float[] acceleration = new float[3];
    private static final float GRAVITY_ALPHA = 0.8f;

    private float[] geomagnetic = new float[3];

    /**
     * The time-stamp being used to record the time when the last gyroscope event occurred.
     */
    private long gyroTimestamp;

    /**
     * This is a filter-threshold for discarding Gyroscope measurements that are below a certain level and
     * potentially are only noise and not real motion. Values from the gyroscope are usually between 0 (stop) and
     * 10 (rapid rotation), so 0.1 seems to be a reasonable threshold to filter noise (usually smaller than 0.1) and
     * real motion (usually > 0.1). Note that there is a chance of missing real motion, if the use is turning the
     * device really slowly, so this value has to find a balance between accepting noise (threshold = 0) and missing
     * slow user-action (threshold > 0.5). 0.1 seems to work fine for most applications.
     *
     */
    private static final double EPSILON = 0.1f;

    /**
     * Constant specifying the factor between a Nano-second and a second
     */
    private static final float NS2S = 1.0f / 1000000000.0f;
    // nanoseconds to milliseconds factor
    private static final float NS2MS = 1.0f / 1000000.0f;

    private final float[] deltaRotationVector = new float[4];
    private final float[] deltaRotationMatrixCalibrated = new float[9];
    private float[] currentRotationMatrixCalibrated = new float[9];
    private float[] initialRotationMatrix = new float[9];
    private float[] gyroscopeOrientationCalibrated = new float[3];

    //reference value of event.timestamp, filled after first event.
    //To ensure precision, first events should be received with max_report_latency_ns. NOT IMPLEMENTED.
    private long referenceEventTimestamp = 0;
    //value of System.currentTimeMillis() when first event received
    private long referenceStartTime;
    //stuff for location events
    private long referenceLocEventTimestamp = 0;
    private long referenceLocStartTime;

    private boolean stateInitializedCalibrated = false;

    private boolean hasInitialOrientation = false;

    /**
     * Constructor
     * @param consumer sensor data consumer
     */
    public SensorListener(ISensorConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onLocationChanged(Location loc) {
        long timestamp = loc.getElapsedRealtimeNanos();
        if (referenceLocEventTimestamp == 0) {
            referenceLocEventTimestamp = timestamp;
            referenceLocStartTime = System.currentTimeMillis();
        }
        long locTimestamp = referenceLocStartTime + (int)((timestamp - referenceLocEventTimestamp) * NS2MS);
        consumer.setLocationValue(locTimestamp, loc);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (referenceEventTimestamp == 0) {
            referenceEventTimestamp = event.timestamp;
            referenceStartTime = System.currentTimeMillis();
        }
        //event timestamp, recalculated into milliseconds using referenceStartTime, referenceEventTimestamp and event.timestamp.
        long eventTimestamp = referenceStartTime + (int)((event.timestamp - referenceEventTimestamp) * NS2MS);
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float[] result = calculateAcceleration(event);
            //result : 0-2 - initial values, 3-5 - linear_acceleration, 6-8 - gravity
            consumer.setAccelerationValues(eventTimestamp, result);
        }
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if (acceleration[0] != 0 && geomagnetic[0] != 0 && !hasInitialOrientation) {
                for (int i = 0; i < currentRotationMatrixCalibrated.length; i++) {
                    currentRotationMatrixCalibrated[i] = 1;
                }
                calculateInitialOrientation();
            }
            calculateGyroscope(event);
            consumer.setGyroscopeValues(eventTimestamp, Arrays.copyOf(gyroscopeOrientationCalibrated, gyroscopeOrientationCalibrated.length));
        }
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic[0] = event.values[0];
            geomagnetic[1] = event.values[1];
            geomagnetic[2] = event.values[2];
            consumer.setGeomagneticValues(eventTimestamp, Arrays.copyOf(geomagnetic, geomagnetic.length));
        }
        else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            consumer.setRotationVector(eventTimestamp, Arrays.copyOf(event.values, 5));
        }
        else if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            consumer.setLinearAccelerationValues(eventTimestamp, Arrays.copyOf(event.values, 3));
        }
        else if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            consumer.setGravityValues(eventTimestamp, Arrays.copyOf(event.values, 3));
        }
        else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            //values[0]: Atmospheric pressure in hPa (millibar)
            float[] values = event.values;
            consumer.setPressureValue(eventTimestamp, values[0]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //nothing to do
    }

    /**
     * Calculates phone orientation using initialRotationMatrix,
     * @param event gyroscope sensor event
     */
    private void calculateGyroscope(SensorEvent event) {
        // don't start until first accelerometer/magnetometer orientation has been acquired
        if (!hasInitialOrientation) {
            return;
        }
        // Initialization of the gyroscope based rotation matrix
        if (!stateInitializedCalibrated) {
            currentRotationMatrixCalibrated = DataUtil.matrixMultiplication(
                    currentRotationMatrixCalibrated, initialRotationMatrix);

            stateInitializedCalibrated = true;
        }
        // This timestep's delta rotation to be multiplied by the current rotation
        // after computing it from the gyro sample data.
        if (gyroTimestamp != 0) {
            final float dT = (event.timestamp - gyroTimestamp) * NS2S;
            // Axis of the rotation sample, not normalized yet.
            float axisX = event.values[0];
            float axisY = event.values[1];
            float axisZ = event.values[2];

            // Calculate the angular speed of the sample
            double gyroscopeRotationVelocity = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);

            // Normalize the rotation vector if it's big enough to get the axis
            if (gyroscopeRotationVelocity > EPSILON) {
                axisX /= gyroscopeRotationVelocity;
                axisY /= gyroscopeRotationVelocity;
                axisZ /= gyroscopeRotationVelocity;
            }

            // Integrate around this axis with the angular speed by the timestep
            // in order to get a delta rotation from this sample over the timestep
            // We will convert this axis-angle representation of the delta rotation
            // into a quaternion before turning it into the rotation matrix.
            double thetaOverTwo = gyroscopeRotationVelocity * dT / 2.0f;
            double sinThetaOverTwo = Math.sin(thetaOverTwo);
            double cosThetaOverTwo = Math.cos(thetaOverTwo);
            deltaRotationVector[0] = (float)sinThetaOverTwo * axisX;
            deltaRotationVector[1] = (float)sinThetaOverTwo * axisY;
            deltaRotationVector[2] = (float)sinThetaOverTwo * axisZ;
            deltaRotationVector[3] = (float)cosThetaOverTwo;

            SensorManager.getRotationMatrixFromVector(
                    deltaRotationMatrixCalibrated,
                    deltaRotationVector);

            currentRotationMatrixCalibrated = DataUtil.matrixMultiplication(
                    currentRotationMatrixCalibrated,
                    deltaRotationMatrixCalibrated);

            SensorManager.getOrientation(currentRotationMatrixCalibrated,
                    gyroscopeOrientationCalibrated);
        }
        gyroTimestamp = event.timestamp;
    }

    /**
     * Calculates linear acceleration and gravity.
     * Can be replaced with TYPE_LINEAR_ACCELERATION and TYPE_GRAVITY sensors.
     * @param event acceleration sensor event
     * @return float array, [0]:[2] - initial values, [3]:[5] - linear_acceleration, [6]:[8] - gravity
     */
    private float[] calculateAcceleration(SensorEvent event) {
        // In this example, GRAVITY_ALPHA is calculated as t / (t + dT),
        // where t is the low-pass filter's time-constant and
        // dT is the event delivery rate.
        acceleration[0] = event.values[0];
        acceleration[1] = event.values[1];
        acceleration[2] = event.values[2];
        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = GRAVITY_ALPHA * gravity[0] + (1 - GRAVITY_ALPHA) * event.values[0];
        gravity[1] = GRAVITY_ALPHA * gravity[1] + (1 - GRAVITY_ALPHA) * event.values[1];
        gravity[2] = GRAVITY_ALPHA * gravity[2] + (1 - GRAVITY_ALPHA) * event.values[2];

        // Remove the gravity contribution with the high-pass filter.
        linear_acceleration[0] = event.values[0] - gravity[0];
        linear_acceleration[1] = event.values[1] - gravity[1];
        linear_acceleration[2] = event.values[2] - gravity[2];

        float[] result = new float[9];
        System.arraycopy(event.values, 0, result, 0, 3);
        System.arraycopy(linear_acceleration, 0, result, 3, 3);
        System.arraycopy(gravity, 0, result, 6, 3);
        //result : 0-2 - initial values, 3-5 - linear_acceleration, 6-8 - gravity
        return result;
    }

    /**
     * Calculates initial rotation matrix, used later for calculations of orientation
     */
    private void calculateInitialOrientation()
    {
        hasInitialOrientation = SensorManager.getRotationMatrix(
                initialRotationMatrix, null, acceleration, geomagnetic);

    }

}
