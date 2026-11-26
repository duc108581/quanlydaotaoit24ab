package com.example.quanlydaotao.repository;

import java.util.UUID;

import com.example.quanlydaotao.entity.LecturerCourseClass;

public interface LecturerCourseClassRepository extends SoftDeleteRepository<LecturerCourseClass> {

    boolean existsByCourseSectionIdAndIsActiveTrueAndDeletedAtIsNull(UUID courseSectionId);

    boolean existsByCourseSectionIdAndIsActiveTrueAndDeletedAtIsNullAndIdNot(UUID courseSectionId, UUID id);
}
