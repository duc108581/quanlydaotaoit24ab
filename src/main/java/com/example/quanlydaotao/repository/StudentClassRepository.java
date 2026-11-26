package com.example.quanlydaotao.repository;

import java.util.UUID;

import com.example.quanlydaotao.entity.StudentClass;

public interface StudentClassRepository extends SoftDeleteRepository<StudentClass> {
    boolean existsByCodeAndDeletedAtIsNull(String code);
    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, UUID id);
}
