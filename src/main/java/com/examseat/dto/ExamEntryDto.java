package com.examseat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class ExamEntryDto {

    private Long id;
    private String department;
    private Integer year;
    private String courseCode;
    private String subject;
    private LocalDate examDate;
    private String session;
    private Double confidenceScore = 1.0;
    private Map<String, Double> fieldConfidences = new HashMap<>();
    @JsonProperty("requiresReview")
    private boolean requiresReview = false;

    public ExamEntryDto() {}

    // Builder pattern
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ExamEntryDto dto = new ExamEntryDto();
        public Builder id(Long id) { dto.id = id; return this; }
        public Builder department(String v) { dto.department = v; return this; }
        public Builder year(Integer v) { dto.year = v; return this; }
        public Builder courseCode(String v) { dto.courseCode = v; return this; }
        public Builder subject(String v) { dto.subject = v; return this; }
        public Builder examDate(LocalDate v) { dto.examDate = v; return this; }
        public Builder session(String v) { dto.session = v; return this; }
        public Builder confidenceScore(Double v) { dto.confidenceScore = v; return this; }
        public Builder fieldConfidences(Map<String, Double> v) { dto.fieldConfidences = v; return this; }
        public Builder requiresReview(boolean v) { dto.requiresReview = v; return this; }
        public ExamEntryDto build() { return dto; }
    }

    // Getters
    public Long getId() { return id; }
    public String getDepartment() { return department; }
    public Integer getYear() { return year; }
    public String getCourseCode() { return courseCode; }
    public String getSubject() { return subject; }
    public LocalDate getExamDate() { return examDate; }
    public String getSession() { return session; }
    public Double getConfidenceScore() { return confidenceScore; }
    public Map<String, Double> getFieldConfidences() { return fieldConfidences; }
    public boolean isRequiresReview() { return requiresReview; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setDepartment(String department) { this.department = department; }
    public void setYear(Integer year) { this.year = year; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setExamDate(LocalDate examDate) { this.examDate = examDate; }
    public void setSession(String session) { this.session = session; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }
    public void setFieldConfidences(Map<String, Double> fieldConfidences) { this.fieldConfidences = fieldConfidences; }
    public void setRequiresReview(boolean requiresReview) { this.requiresReview = requiresReview; }

    public boolean hasLowConfidence() {
        return confidenceScore < 0.8 || (fieldConfidences != null &&
            fieldConfidences.values().stream().anyMatch(c -> c != null && c < 0.8));
    }
}
