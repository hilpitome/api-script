package org.smartregister;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        // Specify the resource file name
        String resourceFileName = "/app.properties";


        Environment env = new Environment();
        System.out.println(env);
        AuthenticationBody authBody = new AuthenticationBody();
        authBody.setScope("openid");
        authBody.setClientId(env.getClientId());
        authBody.setUsername(env.getUsername());
        authBody.setPassword(env.getPassword());
        authBody.setClientSecret(env.getClientSecret());
        authBody.setGrantType("password");

        // Convert AuthenticationBody to URL-encoded string
        String formBody = "scope=" + authBody.getScope()
                + "&username=" + authBody.getUsername()
                + "&password=" + authBody.getPassword()
                + "&grant_type=" + authBody.getGrantType()
                + "&client_id=" + authBody.getClientId()
                + "&client_secret=" + authBody.getClientSecret();

        // Serialize AuthenticationBody to JSON
        ObjectMapper objectMapper;


        // Create OkHttp client
        OkHttpClient client = new OkHttpClient();

        // Create the request body
        RequestBody body = RequestBody.create(formBody, MediaType.get("application/x-www-form-urlencoded"));

        Request request = new Request.Builder()
                .url("https://anckeycloak-stage.ths.rw/auth/realms/who-anc-stage-docker/protocol/openid-connect/token")
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
        try(Response response = client.newCall(request).execute();){

            // response from keycloak
            // Deserialize the response body to AuthResponse
            objectMapper = new ObjectMapper();
            AuthResponse authResponse = objectMapper.readValue(response.body().string(), AuthResponse.class);
            System.out.println("access_token "+authResponse.getAccessToken());
            RequestBody eventRequestParams = RequestBody.create(formBody, MediaType.get("application/x-www-form-urlencoded"));
            String url = HttpUrl.parse("https://ancopensrp-stage.ths.rw/rest/event/sync")
                    .newBuilder()
                    .addQueryParameter("limit","250")
                    .addQueryParameter("teamId", "f2c988ff-8a0c-4cf8-91ad-7dadf01d2b93")
                    .addQueryParameter("serverVersion", "0")
                    .addQueryParameter("access_token", authResponse.getAccessToken())
                    .build()
                    .toString();
            Request request1 = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response1 = client.newCall(request1).execute();){
                System.out.println("is successful "+response1.isSuccessful());

                EventWrapper eventWrapper = objectMapper.readValue(response1.body().bytes(),EventWrapper.class);
                List<Event> events = eventWrapper.getEvents();

                // Filter and collect the results
                List<Event> counsellingAndTreatmentWithLstVisitDate = events.stream()
                        .filter(event -> "Counselling and Treatment".equals(event.getEventType()))
                        .filter(event -> event.getObs().stream()
                                .anyMatch(obs -> "lst_visit_date".equals(obs.getFormSubmissionField())))
                        .collect(Collectors.toList());

                HashMap<String, Event> counsellingAndTreatmentWithLstVisitDateMap = new HashMap<>();
                counsellingAndTreatmentWithLstVisitDate
                        .forEach(event -> counsellingAndTreatmentWithLstVisitDateMap
                                .put(event.getBaseEntityId()+"-"+event.getDetails().get("contact_no"), event)
                        );


//			System.out.println("====================================================");
                List<Event> filteredQuickCheckEvents = events.stream().filter( event -> "Quick Check".equals(event.getEventType()))
                        .collect(Collectors.toList());
                filteredQuickCheckEvents.forEach( event -> {
                    String lookupKey = event.getBaseEntityId()+"-"+event.getDetails().get("contact_no");
                    // get corresponding  counselling and treatment visit date
                    Event counsellingAndTreatmentEvent = counsellingAndTreatmentWithLstVisitDateMap.get(lookupKey);
                    // get visit date object
                    if(!(counsellingAndTreatmentEvent == null)){
                        List<Obs>  observation = counsellingAndTreatmentEvent.getObs().stream()
                                .filter(obs -> "lst_visit_date".equals(obs.getFormSubmissionField()))
                                .collect(Collectors.toList());
                        Obs newOb = observation.get(0);
                        newOb.setFormSubmissionField("visit_date");
                        System.out.println("before lst_visit");
                        System.out.println(event.getObs().size());
                        event.getObs().add(newOb);
                        System.out.println(event.getObs().size());
                        System.out.println("after appending lst_visit");
                    }

                });
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}
