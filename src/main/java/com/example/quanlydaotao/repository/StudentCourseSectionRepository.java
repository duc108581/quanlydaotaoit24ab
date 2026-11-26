package com.example.quanlydaotao.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.quanlydaotao.entity.StudentCourseSection;

public interface StudentCourseSectionRepository extends SoftDeleteRepository<StudentCourseSection> {

    @Query("""
        SELECT CASE WHEN COUNT(registration) > 0 THEN true ELSE false END
        FROM StudentCourseSection registration
        WHERE registration.studentId = :studentId
          AND registration.courseSectionId = :courseSectionId
          AND registration.deletedAt IS NULL
          AND registration.activeRegistration = true
          AND registration.status IN ('REGISTERED', 'CONFIRMED')
          AND (:id IS NULL OR registration.id <> :id)
        """)
    boolean existsActiveRegistration(
            @Param("studentId") UUID studentId,
            @Param("courseSectionId") UUID courseSectionId,
            @Param("id") UUID id);

    @Query("""
        SELECT COUNT(registration)
        FROM StudentCourseSection registration
        WHERE registration.courseSectionId = :courseSectionId
          AND registration.deletedAt IS NULL
          AND registration.activeRegistration = true
          AND registration.status IN ('REGISTERED', 'CONFIRMED')
        """)
    long countActiveByCourseSectionId(@Param("courseSectionId") UUID courseSectionId);

    Optional<StudentCourseSection> findFirstByStudentIdAndCourseSectionIdAndStatusAndDeletedAtIsNullOrderByRegistrationDateDesc(
            UUID studentId, UUID courseSectionId, String status);

    @Query("""
        SELECT COALESCE(MAX(registration.attemptNumber), 0)
        FROM StudentCourseSection registration
        WHERE registration.studentId = :studentId
          AND registration.courseSectionId = :courseSectionId
        """)
    Integer findMaxAttemptNumber(
            @Param("studentId") UUID studentId,
            @Param("courseSectionId") UUID courseSectionId);
}
