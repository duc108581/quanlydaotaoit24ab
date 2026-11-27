package com.example.quanlydaotao.service;

import java.util.UUID;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.entity.Major;
import com.example.quanlydaotao.entity.TrainingProgram;
import com.example.quanlydaotao.repository.DepartmentRepository;
import com.example.quanlydaotao.repository.MajorRepository;
import com.example.quanlydaotao.repository.TrainingProgramRepository;

@Service
public class TrainingProgramService extends AbstractCrudService<TrainingProgram> {

    private final TrainingProgramRepository repository;
    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;

    public TrainingProgramService(
            TrainingProgramRepository repository,
            DepartmentRepository departmentRepository,
            MajorRepository majorRepository) {
        super(repository);
        this.repository = repository;
        this.departmentRepository = departmentRepository;
        this.majorRepository = majorRepository;
    }

    @Override
    public TrainingProgram create(TrainingProgram program) {
        validate(program, null);
        return super.create(program);
    }

    @Override
    public TrainingProgram update(UUID id, TrainingProgram program) {
        validate(program, id);
        return super.update(id, program);
    }

    private void validate(TrainingProgram program, UUID currentId) {
        program.setCode(clean(program.getCode()));
        if (program.getCode() != null) program.setCode(program.getCode().toUpperCase(Locale.ROOT));
        program.setName(clean(program.getName()));
        if (program.getCode() == null || program.getName() == null
                || program.getDepartmentId() == null || program.getMajorId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Mã, tên chương trình, khoa và ngành là bắt buộc");
        }
        if (program.getCode().length() > 50 || program.getName().length() > 255
                || (program.getStatus() != null && program.getStatus().length() > 30)
                || (program.getDescription() != null && program.getDescription().length() > 500)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Thông tin chương trình đào tạo vượt quá độ dài cho phép");
        }
        if (program.getAdmissionYear() != null && program.getAdmissionYear() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Năm tuyển sinh phải là năm dương");
        }
        if (program.getTotalCredits() != null
                && (program.getTotalCredits().signum() < 0
                        || program.getTotalCredits().precision() - program.getTotalCredits().scale() > 4
                        || program.getTotalCredits().scale() > 2)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tổng số tín chỉ không hợp lệ");
        }
        if (program.getDurationYears() != null
                && (program.getDurationYears().signum() <= 0
                        || program.getDurationYears().precision() - program.getDurationYears().scale() > 3
                        || program.getDurationYears().scale() > 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian đào tạo không hợp lệ");
        }
        departmentRepository.findByIdAndDeletedAtIsNull(program.getDepartmentId())
                .filter(department -> !Boolean.FALSE.equals(department.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoa không tồn tại hoặc đã ngừng hoạt động"));
        Major major = majorRepository.findByIdAndDeletedAtIsNull(program.getMajorId())
                .filter(item -> !Boolean.FALSE.equals(item.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngành không tồn tại hoặc đã ngừng hoạt động"));
        if (!program.getDepartmentId().equals(major.getDepartmentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngành được chọn không thuộc khoa đã chọn");
        }
        if (program.getEffectiveDate() != null && program.getExpiryDate() != null
                && program.getExpiryDate().isBefore(program.getEffectiveDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày hiệu lực");
        }
        boolean duplicate = currentId == null
                ? repository.existsByMajorIdAndCode(program.getMajorId(), program.getCode())
                : repository.existsByMajorIdAndCodeAndIdNot(program.getMajorId(), program.getCode(), currentId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã chương trình đào tạo đã tồn tại");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
