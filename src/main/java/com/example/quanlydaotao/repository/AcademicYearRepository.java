package com.example.quanlydaotao.repository;

import com.example.quanlydaotao.entity.AcademicYear;

public interface AcademicYearRepository extends SoftDeleteRepository<AcademicYear> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, java.util.UUID id);
}
