package com.examseat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ExamSeatProApplication {

    public static void main(String[] args) {
        // Load .env file before Spring context starts so ${ENV_VAR} placeholders resolve
        loadEnv();
        SpringApplication.run(ExamSeatProApplication.class, args);
    }

    /**
     * Reads a .env file and registers each key=value pair as a System property,
     * but only if that key is not already set (allows true env vars to override .env).
     * Looks in: ./  then ../  then backend/
     */
    private static void loadEnv() {
        String[] candidates = {".env", "../.env", "backend/.env"};
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                System.out.println("[ENV] Loading environment from: " + f.getAbsolutePath());
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        // Skip blank lines and comments
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int idx = line.indexOf('=');
                        if (idx < 1) continue;
                        String key   = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        // Never override a real environment variable already present
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("[ENV] Warning: Could not read .env file: " + e.getMessage());
                }
                return; // Stop after the first .env found
            }
        }
    }
}