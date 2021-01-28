package cselp.sensortrack.sensor;

import android.location.Location;

/**
 * Interface of sensor data consumer.
 */

public interface ISensorConsumer {

    /**
     * Method to consume values from gyroscope sensor.
     * Gyroscope values recalculated into orientation values by SensorListener.
     * @param timestamp event timestamp, ms
     * @param values array of orientation values.
     */
    void setGyroscopeValues(long timestamp, float[] values);

    /**
     * Method to consume values from TYPE_ACCELEROMETER sensor.
     * Values array contains : 0-2 - initial values, 3-5 - calculated linear_acceleration, 6-8 - calculated gravity
     * Units are m/s^2.
     * @param timestamp event timestamp, ms
     * @param values array of acceleration values
     */
    void setAccelerationValues(long timestamp, float[] values);

    /**
     * Method to consume geomagnetic values.
     * @param timestamp event timestamp, ms
     * @param values magnetic field in the X, Y and Z axis
     */
    void setGeomagneticValues(long timestamp, float[] values);

    /**
     * Method to consume values from TYPE_ROTATION_VECTOR sensor.
     * array of values :
     * <li> values[0]: x*sin(&#952/2) </li>
     * <li> values[1]: y*sin(&#952/2) </li>
     * <li> values[2]: z*sin(&#952/2) </li>
     * <li> values[3]: cos(&#952/2) </li>
     * <li> values[4]: estimated heading Accuracy (in radians) (-1 if unavailable)</li>
     * @param timestamp event timestamp, ms
     * @param values array of rotation vector values
     */
    void setRotationVector(long timestamp, float[] values);

    /**
     * Method to consume values from TYPE_LINEAR_ACCELERATION sensor.
     * A three dimensional vector indicating acceleration along each device axis, not including
     * gravity. Units are m/s^2.
     * @param timestamp event timestamp, ms
     * @param values array of linear acceleration values
     */
    void setLinearAccelerationValues(long timestamp, float[] values);

    /**
     * Method to consume values from TYPE_GRAVITY sensor.
     * A three dimensional vector indicating the direction and magnitude of gravity.
     * Units are m/s^2.
     * @param timestamp event timestamp, ms
     * @param values array of gravity values
     */
    void setGravityValues(long timestamp, float[] values);

    /**
     * Method to consume values from TYPE_PRESSURE sensor.
     * Recalculates into SensorData values:
     * x = altitudeDelta , altitude changes from point of track recording start
     * y = altitude, in meters, calculates from the atmospheric pressure and the
     * pressure at sea level.
     * z = Atmospheric pressure in hPa
     * @param timestamp event timestamp, ms
     * @param value Atmospheric pressure in hPa (millibar)
     */
    void setPressureValue(long timestamp, float value);

    /**
     * Method to consume location updates.
     * @param timestamp event timestamp, ms
     * @param value Location object
     */
    void setLocationValue(long timestamp, Location value);
}
