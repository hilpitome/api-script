package org.smartregister;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Hello world!
 */
public class App {
    protected static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) {
        Environment env = new Environment();
        CSVReader csvReader = new CSVReader();
        logger.info("reading user credentials csv files");
        List<User> users = csvReader.readUsersFromCSV(env.getCsvFilePath());

        users.forEach(user -> {

            System.out.println();
            logger.info("============================================================");
            logger.info("Processing data for user with username " + user.getUsername());
            logger.info("============================================================");
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
            int count = 250;
            long lastServerVersion = 0;
            List<Event> eventsToPost = new ArrayList<>();
            AuthResponse authResponse = null;
            logger.info("authenticating user with username " + user.getUsername());
            try (Response response = client.newCall(request).execute()) {

                // response from keycloak
                // Deserialize the response body to AuthResponse
                objectMapper = new ObjectMapper();
                authResponse = objectMapper.readValue(response.body().string(), AuthResponse.class);
                logger.info("succesfully authenticated to keycloack and obtained access_token ");

                String url = HttpUrl.parse(env.getOpensrpUrl() + "/security/authenticate")
                        .newBuilder()
                        .addQueryParameter("access_token", authResponse.getAccessToken())
                        .build()
                        .toString();
                Request opensrpAuthRequest = new Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .build();
                try (Response openSRPAuthResponse = client.newCall(opensrpAuthRequest).execute()) {
                    OpenSRPAuthResponse opensrpAuth = objectMapper.readValue(openSRPAuthResponse.body().string(), OpenSRPAuthResponse.class);
                    user.setTeamId(opensrpAuth.getTeam().getInnerTeam().getUuid());
                    logger.info("succesfully retrieved team id as " + user.getTeamId() + " for user " + user.getUsername());

                } catch (IOException e) {

                    logger.error("failed to get team id for user " + user.getUsername());
                    logger.error(e.getMessage(), e);
                }

            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
            int i = 0;
            do {

                String url = HttpUrl.parse(env.getOpensrpUrl() + "/rest/event/sync")
                        .newBuilder()
                        .addQueryParameter("limit", "250")
                        .addQueryParameter("teamId", user.getTeamId())
                        .addQueryParameter("serverVersion", String.valueOf(lastServerVersion))
                        .addQueryParameter("access_token", authResponse.getAccessToken())
                        .build()
                        .toString();
                Request request1 = new Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .build();

                logger.info("making event request number " + (i + 1));
                try (Response response1 = client.newCall(request1).execute()) {
                    logger.info("getting response for event batch " + (i + 1));
                    i++;
                    EventWrapper eventWrapper = objectMapper.readValue(response1.body().bytes(), EventWrapper.class);
                    List<Event> events = eventWrapper.getEvents();
                    count = eventWrapper.getNoOfEvents();
                    if (count > 0) {
                        logger.debug("no of events received " + count);
                        lastServerVersion = events.get(events.size() - 1).getServerVersion();


                        // Filter and collect the results
                        List<Event> counsellingAndTreatmentWithLstVisitDate = events.stream()
                                .filter(event -> "Counselling and Treatment".equals(event.getEventType()))
                                .filter(event -> event.getObs().stream()
                                        .anyMatch(obs -> "lst_visit_date".equals(obs.getFormSubmissionField())))
                                .collect(Collectors.toList());

                        System.out.println();
                        logger.debug("filtered " + counsellingAndTreatmentWithLstVisitDate.size() + " counselling and treatment events" +
                                " with lst_visit_date");

                        HashMap<String, Event> counsellingAndTreatmentWithLstVisitDateMap = new HashMap<>();
                        counsellingAndTreatmentWithLstVisitDate
                                .forEach(event -> counsellingAndTreatmentWithLstVisitDateMap
                                        .put(event.getBaseEntityId() + "-" + event.getDetails().get("contact_no"), event)
                                );
                        logger.info("created hashmap of baseentityids-concactno for counselling and treatment events");
                        List<Event> filteredQuickCheckEvents = events.stream().filter(event -> "Quick Check".equals(event.getEventType()))
                                .collect(Collectors.toList());
                        logger.debug("filtered " + filteredQuickCheckEvents.size() + " Quick Check events");
                        filteredQuickCheckEvents.forEach(quickCheckEvent -> {
                            String lookupKey = quickCheckEvent.getBaseEntityId() + "-" + quickCheckEvent.getDetails().get("contact_no");
                            // get corresponding  counselling and treatment visit date
                            Event counsellingAndTreatmentEvent = counsellingAndTreatmentWithLstVisitDateMap.get(lookupKey);
                            // get visit date object
                            if (!(counsellingAndTreatmentEvent == null)) {
                                List<Obs> observation = counsellingAndTreatmentEvent.getObs().stream()
                                        .filter(obs -> "lst_visit_date".equals(obs.getFormSubmissionField()))
                                        .collect(Collectors.toList());
                                if (!observation.isEmpty()) {
                                    Obs visitDateOb = observation.get(0).clone();
                                    visitDateOb.setFormSubmissionField("visit_date");
                                    visitDateOb.setFieldCode("visit_date");

                                    addEventObs(quickCheckEvent, visitDateOb);

                                    eventsToPost.add(quickCheckEvent);
                                    logger.debug("moved lst_visit_date from counselling and treatment event and contact "
                                            + lookupKey + " to Quick Check event " + quickCheckEvent.getBaseEntityId());
                                }

                            }
                        });

                        List<Event> profileEvents = events.stream().filter(event -> "Profile".equals(event.getEventType()))
                                .collect(Collectors.toList());
                        logger.debug("filtered " + profileEvents.size() + " Profile events");
                        profileEvents.forEach(profileEvent -> {
                            String lookupKey = profileEvent.getBaseEntityId() + "-" + profileEvent.getDetails().get("contact_no");
                            // get corresponding  counselling and treatment visit date
                            Event counsellingAndTreatmentEvent = counsellingAndTreatmentWithLstVisitDateMap.get(lookupKey);
                            // get visit date object
                            if (!(counsellingAndTreatmentEvent == null)) {
                                final String[] manualEncounterDate = new String[1];
                                final String[] lmpDone = new String[1];
                                final String[] ultrasoundDone = new String[1];
                                final String[] lmpDateString = new String[1];
                                final String[] ultrasoundDateString = new String[1];
                                final String[] sfhGaString = new String[1];
                                String baseEntityId = profileEvent.getBaseEntityId();
                                System.out.println();
                                logger.debug("mutating Profile event for baseEntityId " + baseEntityId +
                                        " with data from counselling and treatment event "
                                        + lookupKey);

                                manualEncounterDate[0] = getObservationValueByFormSubmissionField(counsellingAndTreatmentEvent, "lst_visit_date");

                                logger.debug(manualEncounterDate[0] == null ? "Not Found manual encounter date from counsellingAndTreatmentEvent lst_visit_date" : "Found manual encounter date " + manualEncounterDate[0] + " from counsellingAndTreatmentEvent lst_visit_date");

                                profileEvent.getObs().forEach(obs -> {
                                    if (Objects.equals(obs.getFormSubmissionField(), "lmp_known_date")) {
                                        lmpDateString[0] = (String) obs.getValues().get(0);
                                        logger.debug("found lmp_known_date " + lmpDateString[0] + " for event with baseEntityId " + baseEntityId);
                                    }
                                    if (Objects.equals(obs.getFormSubmissionField(), "lmp_known")) {
                                        lmpDone[0] = (String) obs.getHumanReadableValues().get(0);
                                        logger.debug("found lmp_known " + lmpDone[0] + " for event with baseEntityId " + baseEntityId);
                                    }
                                    if (Objects.equals(obs.getFormSubmissionField(), "ultrasound_done")) {
                                        ultrasoundDone[0] = (String) obs.getHumanReadableValues().get(0);
                                        logger.debug("found ultrasound_done " + ultrasoundDone[0] + " for event with baseEntityId " + baseEntityId);
                                    }
                                    if (Objects.equals(obs.getFormSubmissionField(), "ultrasound_done_date")) {
                                        ultrasoundDateString[0] = (String) obs.getValues().get(0);
                                        logger.debug("found ultrasound_done_date " + ultrasoundDateString[0] + " for event with baseEntityId " + baseEntityId);
                                    }
                                    if (Objects.equals(obs.getFormSubmissionField(), "sfh_gest_age")) {
                                        sfhGaString[0] = (String) obs.getValues().get(0);
                                        logger.debug("found sfh_gest_age " + sfhGaString[0] + " for event with baseEntityId " + baseEntityId);
                                    }
                                });

                                String selectedGaEddType = getObservationValueByFormSubmissionField(profileEvent, "select_gest_age_edd");

                                // if lmpDone gestationalAge
                                if (lmpDone[0] != null && lmpDone[0].equals("yes") && !lmpDateString[0].equals("0")) {


                                    String lmpGestationalAge = Utils.lmpGestationalAge(lmpDateString[0], manualEncounterDate[0]);

                                    if (!lmpGestationalAge.equals("0")) {
                                        Obs ob = createOb(lmpGestationalAge, "lmp_gest_age");
                                        addEventObs(profileEvent, ob);

                                        logger.debug("added lmp_gest_age obs " + ob + " to Profile Event");

                                        if (GuestAgeEddEnum.LMP.equals(selectedGaEddType)) {
                                            addGestationalAgeObservationToProfileEvent(profileEvent, lmpGestationalAge);
                                            logger.debug("added gest_age obs based on select_gest_age_edd of LMP " + ob + " to Profile Event");
                                        }

                                    } else
                                        logger.error("found null value in user " + user.getUsername() + " while calculating lmpGestationalAge for profileEvent with baseEntityId " + profileEvent.getBaseEntityId()
                                                + ", lmpString value =  " + lmpDateString[0] + " manualEncounterDate " + manualEncounterDate[0]);
                                }

                                //if Ultrasound GA
                                if (ultrasoundDone[0] != null && !ultrasoundDateString[0].equals("0")) {

                                    String ultrasoundGestationalAge = Utils.calculateGaBasedOnUltrasoundEdd(ultrasoundDateString[0], manualEncounterDate[0]);

                                    if (!ultrasoundGestationalAge.equals("0")) {

                                        Obs ob = createOb(ultrasoundGestationalAge, "ultrasound_gest_age");
                                        addEventObs(profileEvent, ob);

                                        logger.debug(user.getUsername() + " has a added ultrasound_gest_age obs " + ob + " to Profile Event");

                                        if (GuestAgeEddEnum.ULTRASOUND.equals(selectedGaEddType)) {
                                            addGestationalAgeObservationToProfileEvent(profileEvent, ultrasoundGestationalAge);
                                            logger.debug("added gest_age obs based on select_gest_age_edd of Ultrasound " + ob + " to Profile Event");
                                        }


                                    } else
                                        logger.error("found null value in user " + user.getUsername() + " while calculating ultrasoundGestationalAge for profileEvent with baseEntityId " + baseEntityId + ", " +
                                                "ultrasound_done_date value = " + ultrasoundDateString[0] +
                                                ", manual encounter date = " + manualEncounterDate[0]);

                                }

                                //if SFH GA
                                if (sfhGaString[0] != null && !sfhGaString[0].trim().isEmpty()) {

                                    String sfhEdd = Utils.calculateSfhEdd(sfhGaString[0], manualEncounterDate[0]);

                                    if (!sfhEdd.equals("0")) {
                                        Obs ob = createOb(sfhEdd, "sfh_edd");
                                        addEventObs(profileEvent, ob);

                                        logger.debug(user.getUsername() + " has a added sfh_edd obs " + ob + " to Profile Event");


                                        if (GuestAgeEddEnum.SFH.equals(selectedGaEddType)) {
                                            addGestationalAgeObservationToProfileEvent(profileEvent, sfhGaString[0] + " weeks 0 days");
                                            logger.debug("added gest_age obs based on select_gest_age_edd of SFH " + ob + " to Profile Event");
                                        }

                                    } else
                                        logger.error("found null value in user " + user.getUsername() + " while calculating sfhEdd for profileEvent with baseEntityId " + baseEntityId + ", " +
                                                "sfhEdd value = " + sfhGaString[0] +
                                                ", manual encounter date = " + manualEncounterDate[0]);


                                }

                                eventsToPost.add(profileEvent);
                                logger.debug("added profile event with baseEntityId " + baseEntityId);

                            }
                        });
                    }


                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }


            } while (count > 0);


            // Define the chunk size. Too large a size, and we get an error code 413 i.e content too large as per the server
            int chunkSize = 50;
            logger.debug("Processing " + eventsToPost.size() + " events for user " + user.getUsername());

            int chunkNo = 1;
            // Loop through the ArrayList in chunks
            for (int j = 0; j < eventsToPost.size(); j += chunkSize) {
                // Calculate the end index of the current chunk
                int end = Math.min(j + chunkSize, eventsToPost.size());

                // Get the sublist representing the current chunk
                List<Event> chunk = eventsToPost.subList(j, end);
                EventWrapper wrapper = new EventWrapper();
                wrapper.setEvents(chunk);

                logger.debug("POSTing Chunk NO. " + chunkNo++ + " for " + user.getUsername());

                String url = HttpUrl.parse(env.getOpensrpUrl() + "/rest/event/add")
                        .newBuilder()
                        .addQueryParameter("access_token", authResponse.getAccessToken())
                        .build()
                        .toString();
                String json = null;
                try {
                    json = objectMapper.writeValueAsString(wrapper);
                } catch (JsonProcessingException e) {
                    logger.error(e.getMessage(), e);
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
                            logger.error(e.getMessage(), e);
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (response.isSuccessful()) {
                                logger.debug("Asynchronous chunk POST complete: Response: " + response.body().string());
                            } else {
                                logger.error("Asynchronous chunk POST complete:Post events failed with error code " + response.code());
                            }
                        }
                    });
                }
            }

        });

        System.out.println();
        logger.info("============================================================");
        logger.debug("Script Execution Complete!! :D");
        logger.info("============================================================");
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
}
