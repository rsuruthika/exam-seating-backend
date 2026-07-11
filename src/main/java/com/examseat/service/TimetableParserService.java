package com.examseat.service;

import com.examseat.dto.ExamEntryDto;
import com.examseat.model.ExamTimetable;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TimetableParserService {

    private final FuzzyNormalizationService fuzzyService;
    
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{1,2}[\\s/-]*\\d{1,2}[\\s/-]*\\d{2,4})|" +
            "(\\d{4}[\\s/-]*\\d{1,2}[\\s/-]*\\d{1,2})|" +
            "((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\\s,]*\\d{1,2}[\\s,]*\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SESSION_PATTERN = Pattern.compile(
            "\\b(FN|AN|Forenoon|Afternoon|Morning|Afternon)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern YEAR_PATTERN = Pattern.compile(
            "\\b(Year|Yr|Semester|Sem)[\\s]*(\\d)\\b|" +
            "\\b(I{1,3}V?|V?I{0,3})\\b(?=\\s*(Year|yr|Sem|sem))|" +
            "(?<=Year[\\s]*)([1-4])|" +
            "(?<=Sem[\\s]*)([1-8])",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern COURSE_CODE_PATTERN = Pattern.compile(
            "\\b([A-Z]{2,4}\\d{2,5}[A-Z]?)\\b|" +
            "\\b(\\d{2,5}[A-Z]?)\\b(?=\\s+[A-Z])"
    );

    private static final List<String> DEPARTMENTS = Arrays.asList(
            "CSE", "ECE", "EEE", "MECH", "CIVIL", "IT", "AI", "DS", "AIDS", "AIML", "CS", "CCE", "VLSI", "CSBS", "BT"
    );

    private static final String[] MONTH_NAMES = {
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec"
    };

    public TimetableParserService(FuzzyNormalizationService fuzzyService) {
        this.fuzzyService = fuzzyService;
    }

    @Async
    public CompletableFuture<List<ExamEntryDto>> parseTimetableAsync(MultipartFile file) {
        try {
            List<ExamEntryDto> entries = parseTimetableInternal(file);
            return CompletableFuture.completedFuture(entries);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public List<ExamEntryDto> parseTimetableSync(MultipartFile file) throws IOException {
        return parseTimetableInternal(file);
    }

    private List<ExamEntryDto> parseTimetableInternal(MultipartFile file) throws IOException {
        List<ExamEntryDto> entries = new ArrayList<>();

        String text;
        
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            int pageCount = document.getNumberOfPages();

            for (int page = 1; page <= pageCount; page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                text = stripper.getText(document);

                entries.addAll(parsePageText(text, page));
            }
        }

        Map<String, ExamEntryDto> uniqueEntries = new LinkedHashMap<>();
        for (ExamEntryDto entry : entries) {
            String key = entry.getDepartment().toLowerCase() + "_" + entry.getYear() + "_" + 
                        entry.getCourseCode().toLowerCase() + "_" + entry.getExamDate();
            if (!uniqueEntries.containsKey(key)) {
                uniqueEntries.put(key, entry);
            } else {
                ExamEntryDto existing = uniqueEntries.get(key);
                if (entry.getConfidenceScore() > existing.getConfidenceScore()) {
                    uniqueEntries.put(key, entry);
                }
            }
        }

        return new ArrayList<>(uniqueEntries.values());
    }

    private List<ExamEntryDto> parsePageText(String text, int pageNum) {
        List<ExamEntryDto> entries = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        String currentContext = "";
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (looksLikeHeader(trimmed)) {
                currentContext = trimmed;
                continue;
            }

            Matcher dateMatcher = DATE_PATTERN.matcher(trimmed);
            if (dateMatcher.find()) {
                String dateStr = dateMatcher.group();
                String cleanLine = trimmed.replace(dateStr, "").trim();
                
                ExamEntryDto entry = parseLineToEntry(cleanLine, dateStr, currentContext);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        return entries;
    }

    private boolean looksLikeHeader(String line) {
        String lower = line.toLowerCase();
        return (lower.contains("date") || lower.contains("subject") || 
                lower.contains("department") || lower.contains("session") ||
                lower.contains("time") || lower.contains("course code")) 
                && (line.length() < 150);
    }

    private ExamEntryDto parseLineToEntry(String line, String dateStr, String context) {
        String department = "";
        String year = "";
        String courseCode = "";
        String subject = "";
        String session = "";
        Map<String, Double> fieldConfidences = new HashMap<>();

        Matcher codeMatcher = COURSE_CODE_PATTERN.matcher(line);
        if (codeMatcher.find()) {
            courseCode = codeMatcher.group();
            fieldConfidences.put("courseCode", 0.9);
        }

        Matcher yearMatcher = YEAR_PATTERN.matcher(line);
        if (yearMatcher.find()) {
            year = extractYear(yearMatcher.group());
            fieldConfidences.put("year", 0.85);
        }

        Matcher sessionMatcher = SESSION_PATTERN.matcher(line);
        if (sessionMatcher.find()) {
            session = fuzzyService.mapValue("session", sessionMatcher.group());
            fieldConfidences.put("session", 0.95);
        }

        // Detect Department using known list
        for (String dept : DEPARTMENTS) {
            Pattern deptPattern = Pattern.compile("\\b" + dept + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher deptMatcher = deptPattern.matcher(line);
            if (deptMatcher.find()) {
                department = dept;
                fieldConfidences.put("department", 0.9);
                break;
            }
        }

        if (department.isEmpty()) {
            String[] parts = line.split("\\s{2,}");
            if (parts.length > 0) {
                department = stripSectionSuffix(parts[0]);
                if (!department.equals(parts[0])) {
                    fieldConfidences.put("department", 0.7);
                } else {
                    fieldConfidences.put("department", 0.9);
                }
            }
        }

        if (subject.isEmpty() && !courseCode.isEmpty() && line.contains(courseCode)) {
            String[] parts = line.split(courseCode);
            if (parts.length > 1) {
                subject = parts[1].trim();
                // Strip the session if it's at the end of the subject
                subject = subject.replaceAll("(?i)\\b(AN|FN|Forenoon|Afternoon|Morning)\\b", "").trim();
                
                if (subject.length() > 100) {
                    subject = subject.substring(0, 100);
                }
                fieldConfidences.put("subject", 0.8);
            }
        }

        if (department.isEmpty()) department = "UNKNOWN";
        if (year.isEmpty()) year = "1";
        if (courseCode.isEmpty()) courseCode = "UNKNOWN";
        if (subject.isEmpty()) subject = "UNKNOWN";
        if (session.isEmpty()) session = "FN";

        double avgConfidence = fieldConfidences.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.5);

        return ExamEntryDto.builder()
                .department(department)
                .year(Integer.parseInt(year))
                .courseCode(courseCode)
                .subject(subject)
                .examDate(parseDate(dateStr))
                .session(session)
                .confidenceScore(avgConfidence)
                .fieldConfidences(fieldConfidences)
                .requiresReview(avgConfidence < 0.8 || "UNKNOWN".equals(department) || "UNKNOWN".equals(courseCode))
                .build();
    }

    private String extractYear(String yearStr) {
        if (yearStr == null) return "1";
        
        Pattern numPattern = Pattern.compile("\\d+");
        Matcher matcher = numPattern.matcher(yearStr);
        if (matcher.find()) {
            String valStr = matcher.group();
            try {
                int val = Integer.parseInt(valStr);
                // Convert semester (1-8) to academic year (1-4)
                if (yearStr.toLowerCase().contains("sem")) {
                    if (val >= 1 && val <= 8) {
                        return String.valueOf((val + 1) / 2);
                    }
                }
                return String.valueOf(val);
            } catch (NumberFormatException ignored) {}
        }
        
        String upper = yearStr.toUpperCase();
        if (upper.contains("VIII")) return "4";
        if (upper.contains("VII")) return "4";
        if (upper.contains("VI")) return "3";
        if (upper.contains("V")) return "3";
        if (upper.contains("IV")) return "2";
        if (upper.contains("III")) return "2";
        if (upper.contains("II")) return "1";
        if (upper.contains("I")) return "1";
        
        return "1";
    }

    private LocalDate parseDate(String dateStr) {
        String[] patterns = {
                "dd/MM/yyyy", "dd-MM-yyyy", "d/M/yyyy", "d-M-yyyy",
                "MM/dd/yyyy", "MM-dd-yyyy", "M/d/yyyy", "M-d-yyyy",
                "yyyy-MM-dd", "yyyy/MM/dd",
                "dd MMM yyyy", "dd MMMM yyyy", "d MMM yyyy"
        };

        String cleaned = dateStr.replaceAll("\\s+", " ").trim();
        
        for (String pattern : patterns) {
            try {
                return LocalDate.parse(cleaned, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {}
        }

        cleaned = cleaned.toLowerCase();
        for (int i = 0; i < MONTH_NAMES.length; i++) {
            if (cleaned.contains(MONTH_NAMES[i])) {
                cleaned = cleaned.replace(MONTH_NAMES[i], String.format("%02d", i + 1));
                String[] parts = cleaned.split("[\\s,/-]+");
                if (parts.length >= 2) {
                    try {
                        if (parts[0].matches("\\d{1,2}")) {
                            return LocalDate.parse(
                                    String.format("%s/%s/%s", 
                                            parts[0], 
                                            parts[1], 
                                            parts.length > 2 && parts[2].length() == 4 ? parts[2] : LocalDate.now().getYear()),
                                    DateTimeFormatter.ofPattern("d/M/yyyy")
                            );
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        return LocalDate.now().plusMonths(1);
    }

    public String stripSectionSuffix(String dept) {
        if (dept == null) return "";
        return dept.replaceAll("\\s+[A-Z]\\s*$", "")
                   .replaceAll("\\s+SEC(TION)?\\s*[A-Z]?\\s*$", "")
                   .replaceAll("\\s+Section\\s*[A-Z]?\\s*$", "")
                   .replaceAll("\\s+[A-Z]$", "")
                   .trim();
    }

    public ExamEntryDto mapToExamEntry(ExamEntryDto dto) {
        return ExamEntryDto.builder()
                .department(stripSectionSuffix(dto.getDepartment()))
                .year(dto.getYear())
                .courseCode(dto.getCourseCode())
                .subject(dto.getSubject())
                .examDate(dto.getExamDate())
                .session(dto.getSession())
                .confidenceScore(dto.getConfidenceScore())
                .fieldConfidences(dto.getFieldConfidences())
                .requiresReview(dto.isRequiresReview())
                .build();
    }

    public String extractRawText(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}