package com.example.quanlydaotao.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.quanlydaotao.entity.StudentClass;

public interface StudentClassRepository extends SoftDeleteRepository<StudentClass> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT studentClass FROM StudentClass studentClass WHERE studentClass.id = :id AND studentClass.deletedAt IS NULL")
    Optional<StudentClass> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);
}
