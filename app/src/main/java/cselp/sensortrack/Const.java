package cselp.sensortrack;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Class - container for common constants.
 */
public class Const {

    public static final float ACCELERATION_DELTA = 0.1f;
    public static final float GYROSCOPE_DELTA = 0.1f;
    public static final float GRAVITY_DELTA = 0.1f; //in m/s^2 ~ 0.01 rad of phone inclination
    public static final float COMPASS_DELTA = 2.0f;
    public static final float ROTATION_DELTA = 0.02f;
    public static final long SENSOR_TIME_DELTA = 5000; // 5 sec
    public static final float ALTITUDE_DELTA = 0.1f; //10 cm
    public static final float LOCATION_DELTA = 0.1f; //10 cm

    public static final int ALTITUDE_MEAN_COUNT = 3;

    public static final int TRACK_PART_STEP = 1000;
    public static final int MAX_ACCELERATION_LIST_SIZE = 40000;

    public static final String APPLICATION_SETTINGS = "SENSOR_TRACK_APPLICATION_SETTINGS";
    public static final String TRACK_SERVER_URL_KEY = "TRACK_SERVER_URL_KEY";
    public static final String SENSOR_DELAY_KEY = "SENSOR_DELAY_KEY";
    public static final String ACCELERATION_DELTA_KEY = "ACCELERATION_DELTA_KEY";
    //interval in which we want to get locations
    public static final long LOCATION_UPDATE_INTERVAL = 1000;  /* 1 secs */
    public static final long LOCATION_FASTEST_INTERVAL = 100; /* 0.1 sec */

    public static final String URL_ADD_TRACK_PART = "/track/part";
    public static final String URL_PING = "/ping";

    /**
     * Enumeration of action types for interactivity communications
     */
    public interface Actions {
        String GPS = "sensortrack.GPS_STATE";
        String LOCATION = "sensortrack.LOCATION_UPDATE";
        String STATUS = "sensortrack.TRACK_STATUS";
        String FILTER_RATIO = "sensortrack.FILTER_RATIO";
        String COMMAND = "sensortrack.ACTION_COMMAND";
    }

    /**
     * enumeration of allowed commands, see Actions.COMMAND
     */
    public interface Command {
        String TRACK_START = "TRACK_START";
        String TRACK_STOP = "TRACK_STOP";
        String TRACK_STOPPING = "TRACK_STOPPING";
    }

    public enum TrackEventType {
        PIT,
        IRREGULARITY,
        HILL,
        STEP_UP,
        STEP_DOWN
    }

}
