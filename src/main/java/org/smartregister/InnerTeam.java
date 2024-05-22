package org.smartregister;

import java.io.Serializable;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "teamName",
        "organizationIds",
        "display",
        "location",
        "uuid"
})
@Getter
public class InnerTeam implements Serializable
{

    @JsonProperty("teamName")
    public String teamName;
    @JsonProperty("organizationIds")
    public List<Double> organizationIds;
    @JsonProperty("display")
    public String display;
    @JsonProperty("location")
    public Location location;
    @JsonProperty("uuid")
    public String uuid;
    private final static long serialVersionUID = -4828707948539446772L;

    /**
     * No args constructor for use in serialization
     *
     */
    public InnerTeam() {
    }

    /**
     *
     * @param teamName
     * @param organizationIds
     * @param display
     * @param location
     * @param uuid
     */
    public InnerTeam(String teamName, List<Double> organizationIds, String display, Location location, String uuid) {
        super();
        this.teamName = teamName;
        this.organizationIds = organizationIds;
        this.display = display;
        this.location = location;
        this.uuid = uuid;
    }

}