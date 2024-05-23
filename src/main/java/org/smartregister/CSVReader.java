package org.smartregister;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CSVReader {
    protected static final Logger logger = LogManager.getLogger();

    public List<User> readUsersFromCSV(String fileName) {
        List<User> users = new ArrayList<>();

        Path path = Paths.get(fileName);

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                // Skip the header line
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                String[] fields = line.split(",");
                if (fields.length >= 2) {
                    String username = fields[0];
                    String password = fields[1];
                    User user = new User(username, password);
                    users.add(user);
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return users;

    }

}
