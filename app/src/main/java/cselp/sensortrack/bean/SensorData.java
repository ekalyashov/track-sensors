package cselp.sensortrack.bean;

/**
 * Container class to hold sensor data: 3 coordinates and time.
 */
public class SensorData extends TimeData {
    private static final long serialVersionUID = 7727903418831292002L;

    public float x;
    public float y;
    public float z;

    public SensorData(long t, float x, float y, float z) {
        this.t = t;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
