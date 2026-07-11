package com.examseat.service;

import com.examseat.model.Student;
import com.examseat.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentService {

    @Autowired
    private StudentRepository studentRepository;

    @Transactional
    public List<Student> saveAll(List<Student> students) {
        // Clear old students to avoid unique regNo conflicts if reloading
        studentRepository.deleteAll();
        studentRepository.flush();
        
        // Remove duplicates within the incoming list by regNo
        java.util.Set<String> seenRegNos = new java.util.HashSet<>();
        List<Student> uniqueStudents = students.stream()
                .filter(s -> s.getRegNo() != null && seenRegNos.add(s.getRegNo().trim()))
                .toList();
                
        return studentRepository.saveAll(uniqueStudents);
    }

    public List<Student> getAll() {
        return studentRepository.findAll();
    }

    public java.util.Optional<Student> getByRegNo(String regNo) {
        return studentRepository.findByRegNo(regNo);
    }

    @Transactional
    public void deleteAll() {
        studentRepository.deleteAll();
    }
}
