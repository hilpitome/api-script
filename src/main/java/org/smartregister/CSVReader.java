package org.smartregister;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CSVReader {
    public List<User> readUsersFromCSV(String fileName) {
        List<User> users = new ArrayList<>();
        InputStream is = getClass().getClassLoader().getResourceAsStream(fileName);

        if (is == null) {
            throw new IllegalArgumentException("file not found! " + fileName);
        } else {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
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
                e.printStackTrace();
            }
        }
        return users;
    }

}
