package org.smartregister;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class User {


    @JsonProperty
    private String username;
    private String password;
    private String teamId;
    private int numberOfContactVisits = 0;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
