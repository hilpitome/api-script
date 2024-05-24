package org.smartregister;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class Utils {
    protected static final Logger logger = LogManager.getLogger();

    //Gestational Age based on the Ultrasound GA: 280 - ({ultrasound_edd} - {today/manual encounter date})
    public static String calculateGaBasedOnUltrasoundEdd(String ultrasoundDateEddDateString, String manualEncounterDateString) {
        logger.warn("Dates U/EDD " + ultrasoundDateEddDateString + " Manual Enc: " + manualEncounterDateString);
        if (ultrasoundDateEddDateString != null && manualEncounterDateString != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            LocalDate ultrasoundDateEddDate = LocalDate.parse(ultrasoundDateEddDateString, formatter);
            LocalDate manualEncounterDate = LocalDate.parse(manualEncounterDateString, formatter);

            long daysBetween = 280 - Math.abs(ChronoUnit.DAYS.between(ultrasoundDateEddDate, manualEncounterDate));
            long weeks = daysBetween / 7;
            long days = daysBetween % 7;
            return weeks + " weeks " + days + " days";
        }
        return "0";
    }

    // sfh_edd {today/manual encounter date} + (280 - {sfh_gest_age} * 7)
    public static String calculateSfhEdd(String sfhGestationalAgeInWeeks, String manualEncounterDateString) {
        if (sfhGestationalAgeInWeeks != null && manualEncounterDateString != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            LocalDate manualEncounterDate = LocalDate.parse(manualEncounterDateString, formatter);
            int days = Integer.parseInt(sfhGestationalAgeInWeeks) * 7;
            return manualEncounterDate.plusDays(280 - days).format(formatter);
        }
        return "0";
    }

    public static String calculateEddUltrasound(String ultrasoundDateString, String weeks, String days) {
        if (ultrasoundDateString == null || weeks == null || days == null) {
            return "0";
        }
        int totalDays = Integer.parseInt(weeks) * 7 + Integer.parseInt(days);
        int diffFrom280days = 280 - totalDays;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        try {
            LocalDate date = LocalDate.parse(ultrasoundDateString, formatter);
            return date.plusDays(diffFrom280days).format(formatter);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format: " + e.getMessage());
            return "0";
        }
    }

    public static String calculateEddLmp(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        return LocalDate.parse(dateString, formatter).plusDays(280).format(formatter);
    }

    public static String lmpGestationalAge(String lmpDateString, String manualEncounterDateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        LocalDate lmpDate = null;
        LocalDate manualEncounterDate = null;
        try {
            if (lmpDateString == null || manualEncounterDateString == null) {
                return "0";
            }
            lmpDate = LocalDate.parse(lmpDateString, formatter);
            manualEncounterDate = LocalDate.parse(manualEncounterDateString, formatter);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format: " + e.getMessage());
            return "0";
        }
        long daysBetween = Math.abs(ChronoUnit.DAYS.between(lmpDate, manualEncounterDate));
        long weeks = daysBetween / 7;
        long days = daysBetween % 7;
        return weeks + " weeks " + days + " days";
    }


}
