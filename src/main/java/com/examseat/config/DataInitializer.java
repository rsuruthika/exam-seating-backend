package com.examseat.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.examseat.model.User;
import com.examseat.repository.UserRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Use defaults if the properties are missing
    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:admin123}")
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

            String pwd = admin.getPassword();

            boolean isBcrypt = pwd != null
                    && (pwd.startsWith("$2a$")
                    || pwd.startsWith("$2b$")
                    || pwd.startsWith("$2y$"))
                    && pwd.length() == 60;

            if (!isBcrypt) {

                admin.setPassword(passwordEncoder.encode(adminPassword));
                userRepository.save(admin);

                System.out.println("Admin password updated to BCrypt encoding.");
            }
        }
    }
}