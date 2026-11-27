package com.example.quanlydaotao.repository;

import java.util.UUID;

import com.example.quanlydaotao.entity.Department;

public interface DepartmentRepository extends SoftDeleteRepository<Department> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, UUID id);
}
