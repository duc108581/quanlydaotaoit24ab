package com.example.quanlydaotao.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.quanlydaotao.entity.StudentStatusHistory;

public interface StudentStatusHistoryRepository extends JpaRepository<StudentStatusHistory, UUID> {

    Optional<StudentStatusHistory> findFirstByStudentIdAndCurrentTrue(UUID studentId);
}
