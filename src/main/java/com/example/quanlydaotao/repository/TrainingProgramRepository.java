package com.example.quanlydaotao.repository;

import java.util.UUID;

import com.example.quanlydaotao.entity.TrainingProgram;

public interface TrainingProgramRepository extends SoftDeleteRepository<TrainingProgram> {
    boolean existsByCodeAndDeletedAtIsNull(String code);
    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, UUID id);
}
