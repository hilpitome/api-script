package org.smartregister;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Event {
    @JsonIgnore
    private String eventId;
    @JsonProperty
    private Map<String, String> identifiers;
    @JsonProperty
    private String baseEntityId;
    @JsonProperty
    private String locationId;
    @JsonProperty
    private String eventType;
    @JsonProperty
    private String formSubmissionId;
    @JsonProperty
    private String providerId;
    @JsonProperty
    private String status;
    @JsonProperty
    private String priority;
    @JsonProperty
    private List<String> episodeOfCare;
    @JsonProperty
    private List<String> referrals;

    @JsonProperty
    private long serverVersion;
    @JsonProperty
    private String category;
    @JsonProperty
    private int duration;
    @JsonProperty
    private String reason;
    @JsonProperty
    private List<Obs> obs;
    @JsonProperty
    private String entityType;
    @JsonProperty
    private Map<String, String> details;
    @JsonProperty
    private long version;
    @JsonProperty
    private String teamId;
    @JsonProperty
    private String team;
    @JsonProperty
    private String childLocationId;

    // Getters and setters

    public String getBaseEntityId() {
        return baseEntityId;
    }

    public void setBaseEntityId(String baseEntityId) {
        this.baseEntityId = baseEntityId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public List<Obs> getObs() {
        return obs;
    }

    public void setObs(List<Obs> obs) {
        this.obs = obs;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }

    @JsonProperty("serverVersion") // Include this during deserialization
    public long getServerVersion() {
        return serverVersion;
    }
    @JsonProperty("serverVersion") // Include this during deserialization
    public void setServerVersion(long serverVersion) {
        this.serverVersion = serverVersion;
    }

    @JsonIgnore // Exclude this during serialization
    public long getServerVersionForSerialization() {
        return serverVersion;
    }
}
