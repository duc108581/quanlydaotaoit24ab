package com.example.quanlydaotao.repository;

import java.util.UUID;

import com.example.quanlydaotao.entity.Department;

public interface DepartmentRepository extends SoftDeleteRepository<Department> {
    boolean existsByCodeAndDeletedAtIsNull(String code);
    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, UUID id);
}
