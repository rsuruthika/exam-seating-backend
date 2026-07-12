package com.examseat.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examseat.config.JwtHelper;
import com.examseat.model.Student;
import com.examseat.model.User;
import com.examseat.repository.StudentRepository;
import com.examseat.repository.UserRepository;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {

        String username = credentials.get("username");
        String password = credentials.get("password");

        try {

            System.out.println("========== LOGIN DEBUG ==========");
            System.out.println("Username: " + username);

            User user = userRepository.findByUsername(username);

            if (user == null) {
                System.out.println("User not found!");
                return ResponseEntity.status(401).body(Map.of(
                        "error", "User not found"
                ));
            }

            System.out.println("Database User: " + user.getUsername());
            System.out.println("Database Role: " + user.getRole());
            System.out.println("Database Password: " + user.getPassword());

            boolean matches = passwordEncoder.matches(password, user.getPassword());

            System.out.println("Password Matches: " + matches);

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            String token = jwtHelper.generateToken(username, user.getRole());

            Map<String, Object> response = new HashMap<>();

            response.put("token", token);

            response.put("user", Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "name", user.getName() != null ? user.getName() : "",
                    "role", user.getRole(),
                    "dept", user.getDept() != null ? user.getDept() : "",
                    "year", user.getYear() != null ? user.getYear() : 0,
                    "regNo", user.getRegNo() != null ? user.getRegNo() : user.getUsername()
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity.status(401).body(Map.of(
                    "exception", e.getClass().getSimpleName(),
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> userData) {
        try {
            String username = userData.get("username");
            String password = userData.get("password");
            String role = userData.get("role");
            String name = userData.get("name");
            String dept = userData.get("dept");
            Integer year = userData.get("year") != null ? Integer.parseInt(userData.get("year")) : null;

            if (userRepository.findByUsername(username) != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
            }

            String actualRole = role != null ? role.toUpperCase() : "STUDENT";
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(actualRole);

            if ("STUDENT".equals(actualRole)) {
                // Auto-detect details from student table if available
                Optional<Student> studentOpt = studentRepository.findByRegNo(username);
                if (studentOpt.isPresent()) {
                    Student student = studentOpt.get();
                    user.setName(student.getName());
                    user.setDept(student.getDept());
                    user.setYear(student.getExamYear());
                    user.setRegNo(student.getRegNo());
                } else {
                    user.setName(name);
                    user.setDept(dept);
                    user.setYear(year);
                    user.setRegNo(username);
                }
            } else {
                user.setName(name);
                user.setDept(dept);
                user.setYear(year);
                user.setRegNo(username);
            }

            userRepository.save(user);

            String token = jwtHelper.generateToken(username, user.getRole());

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "name", user.getName() != null ? user.getName() : "",
                "role", user.getRole(),
                "dept", user.getDept() != null ? user.getDept() : "",
                "year", user.getYear() != null ? user.getYear() : 0,
                "regNo", user.getRegNo() != null ? user.getRegNo() : user.getUsername()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtHelper.validateToken(token)) {
                    String username = jwtHelper.extractUsername(token);
                    String role = jwtHelper.extractRole(token);
                    return ResponseEntity.ok(Map.of("valid", true, "username", username, "role", role));
                }
            }
            return ResponseEntity.ok(Map.of("valid", false));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("valid", false));
        }
    }
}