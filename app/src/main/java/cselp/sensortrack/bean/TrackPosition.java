package cselp.sensortrack.bean;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class contains list of positions for all sensor data lists.
 * Used for filling of TrackPart object.
 */
public class TrackPosition {
    public AtomicInteger partNum = new AtomicInteger(0);
    //position in acceleration list
    public AtomicInteger accIdx = new AtomicInteger(0);
    //position in altitude list
    public AtomicInteger altIdx = new AtomicInteger(0);
    //position in compass list
    public AtomicInteger cmpIdx = new AtomicInteger(0);
    //position in gravity list
    public AtomicInteger gravIdx = new AtomicInteger(0);
    //position in gyroscope list
    public AtomicInteger gyroIdx = new AtomicInteger(0);
    //position in location list
    public AtomicInteger locIdx = new AtomicInteger(0);
    //position in rotation list
    public AtomicInteger rotIdx = new AtomicInteger(0);
    //position in event list
    public AtomicInteger eventIdx = new AtomicInteger(0);

    public int partCount;

    public <T extends TimeData> void copyData(List<T> from, List<T> to, AtomicInteger idx) {
        if (idx.get() < from.size()) {
            int start = idx.getAndSet(from.size());
            to.addAll(from.subList(start, idx.get()));
        }
    }

}