package cselp.sensortrack.bean;


import android.location.Location;

/**
 * Container class to hold location data: longitude, latitude, altitude, speed and time.
 */
public class LocationData extends TimeData {
    private static final long serialVersionUID = 7727903418831292004L;
    //exclude from gson serialisation
    public transient Location l;
    //longitude
    public double x;
    //latitude
    public double y;
    //altitude
    public double z;
    //speed
    public double s;

    public LocationData() {
    }

    public LocationData(long t, Location l) {
        this.t = t;
        this.l = l;
        y = l.getLatitude();
        x = l.getLongitude();
        if (l.hasAltitude()) {
            this.z = l.getAltitude();
        }
        if (l.hasSpeed()) {
            this.s = l.getSpeed();
        }
    }

}
