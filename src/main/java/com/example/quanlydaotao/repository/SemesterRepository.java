package com.example.quanlydaotao.repository;

import com.example.quanlydaotao.entity.Semester;

public interface SemesterRepository extends SoftDeleteRepository<Semester> {
    boolean existsBySchoolYearIdAndCode(java.util.UUID schoolYearId, String code);
    boolean existsBySchoolYearIdAndCodeAndIdNot(
            java.util.UUID schoolYearId, String code, java.util.UUID id);
}
