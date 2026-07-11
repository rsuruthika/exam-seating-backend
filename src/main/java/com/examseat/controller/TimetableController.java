package com.examseat.controller;

import com.examseat.dto.ExamEntryDto;
import com.examseat.model.ExamTimetable;
import com.examseat.repository.ExamTimetableRepository;
import com.examseat.service.TimetableParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/timetable")
@CrossOrigin(origins = "*")
public class TimetableController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @Autowired
    private TimetableParserService parserService;

    @Autowired
    private ExamTimetableRepository repository;

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> extractTimetable(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("No file provided"));
        }

        if (!file.getContentType().equals("application/pdf")) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Only PDF files are allowed"));
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("File size exceeds 10MB limit"));
        }

        try {
            List<ExamEntryDto> entries = parserService.parseTimetableSync(file);
            
            boolean requiresReview = entries.stream().anyMatch(ExamEntryDto::isRequiresReview);
            
            ExtractionResponse response = new ExtractionResponse();
            response.setEntries(entries);
            response.setTotalEntries(entries.size());
            response.setRequiresReview(requiresReview);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to parse PDF: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/extract-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> extractTimetableText(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("No file provided"));
        }

        if (!file.getContentType().equals("application/pdf")) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Only PDF files are allowed"));
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("File size exceeds 10MB limit"));
        }

        try {
            String text = parserService.extractRawText(file);
            return ResponseEntity.ok(Map.of("text", text));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to extract text from PDF: " + e.getMessage()));
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmTimetable(@RequestBody List<ExamEntryDto> entries) {
        if (entries == null || entries.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("No entries provided"));
        }

        List<String> errors = validateEntries(entries);
        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ValidationErrorResponse(errors));
        }

        List<ExamTimetable> savedEntries = new ArrayList<>();
        
        for (ExamEntryDto dto : entries) {
            String department = parserService.stripSectionSuffix(dto.getDepartment());
            
            ExamTimetable existing = null;
            List<ExamTimetable> byDeptYear = repository.findByDepartmentAndYear(department, dto.getYear());
            for (ExamTimetable t : byDeptYear) {
                if (t.getCourseCode().equals(dto.getCourseCode()) && 
                    t.getExamDate().equals(dto.getExamDate())) {
                    existing = t;
                    break;
                }
            }

            if (existing != null) {
                existing.setSubject(dto.getSubject());
                existing.setSession(dto.getSession());
                savedEntries.add(repository.save(existing));
            } else {
                ExamTimetable entry = new ExamTimetable();
                entry.setDepartment(department);
                entry.setYear(dto.getYear());
                entry.setCourseCode(dto.getCourseCode());
                entry.setSubject(dto.getSubject());
                entry.setExamDate(dto.getExamDate());
                entry.setSession(dto.getSession());
                savedEntries.add(repository.save(entry));
            }
        }

        return ResponseEntity.ok(savedEntries);
    }

    @PostMapping("/upload")
    public ResponseEntity<List<ExamTimetable>> uploadPdf(@RequestParam("file") MultipartFile file) {
        try {
            List<ExamEntryDto> parsed = parserService.parseTimetableSync(file);
            
            List<ExamTimetable> entries = new ArrayList<>();
            for (ExamEntryDto dto : parsed) {
                String department = parserService.stripSectionSuffix(dto.getDepartment());
                ExamTimetable entry = new ExamTimetable();
                entry.setDepartment(department);
                entry.setYear(dto.getYear());
                entry.setCourseCode(dto.getCourseCode());
                entry.setSubject(dto.getSubject());
                entry.setExamDate(dto.getExamDate());
                entry.setSession(dto.getSession());
                entries.add(entry);
            }
            
            return ResponseEntity.ok(entries);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/save-all")
    public ResponseEntity<List<ExamTimetable>> saveAll(@RequestBody List<ExamTimetable> entries) {
        List<ExamTimetable> saved = repository.saveAll(entries);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<ExamTimetable>> getAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    public ResponseEntity<ExamTimetable> save(@RequestBody ExamTimetable entry) {
        String department = parserService.stripSectionSuffix(entry.getDepartment());
        entry.setDepartment(department);
        return ResponseEntity.ok(repository.save(entry));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/departments")
    public ResponseEntity<List<String>> getDistinctDepartments() {
        List<ExamTimetable> all = repository.findAll();
        List<String> departments = all.stream()
                .map(t -> parserService.stripSectionSuffix(t.getDepartment()))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return ResponseEntity.ok(departments);
    }

    private List<String> validateEntries(List<ExamEntryDto> entries) {
        List<String> errors = new ArrayList<>();
        
        for (int i = 0; i < entries.size(); i++) {
            ExamEntryDto entry = entries.get(i);
            
            if (entry.getDepartment() == null || entry.getDepartment().trim().isEmpty()) {
                errors.add("Entry " + (i + 1) + ": Department is required");
            }
            
            if (entry.getYear() == null || entry.getYear() < 1 || entry.getYear() > 4) {
                errors.add("Entry " + (i + 1) + ": Year must be between 1 and 4");
            }
            
            if (entry.getCourseCode() == null || entry.getCourseCode().trim().isEmpty()) {
                errors.add("Entry " + (i + 1) + ": Course code is required");
            }
            
            if (entry.getExamDate() == null) {
                errors.add("Entry " + (i + 1) + ": Exam date is required");
            }
            
            if (entry.getSession() == null || (!entry.getSession().equals("FN") && !entry.getSession().equals("AN"))) {
                errors.add("Entry " + (i + 1) + ": Session must be FN or AN");
            }
        }
        
        return errors;
    }

    public static class ErrorResponse {
        private String error;
        
        public ErrorResponse() {}
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    public static class ValidationErrorResponse {
        private List<String> errors;
        
        public ValidationErrorResponse() {}
        
        public ValidationErrorResponse(List<String> errors) {
            this.errors = errors;
        }
        
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }

    public static class ExtractionResponse {
        private List<ExamEntryDto> entries;
        private int totalEntries;
        private boolean requiresReview;
        
        public List<ExamEntryDto> getEntries() { return entries; }
        public void setEntries(List<ExamEntryDto> entries) { this.entries = entries; }
        public int getTotalEntries() { return totalEntries; }
        public void setTotalEntries(int totalEntries) { this.totalEntries = totalEntries; }
        public boolean isRequiresReview() { return requiresReview; }
        public void setRequiresReview(boolean requiresReview) { this.requiresReview = requiresReview; }
    }
}
