package com.example.quanlydaotao.service;

import java.util.UUID;
import java.util.Locale;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.entity.AcademicYear;
import com.example.quanlydaotao.entity.Department;
import com.example.quanlydaotao.entity.Lecturer;
import com.example.quanlydaotao.entity.Major;
import com.example.quanlydaotao.entity.StudentClass;
import com.example.quanlydaotao.entity.TrainingProgram;
import com.example.quanlydaotao.repository.AcademicYearRepository;
import com.example.quanlydaotao.repository.DepartmentRepository;
import com.example.quanlydaotao.repository.LecturerRepository;
import com.example.quanlydaotao.repository.MajorRepository;
import com.example.quanlydaotao.repository.StudentClassHistoryRepository;
import com.example.quanlydaotao.repository.StudentClassRepository;
import com.example.quanlydaotao.repository.StudentRepository;
import com.example.quanlydaotao.repository.TrainingProgramRepository;

@Service
public class StudentClassService extends AbstractCrudService<StudentClass> {

    private final StudentClassRepository repository;
    private final AcademicYearRepository academicYearRepository;
    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;
    private final TrainingProgramRepository programRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentRepository studentRepository;
    private final StudentClassHistoryRepository historyRepository;

    public StudentClassService(
            StudentClassRepository repository,
            AcademicYearRepository academicYearRepository,
            DepartmentRepository departmentRepository,
            MajorRepository majorRepository,
            TrainingProgramRepository programRepository,
            LecturerRepository lecturerRepository,
            StudentRepository studentRepository,
            StudentClassHistoryRepository historyRepository) {
        super(repository);
        this.repository = repository;
        this.academicYearRepository = academicYearRepository;
        this.departmentRepository = departmentRepository;
        this.majorRepository = majorRepository;
        this.programRepository = programRepository;
        this.lecturerRepository = lecturerRepository;
        this.studentRepository = studentRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    @Transactional
    public StudentClass create(StudentClass studentClass) {
        validate(studentClass, null, false);
        return super.create(studentClass);
    }

    @Override
    @Transactional
    public StudentClass update(UUID id, StudentClass studentClass) {
        StudentClass current = repository.findByIdAndDeletedAtIsNullForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp sinh viên"));
        if (studentClass.getIsActive() == null) {
            studentClass.setIsActive(current.getIsActive());
        }
        boolean allowUnchangedInactive = !Boolean.FALSE.equals(current.getIsActive())
                || !Boolean.TRUE.equals(studentClass.getIsActive());
        validate(studentClass, id, allowUnchangedInactive);
        return super.update(id, studentClass);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        StudentClass current = repository.findByIdAndDeletedAtIsNullForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp sinh viên"));
        if (studentRepository.countByStudentClassessId(id) > 0
                || historyRepository.countByStudentClassId(id) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Lớp đã có sinh viên hoặc lịch sử lớp; hãy ngừng hoạt động thay vì xóa");
        }
        super.delete(current.getId());
    }

    @Transactional
    public StudentClass setActive(UUID id, boolean active) {
        StudentClass studentClass = repository.findByIdAndDeletedAtIsNullForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp sinh viên"));
        if (active) {
            validate(studentClass, id, false);
        }
        studentClass.setIsActive(active);
        return repository.save(studentClass);
    }

    private void validate(StudentClass studentClass, UUID currentId, boolean allowUnchangedInactive) {
        StudentClass existing = currentId == null ? null : repository.findByIdAndDeletedAtIsNull(currentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp sinh viên"));
        studentClass.setCode(clean(studentClass.getCode()));
        if (studentClass.getCode() != null) {
            studentClass.setCode(studentClass.getCode().toUpperCase(Locale.ROOT));
        }
        studentClass.setName(clean(studentClass.getName()));
        if (studentClass.getCode() == null || studentClass.getName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã lớp và tên lớp là bắt buộc");
        }
        if (studentClass.getCode().length() > 100 || studentClass.getName().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã lớp tối đa 100 ký tự, tên lớp tối đa 255 ký tự");
        }
        if (studentClass.getAcademicYearId() == null || studentClass.getDepartmentId() == null
                || studentClass.getMajorId() == null || studentClass.getTrainingProgramId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Niên khóa, khoa, ngành và chương trình là bắt buộc");
        }
        AcademicYear academicYear = academicYearRepository.findByIdAndDeletedAtIsNull(studentClass.getAcademicYearId())
                .filter(item -> isActive(item) || (allowUnchangedInactive && existing != null
                        && Objects.equals(existing.getAcademicYearId(), item.getId())))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Niên khóa không tồn tại hoặc đã ngừng hoạt động"));
        Department department = departmentRepository.findByIdAndDeletedAtIsNull(studentClass.getDepartmentId())
                .filter(item -> isActive(item) || (allowUnchangedInactive && existing != null
                        && Objects.equals(existing.getDepartmentId(), item.getId())))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Khoa không tồn tại hoặc đã ngừng hoạt động"));
        Major major = majorRepository.findByIdAndDeletedAtIsNull(studentClass.getMajorId())
                .filter(item -> isActive(item) || (allowUnchangedInactive && existing != null
                        && Objects.equals(existing.getMajorId(), item.getId())))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Ngành không tồn tại hoặc đã ngừng hoạt động"));
        TrainingProgram program = programRepository.findByIdAndDeletedAtIsNull(studentClass.getTrainingProgramId())
                .filter(item -> isActive(item) || (allowUnchangedInactive && existing != null
                        && Objects.equals(existing.getTrainingProgramId(), item.getId())))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Chương trình không tồn tại hoặc đã ngừng hoạt động"));
        if (!studentClass.getDepartmentId().equals(major.getDepartmentId())
                || !studentClass.getDepartmentId().equals(program.getDepartmentId())
                || !studentClass.getMajorId().equals(program.getMajorId())
                || !studentClass.getDepartmentId().equals(department.getId())
                || !studentClass.getAcademicYearId().equals(academicYear.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoa, ngành và chương trình đào tạo không khớp");
        }
        if (studentClass.getAdvisorId() != null) {
            lecturerRepository.findByIdAndDeletedAtIsNull(studentClass.getAdvisorId())
                    .filter(item -> isActive(item) || (allowUnchangedInactive && existing != null
                            && Objects.equals(existing.getAdvisorId(), item.getId())))
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Giảng viên cố vấn không tồn tại hoặc đã ngừng hoạt động"));
        }
        boolean duplicate = currentId == null
                ? repository.existsByCode(studentClass.getCode())
                : repository.existsByCodeAndIdNot(studentClass.getCode(), currentId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã lớp sinh viên đã tồn tại");
        }
    }

    private boolean isActive(com.example.quanlydaotao.entity.AuditedEntity entity) {
        return !Boolean.FALSE.equals(entity.getIsActive());
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
