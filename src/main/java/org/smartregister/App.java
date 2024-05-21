package org.smartregister;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
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
                .url(env.getTokenUrl())
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
        int count = 250;
        long lastServerVersion = 0;
        List<Event> eventsToPost = new ArrayList<>();
        AuthResponse authResponse;
        try(Response response = client.newCall(request).execute();) {

            // response from keycloak
            // Deserialize the response body to AuthResponse
            objectMapper = new ObjectMapper();
            authResponse = objectMapper.readValue(response.body().string(), AuthResponse.class);
            System.out.println("access_token " + authResponse.getAccessToken());
        } catch (IOException e) {
                    throw new RuntimeException(e);
        }
        do {

                String url = HttpUrl.parse(env.getOpensrpUrl()+"/rest/event/sync")
                        .newBuilder()
                        .addQueryParameter("limit","250")
                        .addQueryParameter("teamId", "f2c988ff-8a0c-4cf8-91ad-7dadf01d2b93")
                        .addQueryParameter("serverVersion", String.valueOf(lastServerVersion))
                        .addQueryParameter("access_token", authResponse.getAccessToken())
                        .build()
                        .toString();
                Request request1 = new Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .build();


                try (Response response1 = client.newCall(request1).execute();){

                    EventWrapper eventWrapper = objectMapper.readValue(response1.body().bytes(),EventWrapper.class);
                    List<Event> events = eventWrapper.getEvents();
                    count = eventWrapper.getNoOfEvents();
                    if(count>0){
                        System.err.println("no of events "+count);
                        lastServerVersion = events.get(events.size()-1).getServerVersion();


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
                                newOb.setFormSubmissionField("visit_date");;
                                event.getObs().add(newOb);
                                eventsToPost.add(event);
                            }
                        });

                        List<Event> filteredProfileEvents = events.stream().filter( event -> "Profile".equals(event.getEventType()))
                                .collect(Collectors.toList());
                        filteredProfileEvents.forEach( profileEvent -> {
                            String lookupKey = profileEvent.getBaseEntityId()+"-"+profileEvent.getDetails().get("contact_no");
                            // get corresponding  counselling and treatment visit date
                            Event counsellingAndTreatmentEvent = counsellingAndTreatmentWithLstVisitDateMap.get(lookupKey);
                            // get visit date object
                            if(!(counsellingAndTreatmentEvent == null)){
                                final String[] manualEncounterDate = new String[1];
                                final String[] lmpDone= new String[1];
                                final String[] ultrasoundDone= new String[1];
                                final String[] lmpDateString = new String[1];
                                final String[] ultrasoundDateString= new String[1];
                                final String[] ultrasoundWeeks= new String[1];
                                final String[] ultrasoundDays= new String[1];

                                counsellingAndTreatmentEvent.getObs().forEach((obs -> {
                                    if(Objects.equals(obs.getFormSubmissionField(), "visit_date")){
                                        manualEncounterDate[0] = (String) obs.getValues().get(0);

                                    } else if(Objects.equals(obs.getFormSubmissionField(), "lst_visit_date")){
                                        manualEncounterDate[0] = (String) obs.getValues().get(0);
                                    }
                                }));

                                profileEvent.getObs().forEach(obs -> {
                                    if(Objects.equals(obs.getFormSubmissionField(), "lmp_known_date")){
                                        lmpDateString[0] = (String) obs.getValues().get(0);
                                    }
                                    if(Objects.equals(obs.getFormSubmissionField(), "lmp_known")){
                                        lmpDone[0] = (String) obs.getHumanReadableValues().get(0);
                                    }
                                    if(Objects.equals(obs.getFormSubmissionField(), "ultrasound_done")){
                                        ultrasoundDone[0] = (String) obs.getHumanReadableValues().get(0);
                                    }
                                    if(Objects.equals(obs.getFormSubmissionField(), "ultrasound_done_date")){
                                        ultrasoundDateString[0] = (String) obs.getValues().get(0);
                                    }
                                    if(Objects.equals(obs.getFormSubmissionField(), "ultrasound_gest_age_wks")){
                                        ultrasoundWeeks[0] = (String) obs.getValues().get(0);
                                    }
                                    if(Objects.equals(obs.getFormSubmissionField(), "ultrasound_gest_age_days")){
                                        ultrasoundDays[0] = (String) obs.getValues().get(0);
                                    }
                                });

                                // if lmpDone gestationalAge
                                if(lmpDone[0] != null && !lmpDone[0].isEmpty() && lmpDone[0].equals("yes") && !lmpDateString[0].equals("0")){
                                    String lmpGestationalAge = Utils.lmpGestationalAge(lmpDateString[0], manualEncounterDate[0]);
                                    /*
                                     create lmp_edd obs e.g
                                      {
                                            "fieldCode": "lmp_gest_age",
                                            "fieldDataType": "text",
                                            "fieldType": "formsubmissionField",
                                            "formSubmissionField": "lmp_gest_age",
                                            "humanReadableValues": [],
                                            "parentCode": "",
                                            "saveObsAsArray": false,
                                            "set": [],
                                            "values": [
                                                "37 weeks 3 days"
                                            ]
                                        }
                                    */
                                    Obs ob = new Obs();
                                    ob.setFieldCode("lmp_gest_age");
                                    ob.setFormSubmissionField("lmp_gest_age");
                                    ob.setFieldType("formsubmissionField");
                                    List<Object> values =  new ArrayList<>();
                                    values.add(lmpGestationalAge);
                                    ob.setValues(values);
                                    profileEvent.getObs().add(ob);
                                } else if(ultrasoundDone[0] != null && !ultrasoundDateString[0].equals("0")){
                                    if(ultrasoundDays[0]!= null && ultrasoundWeeks[0] != null)
                                    {
                                        String ultrasoundEdd = Utils.calculateEddUltrasound(ultrasoundDateString[0], ultrasoundWeeks[0], ultrasoundDays[0]);
                                    /*
                                    *       {
                                            "fieldCode": "ultrasound_edd",
                                            "fieldDataType": "text",
                                            "fieldType": "formsubmissionField",
                                            "formSubmissionField": "ultrasound_edd",
                                            "humanReadableValues": [],
                                            "parentCode": "",
                                            "saveObsAsArray": false,
                                            "set": [],
                                            "values": [
                                                "0"
                                            ]
                                        }
                                        * */
                                        Obs ob = new Obs();
                                        ob.setFieldCode("ultrasound_edd");
                                        ob.setFormSubmissionField("ultrasound_edd");
                                        ob.setFieldType("formsubmissionField");
                                        List<Object> values =  new ArrayList<>();
                                        values.add(ultrasoundEdd);
                                        ob.setValues(values);
                                        profileEvent.getObs().add(ob);

                                    }
                                    eventsToPost.add(profileEvent);
                                }
//                                List<Obs>  observation = counsellingAndTreatmentEvent.getObs().stream()
//                                        .filter(obs -> "visit_date".equals(obs.getFormSubmissionField()))
//                                        .collect(Collectors.toList());
//                                if(!observation.isEmpty()){
//                                    Obs newOb = observation.get(0);
//                                    String manualEncounterDate = (String) newOb.getValues().get(0);
//                                }

                            }
                        });
                    }


                } catch (IOException e) {
                    throw new RuntimeException(e);
                }


        } while (count>0);


        // Define the chunk size
        int chunkSize = 250;
        System.out.println(eventsToPost.size()+"eventsize");

        // Loop through the ArrayList in chunks
        for (int i = 0; i < eventsToPost.size(); i += chunkSize) {
            // Calculate the end index of the current chunk
            int end = Math.min(i + chunkSize, eventsToPost.size());

            // Get the sublist representing the current chunk
            List<Event> chunk = eventsToPost.subList(i, end);
            EventWrapper wrapper = new EventWrapper();
            wrapper.setEvents(chunk);


            String url = HttpUrl.parse(env.getOpensrpUrl()+"/rest/event/add")
                    .newBuilder()
                    .addQueryParameter("access_token", authResponse.getAccessToken())
                    .build()
                    .toString();
            String json=null;
            try {
                json = objectMapper.writeValueAsString(wrapper);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            if (json != null) {
                // Send JSON to HTTP endpoint using OkHtt

                RequestBody requestBody = RequestBody.create(json, MediaType.parse("application/json"));
                Request request1 = new Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build();

                client.newCall(request1).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            System.out.println("Response: " + response.body().string());
                        } else {
                            System.out.println("Request failed: " + response);
                        }
                    }
                });
            }
        }


    }

}
