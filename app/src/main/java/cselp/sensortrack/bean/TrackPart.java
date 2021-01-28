package cselp.sensortrack.bean;

/**
 * Container class to hold part of track data from all sensors.
 * See {@link cselp.sensortrack.bean.TrackData TrackData}
 */
public class TrackPart extends TrackData {
    private static final long serialVersionUID = 7727903418831292007L;

    //part serial number
    public int partNum;
    //part start time
    public long partStart;
    //part end time
    public long partEnd;

    public TrackPart() {
    }

    public TrackPart(PhoneInfo phoneInfo) {
        super(phoneInfo);
    }
}
