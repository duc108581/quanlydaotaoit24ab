package com.example.quanlydaotao.repository;

import java.util.UUID;

import com.example.quanlydaotao.entity.Major;

public interface MajorRepository extends SoftDeleteRepository<Major> {
    boolean existsByDepartmentIdAndCode(UUID departmentId, String code);
    boolean existsByDepartmentIdAndCodeAndIdNot(UUID departmentId, String code, UUID id);
}
