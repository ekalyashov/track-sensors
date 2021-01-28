package cselp.sensortrack.bean;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cselp.sensortrack.Const;

/**
 * Container class to hold all track data from all sensors.
 */
public class TrackData implements Serializable {
    private static final long serialVersionUID = 7727903418831292000L;

    public String uuid = UUID.randomUUID().toString();
    //track start time
    public long start;
    //track end time
    public long end;
    public int partCount = -1;
    public int sensorDataRate = -1;

    //acceleration along each device axis, not including gravity
    public List<SensorData> linearAcc = new ArrayList<>();
    //acceleration along each device axis, including gravity
    public List<SensorData> acceleration = new ArrayList<>();
    public float accelerationDelta = Const.ACCELERATION_DELTA;
    //geomagnetic field in the X, Y and Z axis, in micro-Tesla
    public List<SensorData> compass = new ArrayList<>();
    public float compassDelta = Const.COMPASS_DELTA;
    //gyroscope sensor values
    public List<SensorData> gyroscope = new ArrayList<>();
    public float gyroscopeDelta = Const.GYROSCOPE_DELTA;
    //SensorData contains three dimensional vector indicating the direction and magnitude of gravity
    public List<SensorData> gravity = new ArrayList<>();
    public float gravityDelta = Const.GRAVITY_DELTA;
    //rotation vector values
    public List<RotationVector> rotation = new ArrayList<>();
    public float rotationDelta = Const.ROTATION_DELTA;
    /**
     * SensorData contains: x - altitudeDelta,
     * y - altitude, in meters,
     * z - Atmospheric pressure in hPa
     */
    public List<SensorData> altitude = new ArrayList<>();
    public float altitudeDelta = Const.ALTITUDE_DELTA;
    //location values
    public List<LocationData> location = new ArrayList<>();
    public double locationDelta = Const.LOCATION_DELTA;

    public List<TrackEvent> events = new ArrayList<>();
    //mobile device information
    public PhoneInfo terminal;

    public TrackData() {
    }

    public TrackData(PhoneInfo phoneInfo) {
        this.terminal = phoneInfo;
    }

    public void initFrom(TrackData t) {
        uuid = t.uuid;
        start = t.start;
        end = t.end;
        partCount = t.partCount;
        sensorDataRate = t.sensorDataRate;
        accelerationDelta = t.accelerationDelta;
        compassDelta = t.compassDelta;
        gyroscopeDelta = t.gyroscopeDelta;
        gravityDelta = t.gravityDelta;
        rotationDelta = t.rotationDelta;
        altitudeDelta = t.altitudeDelta;
        locationDelta = t.locationDelta;
        terminal = t.terminal;
    }
}
