package cselp.sensortrack.bean;

import cselp.sensortrack.util.DataUtil;

/**
 * Container class to hold acceleration sensor data.
 * Implemented logic to minimise volume of data written to storage.
 * Limits defined by 'delta' between adjacent meaningful data points
 * and maximum period between unsaved data points.
 */
public class DataHolder {
    private long previousTime;
    float[] previousData;
    boolean previousStored = false;
    private long lastTime;
    float[] lastData;
    boolean lastStored = false;
    private long lastSavedTime;
    float[] lastStoredData;
    private float delta;
    private long maxUnsavedPeriod;
    private int dimension = 3;

    public DataHolder(float delta, long maxUnsavedPeriod) {
        this.delta = delta;
        this.maxUnsavedPeriod = maxUnsavedPeriod;
    }

    public void setDelta(float delta) {
        this.delta = delta;
    }

    /**
     * Drops object state.
     */
    public void clear() {
        previousData = null;
        previousStored = true;
        lastData = null;
        lastStored = true;
        lastStoredData = null;
        lastSavedTime = 0;
    }

    /**
     * Append supplied data to inner holder.
     * @param timestamp data point time
     * @param data array of data
     */
    public void setLastData(long timestamp, float[] data) {
        if (previousData == null) {
            previousData = new float[dimension];
            previousStored = true;
        }
        if (lastData == null) {
            lastData = new float[dimension];
            lastStored = true;
        }
        previousTime = lastTime;
        System.arraycopy(lastData, 0, previousData, 0, dimension);
        previousStored = lastStored;
        lastTime = timestamp;
        System.arraycopy(data, 0, lastData, 0, dimension);
        lastStored = false;
    }

    /**
     * Check if inner data should be stored.
     * @return true if inner data should be stored
     */
    public boolean shouldStore() {
        if ((lastTime - lastSavedTime) >= maxUnsavedPeriod) {
            return true;
        }
        if (lastStoredData == null) {
            return (lastData != null);
        }
        return (DataUtil.distance(lastStoredData, lastData, 3) > delta);
    }

    /**
     * Returns array of SensorData to store.
     * @return array of SensorDat
     */
    public SensorData[] valuesToStore() {
        if (previousData == null) {
            previousStored = true;
        }
        if (lastData == null) {
            lastStored = true;
        }
        int l = previousStored && lastStored ? 0 : ((previousStored || lastStored) ? 1 : 2);
        SensorData[] res = new SensorData[l];
        if (!previousStored) {
            res[0] = new SensorData(previousTime, previousData[0], previousData[1], previousData[2]);
            previousStored = true;
        }
        if (!lastStored) {
            int pos = l > 1 ? 1 : 0;
            res[pos] = new SensorData(lastTime, lastData[0], lastData[1], lastData[2]);
            lastStored = true;
            //set last saved data
            if (lastStoredData == null) {
                lastStoredData = new float[dimension];
            }
            System.arraycopy(lastData, 0, lastStoredData, 0, dimension);
            lastSavedTime = lastTime;
        }
        return res;
    }
}
