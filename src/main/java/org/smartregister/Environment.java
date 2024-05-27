package org.smartregister;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;


@Setter
@Getter
@ToString
public class Environment {
    private String clientId;
    private String clientSecret;
    private String keycloakUrl;
    private String opensrpUrl;
    private String realm;
    private String tokenUrl;

    public Environment(String propertiesFile){
        Properties properties = new Properties();

        try (InputStream inputStream = Files.newInputStream(Paths.get(propertiesFile))){

            properties.load(inputStream);

            // Read a specific key-value pair
            clientId = properties.getProperty("CLIENT_ID");
            clientSecret = properties.getProperty("CLIENT_SECRET");
            keycloakUrl = properties.getProperty("KEYCLOAK_URL");
            opensrpUrl = properties.getProperty("OPENSRP_URL");
            realm = properties.getProperty("REALM");
            tokenUrl = properties.getProperty("TOKEN_URL");

        }
        catch (Exception e) {
            e.printStackTrace();
        }
      
    }

}
