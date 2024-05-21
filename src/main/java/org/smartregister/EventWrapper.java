package org.smartregister;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class EventWrapper {
    @JsonProperty("events")
    private List<Event> events;

    @JsonProperty("no_of_events")
    private int noOfEvents;

    // Getters and setters


}
