package com.example.quanlydaotao.repository;

import java.util.UUID;

import com.example.quanlydaotao.entity.Major;

public interface MajorRepository extends SoftDeleteRepository<Major> {
    boolean existsByCodeAndDeletedAtIsNull(String code);
    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, UUID id);
}
