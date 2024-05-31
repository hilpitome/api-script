package org.smartregister;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kotlin.Pair;
import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Hello world!
 */
public class App {
    protected static final Logger logger = Logger.getLogger(App.class.getName());
    private static int lstVisitDateEventsFound;
    private static int addGestationalEventCount;

    public static void main(String[] args) {
        System.out.println(App.class.getName());

        /**
         * Process CLI params
         * to specify properties use -p=</path/to/app.properties>
         *     to specify credentials use -c=</path/to/user_credentials.csv>
         */
        try (InputStream fileInputStream = Files.newInputStream(Paths.get("src/main/resources/java.logging.properties"))) {
            LogManager.getLogManager().readConfiguration(fileInputStream);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }

        String appPropertiesFilePath = "";
        String userCredentialsFilePath = "";
        String token;
        for (int i = 0; i < args.length; i++) {
            token = args[i].trim();
            userCredentialsFilePath = token.startsWith("-c") ? token.substring(token.indexOf('=') + 1) : userCredentialsFilePath;
            appPropertiesFilePath = token.startsWith("-p") ? token.substring(token.indexOf('=') + 1) : appPropertiesFilePath;
        }

        Environment env = new Environment(appPropertiesFilePath);
        CSVReader csvReader = new CSVReader();
        logger.log(Level.INFO, "reading user credentials csv files");
        List<User> users = csvReader.readUsersFromCSV(userCredentialsFilePath);

        users.forEach(user -> {
            /**
             * User Authentication
             */

            System.out.println();
            logger.log(Level.INFO, "============================================================");
            logger.log(Level.INFO, "Processing data for user with username " + user.getUsername());
            logger.log(Level.INFO, "============================================================");
            System.out.println();

            // Convert AuthenticationBody to URL-encoded string
            String formBody = "scope=" + "openid"
                    + "&username=" + user.getUsername()
                    + "&password=" + user.getPassword()
                    + "&grant_type=" + "password"
                    + "&client_id=" + env.getClientId()
                    + "&client_secret=" + env.getClientSecret();

            // Serialize AuthenticationBody to JSON
            ObjectMapper objectMapper = null;

            // Create OkHttp client
            OkHttpClient client = new OkHttpClient();

            // Create the request body
            RequestBody body = RequestBody.create(formBody, MediaType.get("application/x-www-form-urlencoded"));

            Request request = new Request.Builder()
                    .url(env.getTokenUrl())
                    .post(body)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();
            logger.log(Level.INFO, env.getTokenUrl());

            AuthResponse authResponse = null;
            logger.log(Level.INFO, "AUTHENTICATION");
            logger.log(Level.INFO, "authenticating user with username " + user.getUsername());
            try (Response response = client.newCall(request).execute()) {

                // response from keycloak
                // Deserialize the response body to AuthResponse
                objectMapper = new ObjectMapper();
                authResponse = objectMapper.readValue(response.body().string(), AuthResponse.class);
                logger.log(Level.INFO, "successfully authenticated to Keycloak and obtained access_token ");

                String url = HttpUrl.parse(env.getOpensrpUrl() + "/security/authenticate")
                        .newBuilder()
                        .addQueryParameter("access_token", authResponse.getAccessToken())
                        .build()
                        .toString();
                Request opensrpAuthRequest = new Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .build();
                logger.log(Level.INFO, url);
                logger.log(Level.INFO, "auth token");
                try (Response openSRPAuthResponse = client.newCall(opensrpAuthRequest).execute()) {
                    OpenSRPAuthResponse opensrpAuth = objectMapper.readValue(openSRPAuthResponse.body().string(), OpenSRPAuthResponse.class);
                    logger.log(Level.INFO, opensrpAuth.toString());

                    user.setTeamId(opensrpAuth.getTeam().getInnerTeam().getUuid());
                    logger.log(Level.INFO, "succesfully retrieved team id as " + user.getTeamId() + " for user " + user.getUsername());

                } catch (IOException e) {

                    logger.log(Level.SEVERE, "failed to get team id for user " + user.getUsername());
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }

            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
            }

            /***
             * Sync in Batches
             */

            System.out.println();
            logger.log(Level.INFO, "DATA FETCH");
            int lastEventCountReceived = 0;
            long lastServerVersion = 0;
            long eventPullLimit = 250; //Batch Size
            List<Event> eventsToPost = new ArrayList<>();
            int i = 0;

            String hostUrl = env.getOpensrpUrl() + "/rest/event/sync";
            logger.log(Level.INFO, "server host url - " + hostUrl);

            List<Event> counsellingAndTreatmentWithLstVisitDate = new ArrayList<>();
            HashMap<String, Event> counsellingAndTreatmentWithLstVisitDateMap = new HashMap<>();
            List<Event> filteredContactVisitEvents = new ArrayList<>();
            HashMap<String, Event> contactsVisitEventsMap = new HashMap<>();
            List<Event> profileEvents = new ArrayList<>();
            List<Event> filteredQuickCheckEvents = new ArrayList<>();
            do {

                String url = HttpUrl.parse(hostUrl)
                        .newBuilder()
                        .addQueryParameter("limit", String.valueOf(eventPullLimit))
                        .addQueryParameter("teamId", user.getTeamId())
                        .addQueryParameter("serverVersion", String.valueOf(lastServerVersion))
                        .addQueryParameter("access_token", authResponse.getAccessToken())
                        .build()
                        .toString();
                Request request1 = new Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .build();

                logger.log(Level.INFO, "fetching events for batch request number " + (i + 1));
                try (Response response1 = client.newCall(request1).execute()) {
                    logger.log(Level.INFO, "processing response for event batch " + (i + 1));
                    i++;
                    EventWrapper eventWrapper = objectMapper.readValue(response1.body().bytes(), EventWrapper.class);
                    List<Event> events = eventWrapper.getEvents();
                    lastEventCountReceived = eventWrapper.getNoOfEvents();
                    if (lastEventCountReceived > 0) {
                        logger.log(Level.INFO, "no of events received " + lastEventCountReceived);

                        Pair<Long, Long> serverVersionPair = SyncUtils.getMinMaxServerVersions(events);
                        lastServerVersion = serverVersionPair.getSecond();

                        // Filter and collect the results
                        counsellingAndTreatmentWithLstVisitDate.addAll(events.stream()
                                .filter(event -> "Counselling and Treatment".equals(event.getEventType()))
                                .filter(event -> event.getObs().stream()
                                        .anyMatch(obs -> "lst_visit_date".equals(obs.getFormSubmissionField())))
                                .collect(Collectors.toList()));

                        counsellingAndTreatmentWithLstVisitDate.stream().filter(e->e.getBaseEntityId().equals("f8679c26-0c6b-4563-9972-af8329649dbd"))
                                .forEach(e -> logger.log(Level.WARNING, "################################################################  Event Type: "+e.getEventType() + "Base entity Id:  "+e.getBaseEntityId())
                                );


                        System.out.println();
                        logger.log(Level.INFO, "FILTERED " + counsellingAndTreatmentWithLstVisitDate.size() + " counselling and treatment events" +
                                " with lst_visit_date");

                        counsellingAndTreatmentWithLstVisitDate
                                .forEach(event -> counsellingAndTreatmentWithLstVisitDateMap
                                        .put(event.getBaseEntityId(), event)
                                );

                        logger.log(Level.INFO, "created hashmap of baseentityids-concactno for counselling and treatment events");

                        filteredContactVisitEvents.addAll(events.stream()
                                .filter(event -> "Contact Visit".equals(event.getEventType()))
                                .collect(Collectors.toList()));
                        logger.log(Level.INFO, +filteredContactVisitEvents.size() + " Contact Visit events");

                        filteredContactVisitEvents
                                .forEach(contactVisitEvent -> {
                                            String contactNo = Utils.getContactNumberFromDetailsMap(contactVisitEvent.getDetails(), contactVisitEvent.getBaseEntityId());
                                            String lookupKey = contactVisitEvent.getBaseEntityId();
                                            contactsVisitEventsMap
                                                    .put(lookupKey, contactVisitEvent);
                                        }
                                );
                        logger.info("created hashmap of baseentityids-concactno for Contact Visit events");


                        filteredQuickCheckEvents.addAll(events.stream().filter(event -> "Quick Check".equals(event.getEventType()))
                                .collect(Collectors.toList()));

                        logger.log(Level.INFO, "FILTERED " + filteredQuickCheckEvents.size() + " Quick Check events");

                        profileEvents.addAll(events.stream().filter(event -> "Profile".equals(event.getEventType()))
                                .collect(Collectors.toList()));
                        logger.log(Level.INFO, "FILTERED " + profileEvents.size() + " Profile events");

                        counsellingAndTreatmentWithLstVisitDate.forEach(e -> {

                            if(e.getBaseEntityId().equals("f8679c26-0c6b-4563-9972-af8329649dbd"))
    logger.log(Level.WARNING, "################################################################  Event Type: "+e.getEventType() + "Base entity Id:  "+e.getBaseEntityId());
});



                    }


                } catch (IOException e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }

            } while (lastEventCountReceived == eventPullLimit);

            profileEvents.forEach(profileEvent -> {
                String lookupKey = profileEvent.getBaseEntityId();
                // get corresponding  counselling and treatment visit date
                Event counsellingAndTreatmentEvent = counsellingAndTreatmentWithLstVisitDateMap.get(lookupKey);
                // get visit date object
                if (!(counsellingAndTreatmentEvent == null)) {
                    final String[] manualEncounterDate = new String[1];
                    final String[] lmpDone = new String[1];
                    final String[] ultrasoundDone = new String[1];
                    final String[] lmpDateString = new String[1];
                    final String[] ultrasoundEddDateString = new String[1];
                    final String[] sfhGaString = new String[1];
                    final String[] lmpGaString = new String[1];
                    final String[] ultrasoundGaString = new String[1];
                    String baseEntityId = profileEvent.getBaseEntityId();
                    System.out.println();
                    logger.log(Level.INFO, "mutating Profile event for baseEntityId " + baseEntityId +
                            " with data from counselling and treatment event "
                            + lookupKey);

                    manualEncounterDate[0] = getObservationValueByFormSubmissionField(counsellingAndTreatmentEvent, "lst_visit_date");

                    logger.log(Level.INFO, manualEncounterDate[0] == null ? "not Found manual encounter date from counsellingAndTreatmentEvent lst_visit_date" : "Found manual encounter date " + manualEncounterDate[0] + " from counsellingAndTreatmentEvent lst_visit_date");

                    profileEvent.getObs().forEach(obs -> {
                        if (Objects.equals(obs.getFormSubmissionField(), "lmp_known_date")) {
                            lmpDateString[0] = (String) obs.getValues().get(0);
                            logger.log(Level.INFO, "found lmp_known_date " + lmpDateString[0] + " for event with baseEntityId " + baseEntityId);
                        }
                        if (Objects.equals(obs.getFormSubmissionField(), "lmp_known")) {
                            lmpDone[0] = (String) obs.getHumanReadableValues().get(0);
                            logger.log(Level.INFO, "found lmp_known " + lmpDone[0] + " for event with baseEntityId " + baseEntityId);
                        }
                        if (Objects.equals(obs.getFormSubmissionField(), "ultrasound_done")) {
                            ultrasoundDone[0] = (String) obs.getHumanReadableValues().get(0);
                            logger.log(Level.INFO, "found ultrasound_done " + ultrasoundDone[0] + " for event with baseEntityId " + baseEntityId);
                        }
                        if (Objects.equals(obs.getFormSubmissionField(), "ultrasound_edd")) {
                            ultrasoundEddDateString[0] = (String) obs.getValues().get(0);
                            logger.log(Level.INFO, "found ultrasound_edd date " + ultrasoundEddDateString[0] + " for event with baseEntityId " + baseEntityId);
                        }
                        if (Objects.equals(obs.getFormSubmissionField(), "sfh_gest_age")) {
                            sfhGaString[0] = (String) obs.getValues().get(0);
                            logger.log(Level.INFO, "found sfh_gest_age " + sfhGaString[0] + " for event with baseEntityId " + baseEntityId);
                        }
                        if (Objects.equals(obs.getFormSubmissionField(), "lmp_gest_age")) {
                            lmpGaString[0] = (String) obs.getValues().get(0);
                            logger.log(Level.INFO, "found lmp_gest_age " + sfhGaString[0] + " for event with baseEntityId " + baseEntityId);
                        }
                        if (Objects.equals(obs.getFormSubmissionField(), "ultrasound_gest_age")) {
                            ultrasoundGaString[0] = (String) obs.getValues().get(0);
                            logger.log(Level.INFO, "found ultrasound_gest_age " + ultrasoundGaString[0] + " for event with baseEntityId " + baseEntityId);
                        }

                    });

                    String selectedGaEddType = getObservationValueByFormSubmissionField(profileEvent, "select_gest_age_edd");

                    // if lmpDone gestationalAge
                    if (lmpDone[0] != null && lmpDone[0].equals("yes") && !lmpDateString[0].equals("0")) {


                        String lmpGestationalAge = Utils.lmpGestationalAge(lmpDateString[0], manualEncounterDate[0]);

                        if (!lmpGestationalAge.equals("0")) {

                            Obs ob = createOb(lmpGestationalAge, "lmp_gest_age");
                            addEventObs(profileEvent, ob);

                            logger.log(Level.INFO, "added lmp_gest_age obs " + ob + " to Profile Event");

                            if (GuestAgeEddEnum.LMP.equals(selectedGaEddType)) {
                                addGestationalEventCount++;
                                addGestationalAgeObservationToProfileEvent(profileEvent, lmpGestationalAge);
                                Event contactVisitEvent = contactsVisitEventsMap.get(lookupKey);
                                if (contactVisitEvent != null) {
                                    JSONObject jsonObject = new JSONObject(contactVisitEvent.getDetails().get("previous_contacts"));
                                    Iterator<String> keys = jsonObject.keys();
                                    if (jsonObject.get("select_gest_age_edd").equals("lmp")) {
                                        while (keys.hasNext()) {
                                            String key = keys.next();
                                            if (!(jsonObject.get(key) instanceof JSONObject)) {
                                                if (key.equals("lmp_gest_age")) {
                                                    jsonObject.put("lmp_gest_age", lmpGaString);
                                                }
                                                if (key.equals("gest_age")) {
                                                    jsonObject.put("gest_age", lmpGaString);
                                                }
                                                if (key.equals("gest_age_openmrs")) {
                                                    String openMrsGestAge = Utils.getFirstDigit(lmpGestationalAge);
                                                    jsonObject.put("gest_age_openmrs", openMrsGestAge);
                                                }
                                            }
                                        }
                                    }
                                    contactVisitEvent.getDetails().put("previous_contacts", jsonObject.toString());
                                    addEventsToPost(eventsToPost,contactVisitEvent);
                                }
                                logger.log(Level.INFO, "added gest_age obs based on select_gest_age_edd of LMP " + ob + " to Profile Event");
                            }

                        } else {
                            logger.log(Level.SEVERE, "found null value in user " + user.getUsername() + " while calculating lmpGestationalAge for profileEvent with baseEntityId " + profileEvent.getBaseEntityId()
                                    + ", lmpString value =  " + lmpDateString[0] + " manualEncounterDate " + manualEncounterDate[0]);
                        }
                    }

                    //if Ultrasound GA
                    if (ultrasoundDone[0] != null && !ultrasoundEddDateString[0].equals("0")) {

                        String ultrasoundGestationalAge = Utils.calculateGaBasedOnUltrasoundEdd(ultrasoundEddDateString[0], manualEncounterDate[0]);

                        if (!ultrasoundGestationalAge.equals("0")) {

                            Obs ob = createOb(ultrasoundGestationalAge, "ultrasound_gest_age");
                            addEventObs(profileEvent, ob);

                            logger.log(Level.INFO, user.getUsername() + " has a added ultrasound_gest_age obs " + ob + " to Profile Event");

                            if (GuestAgeEddEnum.ULTRASOUND.equals(selectedGaEddType)) {
                                String openMrsGestAge = Utils.getFirstDigit(ultrasoundGestationalAge);
                                Event contactVisitEvent = contactsVisitEventsMap.get(lookupKey);
                                addGestationalEventCount++;
                                if (contactVisitEvent != null) {
                                    JSONObject jsonObject = new JSONObject(contactVisitEvent.getDetails().get("previous_contacts"));
                                    Iterator<String> keys = jsonObject.keys();
                                    if (jsonObject.get("select_gest_age_edd").equals("ultrasound")) {
                                        while (keys.hasNext()) {
                                            String key = keys.next();
                                            if (!(jsonObject.get(key) instanceof JSONObject)) {
                                                if (key.equals("ultrasound_gest_age")) {
                                                    jsonObject.put("ultrasound_gest_age", ultrasoundGaString);
                                                }
                                                if (key.equals("gest_age")) {
                                                    jsonObject.put("lmp_gest_age", ultrasoundGaString);
                                                }
                                                if (key.equals("gest_age_openmrs")) {
                                                    jsonObject.put("gest_age_openmrs", openMrsGestAge);
                                                }
                                            }
                                        }
                                        contactVisitEvent.getDetails().put("previous_contacts", jsonObject.toString());
                                        addEventsToPost(eventsToPost,contactVisitEvent);
                                    }

                                }
                                addGestationalAgeObservationToProfileEvent(profileEvent, ultrasoundGestationalAge);
                                logger.log(Level.INFO, "added gest_age obs based on select_gest_age_edd of Ultrasound " + ob + " to Profile Event");
                            }


                        } else {
                            logger.log(Level.SEVERE, "found null value in user " + user.getUsername() + " while calculating ultrasoundGestationalAge for profileEvent with baseEntityId " + baseEntityId + ", " +
                                    "ultrasound_edd_date value = " + ultrasoundEddDateString[0] +
                                    ", manual encounter date = " + manualEncounterDate[0]);
                        }

                    }

                    //if SFH GA
                    if (sfhGaString[0] != null && !sfhGaString[0].trim().isEmpty()) {

                        String sfhEdd = Utils.calculateSfhEdd(sfhGaString[0], manualEncounterDate[0]);
                        //update sfh fields

                        if (!sfhEdd.equals("0")) {
                            Obs ob = createOb(sfhEdd, "sfh_edd");
                            addEventObs(profileEvent, ob);

                            logger.log(Level.INFO, user.getUsername() + " has a added sfh_edd obs " + ob + " to Profile Event");


                            if (GuestAgeEddEnum.SFH.equals(selectedGaEddType)) {
                                Event contactVisitEvent = contactsVisitEventsMap.get(lookupKey);
                                if (contactVisitEvent != null) {
                                    JSONObject jsonObject = new JSONObject(contactVisitEvent.getDetails().get("previous_contacts"));
                                    Iterator<String> keys = jsonObject.keys();
                                    if (jsonObject.get("select_gest_age_edd").equals("sfh")) {
                                        while (keys.hasNext()) {
                                            String key = keys.next();
                                            if (!(jsonObject.get(key) instanceof JSONObject)) {
                                                if (key.equals("sfh_gest_age")) {
                                                    jsonObject.put("sfh_gest_age", sfhGaString);
                                                }
                                                if (key.equals("gest_age")) {
                                                    jsonObject.put("gest_age", sfhGaString);
                                                }
                                                if (key.equals("sfh_edd")) {
                                                    jsonObject.put("sfh_edd", sfhEdd);
                                                }
                                                if (key.equals("sfh_ga_hidden")) {
                                                    jsonObject.put("sfh_ga_hidden", sfhGaString);
                                                }
                                                if (key.equals("gest_age_openmrs")) {
                                                    jsonObject.put("gest_age_openmrs", Utils.getFirstDigit(sfhGaString[0]));
                                                }
                                            }
                                        }
                                        contactVisitEvent.getDetails().put("previous_contacts", jsonObject.toString());
                                        addEventsToPost(eventsToPost,contactVisitEvent);
                                    }
                                }

                                addGestationalAgeObservationToProfileEvent(profileEvent, sfhGaString[0] + " weeks 0 days");
                                logger.log(Level.INFO, "added gest_age obs based on select_gest_age_edd of SFH " + ob + " to Profile Event");
                            }

                        } else {
                            logger.log(Level.SEVERE, "found null value in user " + user.getUsername() + " while calculating sfhEdd for profileEvent with baseEntityId " + baseEntityId + ", " +
                                    "sfhEdd value = " + sfhGaString[0] +
                                    ", manual encounter date = " + manualEncounterDate[0]);
                        }

                    }

                    addEventsToPost(eventsToPost,profileEvent);
                    logger.log(Level.INFO, "added profile event with baseEntityId " + baseEntityId);
                }
            });

            filteredQuickCheckEvents.forEach(quickCheckEvent -> {
                String lookupKey = quickCheckEvent.getBaseEntityId();
                // get corresponding  counselling and treatment visit date
                Event counsellingAndTreatmentEvent = counsellingAndTreatmentWithLstVisitDateMap.get(lookupKey);
                // get visit date object
                if (!(counsellingAndTreatmentEvent == null)) {
                    List<Obs> observation = counsellingAndTreatmentEvent.getObs().stream()
                            .filter(obs -> "lst_visit_date".equals(obs.getFormSubmissionField()))
                            .collect(Collectors.toList());
                    lstVisitDateEventsFound++;
                    if (!observation.isEmpty()) {
                        Obs visitDateOb = observation.get(0).clone();
                        visitDateOb.setFormSubmissionField("visit_date");
                        visitDateOb.setFieldCode("visit_date");

                        addEventObs(quickCheckEvent, visitDateOb);

                        addEventsToPost(eventsToPost,quickCheckEvent);
                        logger.log(Level.INFO, "moved lst_visit_date from counselling and treatment event and contact "
                                + lookupKey + " to Quick Check event " + quickCheckEvent.getBaseEntityId());
                    }

                }
            });
            /**
             * Submit all modified events to the server in chunks e.g. size 50
             *
             * */
            // Define the chunk size. Too large a size, and we get an error code 413 i.e content too large as per the server
            int chunkSize = 50;
            logger.log(Level.INFO, "Processing " + eventsToPost.size() + " events for user " + user.getUsername());

            int chunkNo = 1;
            // Loop through the ArrayList in chunks
            for (int j = 0; j < eventsToPost.size(); j += chunkSize) {
                // Calculate the end index of the current chunk
                int end = Math.min(j + chunkSize, eventsToPost.size());

                // Get the sublist representing the current chunk
                List<Event> chunk = eventsToPost.subList(j, end);
                EventWrapper wrapper = new EventWrapper();
                wrapper.setEvents(chunk);

                logger.log(Level.INFO, "POSTing Chunk NO. " + chunkNo++ + " for " + user.getUsername());

                String url = HttpUrl.parse(env.getOpensrpUrl() + "/rest/event/add")
                        .newBuilder()
                        .addQueryParameter("access_token", authResponse.getAccessToken())
                        .build()
                        .toString();
                String json = null;
                try {
                    json = objectMapper.writeValueAsString(wrapper);
                } catch (JsonProcessingException e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
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
                            logger.log(Level.SEVERE, e.getMessage(), e);
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (response.isSuccessful()) {
                                logger.log(Level.INFO, "Asynchronous chunk POST complete: Response: " + response.body().string());
                            } else {
                                logger.log(Level.SEVERE, "Asynchronous chunk POST complete:Post events failed with error code " + response.code());
                            }
                        }
                    });
                }
            }
            logger.log(Level.INFO, "visitDateEventsFound " + lstVisitDateEventsFound + " for user " + user.getUsername());
            logger.log(Level.INFO, "addGestationalEventCount " + addGestationalEventCount + " for user " + user.getUsername());
            lstVisitDateEventsFound = 0;


        });

        System.out.println();
        logger.log(Level.INFO, "============================================================");
        logger.log(Level.INFO, "Script Execution Complete!!");
        logger.log(Level.INFO, "============================================================");
    }

    /**
     * @param value               The new or updated observation
     * @param formSubmissionField The Obs formSubmissionField or FieldCode
     * @return the new Obs (Observation)
     */
    public static Obs createOb(String value, String formSubmissionField) {
        Obs observation = new Obs();
        observation.setFieldCode(formSubmissionField);
        observation.setFormSubmissionField(formSubmissionField);
        observation.setFieldType("formsubmissionField");
        List<Object> values = new ArrayList<>();
        values.add(value);
        observation.setValues(values);
        return observation;
    }

    /**
     * Given an Event, and Obs, this method creates an observation and adds it to the event's list of obs.
     * If an obs with the same formSubmissionField exists it is removed and replaced with the new one
     *
     * @param event       The Event containing the observations to update
     * @param observation The new or updated observation
     */

    public static void addEventObs(Event event, Obs observation) {

        List<Obs> otherEventObs = event.getObs().stream()
                .filter(obs -> !observation.getFormSubmissionField().equals(obs.getFormSubmissionField()))
                .collect(Collectors.toList());
        otherEventObs.add(observation);
        event.setObs(otherEventObs);
    }

    interface GuestAgeEddEnum {
        String ULTRASOUND = "ultrasound";
        String SFH = "sfh";
        String LMP = "lmp";
    }

    public static void addGestationalAgeObservationToProfileEvent(Event profileEvent, String humanReadableGestationalAge) {

        Obs getAgeObservation = createOb(humanReadableGestationalAge, "gest_age");
        addEventObs(profileEvent, getAgeObservation);

        String lmpGestationalAgeWeeks = humanReadableGestationalAge.contains("weeks") ? humanReadableGestationalAge.substring(0, humanReadableGestationalAge.indexOf("weeks") - 1) : humanReadableGestationalAge;
        Obs gestAgeOpenmrsObservation = createOb(lmpGestationalAgeWeeks, "gest_age_openmrs");
        addEventObs(profileEvent, gestAgeOpenmrsObservation);

    }

    public static String getObservationValueByFormSubmissionField(Event event, String formSubmissionField) {
        List<Obs> observationList = event.getObs().stream().filter(obs -> Objects.equals(obs.getFormSubmissionField(), formSubmissionField)).collect(Collectors.toList());
        List<Object> observationValuesList = !observationList.isEmpty() ? observationList.get(0).getValues() : new ArrayList<>();
        return !observationValuesList.isEmpty() ? String.valueOf(observationValuesList.get(0)) : null;

    }
    
    public static void addEventsToPost(List<Event> eventsToPost, Event event){
         
        event.setServerVersion(null);
        eventsToPost.add(event);
    }
}
