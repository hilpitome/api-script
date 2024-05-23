package org.smartregister;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class Utils {
    protected static final Logger logger = LogManager.getLogger();
    public static String calculateEddUltrasound(String dateString, String weeks, String days){
        if(dateString == null || weeks == null || days == null){
            return "0";
        }
       int totalDays = Integer.parseInt(weeks) * 7 + Integer.parseInt(days);
       int diffFrom280days = 280 - totalDays;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        try {
            LocalDate date = LocalDate.parse(dateString, formatter);
            return date.plusDays(diffFrom280days).format(formatter);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format: " + e.getMessage());
            return "0";
        }
    }
    public static String calculateEddLmp(String dateString){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        return LocalDate.parse(dateString, formatter).plusDays(280).format(formatter);
    }

    public static String lmpGestationalAge(String lmpDateString, String manualEncounterDateString){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        LocalDate lmpDate = null;
        LocalDate manualEncounterDate = null;
        try {
            if(lmpDateString == null || manualEncounterDateString == null){
                return "0";
            }
            lmpDate = LocalDate.parse(lmpDateString, formatter);
            manualEncounterDate = LocalDate.parse(manualEncounterDateString, formatter);
        } catch (DateTimeParseException e){
            logger.error("Invalid date format: " + e.getMessage());
            return "0";
        }
        long daysBetween = ChronoUnit.DAYS.between(lmpDate, manualEncounterDate);
        long weeks = daysBetween/7;
        long days = daysBetween % 7;
        return weeks+ " weeks "+days+" days";
    }

}
