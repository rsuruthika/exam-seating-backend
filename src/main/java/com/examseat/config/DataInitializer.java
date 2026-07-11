package com.examseat.config;

import com.examseat.model.User;
import com.examseat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Values come from application.properties → environment variables
    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        User admin = userRepository.findByUsername(adminUsername);
        if (admin == null) {
            admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole("ADMIN");
            admin.setName("Administrator");
            userRepository.save(admin);
            System.out.println("Default admin user created: " + adminUsername);
        } else {
            // Ensure password is a valid BCrypt hash (starts with $2a$/$2b$/$2y$ and is 60 chars)
            String pwd = admin.getPassword();
            boolean isBcrypt = pwd != null
                    && (pwd.startsWith("$2a$") || pwd.startsWith("$2b$") || pwd.startsWith("$2y$"))
                    && pwd.length() == 60;
            if (!isBcrypt) {
                admin.setPassword(passwordEncoder.encode(adminPassword));
                userRepository.save(admin);
                System.out.println("Admin password updated to BCrypt encoding.");
            }
        }
    }
}