package cselp.sensortrack.util;

import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Implementation of JsonSerializer, ensures valid float type serialization.
 */
public class JsonFloatSerializer implements JsonSerializer<Float> {
    private int precision;

    public JsonFloatSerializer(int precision) {
        this.precision = precision;
    }

    @Override
    public JsonElement serialize(Float src, Type typeOfSrc, JsonSerializationContext context) {
        try {
            BigDecimal bigValue = BigDecimal.valueOf(src);
            bigValue = bigValue.setScale(precision, RoundingMode.HALF_EVEN);
            return new JsonPrimitive(bigValue);
        } catch (Exception e) {
            Log.e("JsonFloatSerializer", "serialize error", e);
            return new JsonPrimitive("NaN");
        }
    }
}
