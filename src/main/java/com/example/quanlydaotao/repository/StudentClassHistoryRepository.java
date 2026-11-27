package com.example.quanlydaotao.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.quanlydaotao.entity.StudentClassHistory;

public interface StudentClassHistoryRepository extends JpaRepository<StudentClassHistory, UUID> {

    Optional<StudentClassHistory> findFirstByStudentIdAndCurrentTrue(UUID studentId);

    long countByStudentClassId(UUID studentClassId);
}
