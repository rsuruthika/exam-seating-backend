package com.examseat.repository;

import com.examseat.model.ExamTimetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExamTimetableRepository extends JpaRepository<ExamTimetable, Long> {
    List<ExamTimetable> findByExamDateAndSession(LocalDate examDate, String session);
    List<ExamTimetable> findByDepartmentAndYear(String department, Integer year);
}
