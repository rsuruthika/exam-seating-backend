package com.examseat.service;

import com.examseat.model.Allocation;
import com.examseat.repository.AllocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AllocationService {

    @Autowired
    private AllocationRepository allocationRepository;

    @Transactional
    public List<Allocation> saveAllocations(List<Allocation> allocations) {
        // Bulk delete with a single SQL statement (much faster than row-by-row deleteAll)
        allocationRepository.bulkDeleteAll();

        // De-duplicate by regNo (keep last occurrence) and filter nulls
        Map<String, Allocation> deduped = new LinkedHashMap<>();
        for (Allocation a : allocations) {
            if (a == null) continue;
            String reg = a.getRegNo();
            if (reg == null || reg.isBlank()) continue; // skip rows without regNo
            deduped.put(reg.trim(), a);
        }

        if (deduped.isEmpty()) return new ArrayList<>();

        return allocationRepository.saveAll(new ArrayList<>(deduped.values()));
    }

    public Optional<Allocation> findByRegNo(String regNo) {
        return allocationRepository.findByRegNo(regNo);
    }

    public List<Allocation> getAllAllocations() {
        return allocationRepository.findAll();
    }

    @Transactional
    public void deleteAll() {
        allocationRepository.bulkDeleteAll();
    }
}
