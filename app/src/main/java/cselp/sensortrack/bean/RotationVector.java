package cselp.sensortrack.bean;

/**
 * Container class to hold rotation data:
 * x*sin(&#952/2), y*sin(&#952/2), z*sin(&#952/2),
 * cos(&#952/2), accuracy and time.
 * See {@link android.hardware.SensorEvent#values SensorEvent.values}
 */
public class RotationVector extends SensorData {
    private static final long serialVersionUID = 7727903418831292003L;

    /**
     * cos(&#952/2)
     */
    public float cos;
    /**
     * estimated heading Accuracy (in radians) (-1 if unavailable)
     */
    public float acc;

    public RotationVector(long t, float x, float y, float z, float cos, float accuracy) {
        super(t, x, y, z);
        this.cos = cos;
        this.acc = accuracy;
    }
}
