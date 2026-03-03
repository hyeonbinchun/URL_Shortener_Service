package com.hyeonbin.URLShortener.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;

@Service
public class UrlShortenerService {
    private final File database;

    public UrlShortenerService(@Value("${app.database-path}") String databasePath) throws IOException {
        this.database = new File(databasePath);
        if (!database.exists()) {
            database.getParentFile().mkdirs(); // creates parent dirs if needed
            database.createNewFile();          // creates database.txt if missing
        }
    }

    // find method to retrieve the long URL based on the short URL
    public String find(String shortURL) throws IOException {
         try (BufferedReader br = new BufferedReader(new FileReader(database))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts[0].equals(shortURL)) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    // save method to store the short URL and long URL pair in the database
    public void save(String shortURL, String longURL) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(database, true))) {
            pw.println(shortURL + "\t" + longURL);
        }
    }
}
