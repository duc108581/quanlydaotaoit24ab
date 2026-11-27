package com.example.quanlydaotao.repository;

import java.util.UUID;

import com.example.quanlydaotao.entity.TrainingProgram;

public interface TrainingProgramRepository extends SoftDeleteRepository<TrainingProgram> {
    boolean existsByMajorIdAndCode(UUID majorId, String code);
    boolean existsByMajorIdAndCodeAndIdNot(UUID majorId, String code, UUID id);
}
