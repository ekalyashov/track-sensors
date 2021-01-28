package cselp.sensortrack.util;


import android.location.Location;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

/**
 * A strategy definition to exclude field of Location type from serialization to JSON.
 */
public class LocationExclusionStrategy implements ExclusionStrategy {

    @Override
    public boolean shouldSkipField(FieldAttributes f) {
        return (f.getDeclaredClass().equals(Location.class));
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
        return false;
    }
}
