package org.smartregister;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.*;
import lombok.Getter;
import org.smartregister.Location;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({
        "identifier",
        "locations",
        "team",
        "uuid"
})
@Getter
public class Team implements Serializable
{

    @JsonProperty("identifier")
    public String identifier;
    @JsonProperty("locations")
    public List<Location> locations;
    @JsonProperty("team")
    public InnerTeam innerTeam;
    @JsonProperty("uuid")
    public String uuid;
    private final static long serialVersionUID = -2378789669211621510L;

    /**
     * No args constructor for use in serialization
     *
     */
    public Team() {
    }

    /**
     *
     * @param identifier
     * @param locations
     * @param team
     * @param uuid
     */
    public Team(String identifier, List<Location> locations, InnerTeam team, String uuid) {
        super();
        this.identifier = identifier;
        this.locations = locations;
        this.innerTeam = team;
        this.uuid = uuid;
    }

}