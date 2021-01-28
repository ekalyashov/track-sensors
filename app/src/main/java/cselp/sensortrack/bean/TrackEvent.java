package cselp.sensortrack.bean;

import cselp.sensortrack.Const;

/**
 * Container class to hold event type and time.
 */
public class TrackEvent extends TimeData {
    private static final long serialVersionUID = 7727903418831292005L;

    public Const.TrackEventType type;

    public TrackEvent(long t, Const.TrackEventType type) {
        this.t = t;
        this.type = type;
    }
}
