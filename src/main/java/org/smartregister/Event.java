package org.smartregister;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Event {
    @JsonProperty("baseEntityId")
    private String baseEntityId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("obs")
    private List<Obs> obs;

    @JsonProperty("details")
    private Map<String, String> details;

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


}
