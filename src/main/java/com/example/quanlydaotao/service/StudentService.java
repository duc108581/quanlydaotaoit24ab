
package com.example.quanlydaotao.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.entity.Student;
import com.example.quanlydaotao.entity.StudentClass;
import com.example.quanlydaotao.entity.StudentClassHistory;
import com.example.quanlydaotao.entity.StudentStatus;
import com.example.quanlydaotao.entity.StudentStatusHistory;
import com.example.quanlydaotao.repository.StudentClassRepository;
import com.example.quanlydaotao.repository.StudentClassHistoryRepository;
import com.example.quanlydaotao.repository.StudentRepository;
import com.example.quanlydaotao.repository.StudentStatusRepository;
import com.example.quanlydaotao.repository.StudentStatusHistoryRepository;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final StudentClassRepository studentClassRepository;
    private final StudentStatusRepository studentStatusRepository;
    private final StudentClassHistoryRepository classHistoryRepository;
    private final StudentStatusHistoryRepository statusHistoryRepository;
    private final JdbcTemplate jdbcTemplate;
    private final UserAccountProvisioningService accountProvisioningService;

    public StudentService(
            StudentRepository studentRepository,
            StudentClassRepository studentClassRepository,
            StudentStatusRepository studentStatusRepository,
            StudentClassHistoryRepository classHistoryRepository,
            StudentStatusHistoryRepository statusHistoryRepository,
            JdbcTemplate jdbcTemplate,
            UserAccountProvisioningService accountProvisioningService) {
        this.studentRepository = studentRepository;
        this.studentClassRepository = studentClassRepository;
        this.studentStatusRepository = studentStatusRepository;
        this.classHistoryRepository = classHistoryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.accountProvisioningService = accountProvisioningService;
    }

    public List<Student> getAll(boolean includeInactive) {
        return studentRepository.findAll().stream()
                .filter(s -> s.getDeletedAt() == null)
                .filter(s -> includeInactive || !Boolean.FALSE.equals(s.getIsActive()))
                .map(this::hydrate)
                .collect(Collectors.toList());
    }

    public List<Student> search(String keyword, boolean includeInactive) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAll(includeInactive);
        }

        return studentRepository.searchStudents(keyword.trim()).stream()
                .filter(s -> s.getDeletedAt() == null)
                .filter(s -> includeInactive || !Boolean.FALSE.equals(s.getIsActive()))
                .map(this::hydrate)
                .collect(Collectors.toList());
    }

    public Student getById(UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy sinh viên: " + id));

        if (student.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sinh viên đã bị xóa mềm");
        }

        return hydrate(student);
    }

    @Transactional
    public Student save(Student student) {
        LocalDateTime now = LocalDateTime.now();
        boolean creating = student.getId() == null;
        Student previous = null;
        StudentStatus selectedStatus = findStatus(student.getStatusId());
        if (creating) {
            validateCodeFormat(student.getCode());
            applyClassDetails(student, null);
            validate(student, null);
            if (student.getCreatedAt() == null) {
                student.setCreatedAt(now);
            }
            if (student.getIsActive() == null) {
                student.setIsActive(true);
            }
        } else {
            previous = studentRepository.findById(student.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy sinh viên cần cập nhật"));
            if (student.getUserId() == null) {
                student.setUserId(previous.getUserId());
            }
            if (student.getStatusId() == null) {
                student.setStatusId(statusHistoryRepository
                        .findFirstByStudentIdAndCurrentTrue(previous.getId())
                        .map(StudentStatusHistory::getStatusId)
                        .orElse(null));
                selectedStatus = findStatus(student.getStatusId());
            }
            if (!Objects.equals(normalize(previous.getCode()), normalize(student.getCode()))) {
                validateCodeFormat(student.getCode());
            }

            student.setCreatedAt(previous.getCreatedAt());
            student.setCreatedBy(previous.getCreatedBy());
            student.setDeletedAt(previous.getDeletedAt());
            student.setDeletedBy(previous.getDeletedBy());

            if (student.getIsActive() == null) {
                student.setIsActive(previous.getIsActive());
            }
            applyClassDetails(student, previous);
            validate(student, previous.getId());
        }

        student.setCode(student.getCode().trim().toUpperCase(Locale.ROOT));
        student.setFullName(student.getFullName().trim());
        if (student.getEmail() != null) {
            student.setEmail(student.getEmail().trim().toLowerCase(Locale.ROOT));
        }
        if (student.getPhone() != null) {
            student.setPhone(student.getPhone().trim());
        }
        if (creating && student.getUserId() == null) {
            UUID userId = accountProvisioningService.createAccount(
                    student.getCode(), student.getFullName(), student.getInitialPassword(), List.of("STUDENT"));
            student.setUserId(userId);
        }
        student.setInitialPassword(null);
        if (student.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sinh viên phải được liên kết với tài khoản");
        }
        Integer accountCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT account.id)
                FROM users account
                JOIN user_roles assignedRole ON assignedRole.user_id = account.id
                JOIN roles assigned ON assigned.id = assignedRole.role_id
                WHERE account.id = ?
                  AND account.is_active = 1
                  AND assigned.code = N'STUDENT'
                  AND assigned.is_active = 1
                """, Integer.class, student.getUserId());
        if (accountCount == null || accountCount != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Tài khoản sinh viên không tồn tại hoặc đã bị vô hiệu hóa");
        }
        student.setUpdatedAt(now);
        Student savedStudent = studentRepository.save(student);
        if (Boolean.FALSE.equals(savedStudent.getIsActive())) {
            deactivateAccount(savedStudent.getUserId(), now);
        }
        recordClassChange(savedStudent, creating ? null : previous, now);
        recordStatusChange(savedStudent, selectedStatus, now);
        savedStudent.setStatusId(selectedStatus == null ? null : selectedStatus.getId());
        return hydrate(savedStudent);
    }

    private StudentStatus findStatus(UUID statusId) {
        if (statusId == null) {
            return null;
        }
        return studentStatusRepository.findByIdAndDeletedAtIsNull(statusId)
                .filter(status -> !Boolean.FALSE.equals(status.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Trạng thái sinh viên không tồn tại hoặc đã ngừng hoạt động"));
    }

    @Transactional
    public Student update(UUID id, JsonNode changes) {
        Student student = getById(id);
        if (changes.has("code") || changes.has("studentCode")) {
            student.setCode(textValue(changes.has("code") ? changes.get("code") : changes.get("studentCode")));
        }
        if (changes.has("userId")) student.setUserId(uuidValue(changes.get("userId")));
        if (changes.has("fullName") || changes.has("name")) {
            student.setFullName(textValue(changes.has("fullName") ? changes.get("fullName") : changes.get("name")));
        }
        if (changes.has("email")) student.setEmail(textValue(changes.get("email")));
        if (changes.has("phone")) student.setPhone(textValue(changes.get("phone")));
        if (changes.has("dateOfBirth")) student.setDateOfBirth(dateValue(changes.get("dateOfBirth")));
        if (changes.has("gender")) student.setGender(textValue(changes.get("gender")));
        if (changes.has("personalIdentificationNumber")) {
            student.setPersonalIdentificationNumber(textValue(changes.get("personalIdentificationNumber")));
        }
        if (changes.has("dateOfIssue")) student.setDateOfIssue(dateValue(changes.get("dateOfIssue")));
        if (changes.has("cardPlace")) student.setCardPlace(textValue(changes.get("cardPlace")));
        if (changes.has("address")) student.setAddress(textValue(changes.get("address")));
        if (changes.has("currentAddress")) student.setCurrentAddress(textValue(changes.get("currentAddress")));
        if (changes.has("departmentId")) student.setDepartmentId(uuidValue(changes.get("departmentId")));
        if (changes.has("majorId")) student.setMajorId(uuidValue(changes.get("majorId")));
        if (changes.has("trainingProgramId")) {
            student.setTrainingProgramId(uuidValue(changes.get("trainingProgramId")));
        }
        if (changes.has("statusId")) student.setStatusId(uuidValue(changes.get("statusId")));
        if (changes.has("studentClassessId")) {
            student.setStudentClassessId(uuidValue(changes.get("studentClassessId")));
        }
        if (changes.has("admissionYear")) student.setAdmissionYear(shortValue(changes.get("admissionYear")));
        if (changes.has("isActive") && !changes.get("isActive").isNull()) {
            student.setIsActive(changes.get("isActive").asBoolean());
        }
        return save(student);
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText().trim();
    }

    private UUID uuidValue(JsonNode node) {
        String value = textValue(node);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Một giá trị liên kết không phải UUID hợp lệ");
        }
    }

    private LocalDate dateValue(JsonNode node) {
        String value = textValue(node);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày tháng không hợp lệ");
        }
    }

    private Short shortValue(JsonNode node) {
        String value = textValue(node);
        if (value == null) return null;
        try {
            return Short.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Năm tuyển sinh không hợp lệ");
        }
    }

    private void validate(Student student, UUID currentId) {
        if (student.getCode() == null || student.getCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã sinh viên là bắt buộc");
        }
        if (student.getFullName() == null || student.getFullName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ và tên là bắt buộc");
        }
        if (student.getStudentClassessId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn lớp sinh viên");
        }
        boolean duplicateCode = currentId == null
                ? studentRepository.existsByCodeAndDeletedAtIsNull(student.getCode().trim())
                : studentRepository.existsByCodeAndDeletedAtIsNullAndIdNot(
                        student.getCode().trim(), currentId);
        if (duplicateCode) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã sinh viên đã tồn tại");
        }
        if (student.getEmail() != null && !student.getEmail().isBlank()) {
            if (!student.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không hợp lệ");
            }
            boolean duplicateEmail = currentId == null
                    ? studentRepository.existsByEmailAndDeletedAtIsNull(student.getEmail().trim().toLowerCase(Locale.ROOT))
                    : studentRepository.existsByEmailAndDeletedAtIsNullAndIdNot(
                            student.getEmail().trim().toLowerCase(Locale.ROOT), currentId);
            if (duplicateEmail) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng");
            }
        }
        if (student.getPhone() != null && !student.getPhone().isBlank()
                && !student.getPhone().matches("^[+0-9() .-]{7,20}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ");
        }
    }

    private void validateCodeFormat(String code) {
        if (code == null || !code.trim().matches("(?i)^SV[0-9]{4,}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Mã sinh viên mới phải theo mẫu SV0001 (SV và ít nhất 4 chữ số)");
        }
    }

    private void applyClassDetails(Student student, Student previous) {
        if (student.getStudentClassessId() == null) {
            if (previous == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn lớp sinh viên");
            }
            return;
        }
        if (previous != null && Objects.equals(previous.getStudentClassessId(), student.getStudentClassessId())) {
            return;
        }
        StudentClass studentClass = studentClassRepository.findByIdAndDeletedAtIsNull(student.getStudentClassessId())
                .filter(item -> !Boolean.FALSE.equals(item.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Lớp sinh viên không tồn tại hoặc đã ngừng hoạt động"));
        if (studentClass.getDepartmentId() != null) {
            student.setDepartmentId(studentClass.getDepartmentId());
        }
        if (studentClass.getMajorId() != null) {
            student.setMajorId(studentClass.getMajorId());
        }
        if (studentClass.getTrainingProgramId() != null) {
            student.setTrainingProgramId(studentClass.getTrainingProgramId());
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private Student hydrate(Student student) {
        classHistoryRepository.findFirstByStudentIdAndCurrentTrue(student.getId())
                .ifPresent(history -> {
                    if (student.getStudentClassessId() == null) {
                        student.setStudentClassessId(history.getStudentClassId());
                    }
                });
        statusHistoryRepository.findFirstByStudentIdAndCurrentTrue(student.getId())
                .ifPresent(history -> student.setStatusId(history.getStatusId()));
        if (student.getStudentClassessId() != null) {
            studentClassRepository.findByIdAndDeletedAtIsNull(student.getStudentClassessId())
                    .ifPresent(studentClass -> {
                        student.setDepartmentId(studentClass.getDepartmentId());
                        student.setMajorId(studentClass.getMajorId());
                        student.setTrainingProgramId(studentClass.getTrainingProgramId());
                    });
        }
        return student;
    }

    private void recordClassChange(Student student, Student previous, LocalDateTime now) {
        UUID previousClassId = previous == null ? null : previous.getStudentClassessId();
        UUID newClassId = student.getStudentClassessId();
        StudentClassHistory current = classHistoryRepository
                .findFirstByStudentIdAndCurrentTrue(student.getId()).orElse(null);
        if (current != null) {
            previousClassId = current.getStudentClassId();
        }
        if (Objects.equals(previousClassId, newClassId)) {
            if (current == null && newClassId != null) {
                classHistoryRepository.save(newClassHistory(student.getId(), newClassId, now));
            }
            return;
        }
        if (current == null && previousClassId != null) {
            StudentClassHistory prior = newClassHistory(student.getId(), previousClassId, now);
            prior.setCurrent(false);
            prior.setEffectiveFrom(previous.getCreatedAt() == null
                    ? now.toLocalDate()
                    : previous.getCreatedAt().toLocalDate());
            prior.setEffectiveTo(now.toLocalDate());
            classHistoryRepository.save(prior);
        }
        if (current != null) {
            current.setCurrent(false);
            current.setEffectiveTo(now.toLocalDate());
            classHistoryRepository.save(current);
        }
        if (newClassId != null) {
            classHistoryRepository.save(newClassHistory(student.getId(), newClassId, now));
        }
    }

    private StudentClassHistory newClassHistory(UUID studentId, UUID studentClassId, LocalDateTime now) {
        StudentClassHistory history = new StudentClassHistory();
        history.setStudentId(studentId);
        history.setStudentClassId(studentClassId);
        history.setEffectiveFrom(now.toLocalDate());
        history.setCurrent(true);
        history.setCreatedAt(now);
        return history;
    }

    private void recordStatusChange(Student student, StudentStatus selectedStatus, LocalDateTime now) {
        StudentStatusHistory current = statusHistoryRepository
                .findFirstByStudentIdAndCurrentTrue(student.getId()).orElse(null);
        UUID selectedId = selectedStatus == null ? null : selectedStatus.getId();
        if (Objects.equals(current == null ? null : current.getStatusId(), selectedId)) {
            return;
        }
        if (current != null) {
            current.setCurrent(false);
            current.setEffectiveTo(now);
            statusHistoryRepository.save(current);
        }
        if (selectedId != null) {
            StudentStatusHistory history = new StudentStatusHistory();
            history.setStudentId(student.getId());
            history.setStatusId(selectedId);
            history.setEffectiveFrom(now);
            history.setCurrent(true);
            history.setCreatedAt(now);
            statusHistoryRepository.save(history);
        }
    }

    @Transactional
    public void delete(UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy sinh viên: " + id));

        student.setIsActive(false);
        student.setDeletedAt(LocalDateTime.now());
        student.setUpdatedAt(LocalDateTime.now());

        studentRepository.save(student);
        deactivateAccount(student.getUserId(), student.getUpdatedAt());
    }

    private void deactivateAccount(UUID userId, LocalDateTime now) {
        if (userId != null) {
            jdbcTemplate.update("""
                    UPDATE users
                    SET is_active = 0, updated_at = ?
                    WHERE id = ?
                    """, now, userId);
        }
    }
}
