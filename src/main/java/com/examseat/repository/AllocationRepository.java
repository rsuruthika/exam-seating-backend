package com.examseat.repository;

import com.examseat.model.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {
    Optional<Allocation> findByRegNo(String regNo);

    // Single bulk DELETE instead of row-by-row (avoids N+1 delete queries on large datasets)
    @Modifying
    @Query("DELETE FROM Allocation a")
    void bulkDeleteAll();
}
