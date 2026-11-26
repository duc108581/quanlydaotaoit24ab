package com.example.quanlydaotao.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.quanlydaotao.entity.CourseSection;

public interface CourseSectionRepository extends SoftDeleteRepository<CourseSection> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT section FROM CourseSection section WHERE section.id = :id AND section.deletedAt IS NULL")
    Optional<CourseSection> findByIdForUpdate(@Param("id") UUID id);
}
