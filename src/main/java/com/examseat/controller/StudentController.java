package com.examseat.controller;

import com.examseat.model.Student;
import com.examseat.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    @Autowired
    private StudentService studentService;

    @PostMapping("/upload")
    public List<Student> uploadStudents(@RequestBody List<Student> students) {
        return studentService.saveAll(students);
    }

    @GetMapping
    public List<Student> getAllStudents() {
        return studentService.getAll();
    }

    @GetMapping("/{regNo}")
    public org.springframework.http.ResponseEntity<Student> getStudentByRegNo(@PathVariable String regNo) {
        return studentService.getByRegNo(regNo)
                .map(org.springframework.http.ResponseEntity::ok)
                .orElse(org.springframework.http.ResponseEntity.notFound().build());
    }

    @DeleteMapping
    public void deleteAll() {
        studentService.deleteAll();
    }
}
