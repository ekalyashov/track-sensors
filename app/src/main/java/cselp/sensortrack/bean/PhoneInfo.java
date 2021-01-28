package cselp.sensortrack.bean;

import java.io.Serializable;

/**
 * Container class to hold mobile device info.
 */
public class PhoneInfo implements Serializable {
    private static final long serialVersionUID = 7727903418831292006L;

    private String version;
    private String build;
    private String model;
    private String manufacturer;
    private String deviceId;

    public PhoneInfo(String version, String buildNumber, String model, String manufacturer) {
        this.version = version;
        this.build = buildNumber;
        this.model = model;
        this.manufacturer = manufacturer;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBuild() {
        return build;
    }

    public void setBuild(String build) {
        this.build = build;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}
