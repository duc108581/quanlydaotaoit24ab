package com.example.quanlydaotao.repository;

import java.util.UUID;

import com.example.quanlydaotao.entity.Lecturer;

public interface LecturerRepository extends SoftDeleteRepository<Lecturer> {
    boolean existsByLecturerCodeAndDeletedAtIsNull(String code);
    boolean existsByLecturerCodeAndDeletedAtIsNullAndIdNot(String code, UUID id);
}
