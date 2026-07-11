package com.examseat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "exam_timetable")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamTimetable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String department; // Unique Department name (e.g., CCE, CSE)

    @Column(name = "exam_year", nullable = false)
    private Integer year;

    @Column(nullable = false)
    private String courseCode;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private LocalDate examDate;

    @Column(nullable = false)
    private String session; // FN or AN
}
