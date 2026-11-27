package com.example.quanlydaotao.repository;

import com.example.quanlydaotao.entity.SchoolYear;

public interface SchoolYearRepository extends SoftDeleteRepository<SchoolYear> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, java.util.UUID id);
}
