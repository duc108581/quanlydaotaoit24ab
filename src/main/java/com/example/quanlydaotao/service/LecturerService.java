package com.example.quanlydaotao.service;

import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.entity.Employee;
import com.example.quanlydaotao.entity.Lecturer;
import com.example.quanlydaotao.repository.DepartmentRepository;
import com.example.quanlydaotao.repository.EmployeeRepository;
import com.example.quanlydaotao.repository.LecturerRepository;

@Service
public class LecturerService extends AbstractCrudService<Lecturer> {

    private final LecturerRepository repository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final JdbcTemplate jdbcTemplate;
    private final UserAccountProvisioningService accountProvisioningService;

    public LecturerService(
            LecturerRepository repository,
            DepartmentRepository departmentRepository,
            EmployeeRepository employeeRepository,
            JdbcTemplate jdbcTemplate,
            UserAccountProvisioningService accountProvisioningService) {
        super(repository);
        this.repository = repository;
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.accountProvisioningService = accountProvisioningService;
    }

    @Override
    public List<Lecturer> list() {
        return repository.findAllByDeletedAtIsNull().stream()
                .map(this::hydrate)
                .collect(Collectors.toList());
    }

    @Override
    public Lecturer get(UUID id) {
        return hydrate(super.get(id));
    }

    @Override
    @Transactional
    public Lecturer create(Lecturer lecturer) {
        validate(lecturer, null);
        if (lecturer.getUserId() == null) {
            lecturer.setUserId(accountProvisioningService.createAccount(
                    lecturer.getLecturerCode(), lecturer.getFullName(),
                    lecturer.getInitialPassword(), List.of("LECTURER")));
        }
        lecturer.setInitialPassword(null);
        Employee employee = toEmployee(lecturer);
        Employee savedEmployee = employeeRepository.save(employee);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO lecturers (id, lecturer_code, created_at, updated_at, is_active)
                VALUES (?, ?, ?, ?, 1)
                """, savedEmployee.getId(), lecturer.getLecturerCode(), now, now);
        lecturer.setId(savedEmployee.getId());
        lecturer.setCreatedAt(now);
        lecturer.setUpdatedAt(now);
        lecturer.setIsActive(true);
        return hydrate(lecturer);
    }

    @Override
    @Transactional
    public Lecturer update(UUID id, Lecturer lecturer) {
        Lecturer existing = get(id);
        lecturer.setInitialPassword(null);
        if (lecturer.getLecturerCode() == null) lecturer.setLecturerCode(existing.getLecturerCode());
        if (lecturer.getFullName() == null) lecturer.setFullName(existing.getFullName());
        if (lecturer.getGender() == null) lecturer.setGender(existing.getGender());
        if (lecturer.getDateOfBirth() == null) lecturer.setDateOfBirth(existing.getDateOfBirth());
        if (lecturer.getEmail() == null) lecturer.setEmail(existing.getEmail());
        if (lecturer.getPhone() == null) lecturer.setPhone(existing.getPhone());
        if (lecturer.getUserId() == null) lecturer.setUserId(existing.getUserId());
        if (lecturer.getDepartmentId() == null) lecturer.setDepartmentId(existing.getDepartmentId());
        if (lecturer.getPositionId() == null) lecturer.setPositionId(existing.getPositionId());
        if (lecturer.getHireDate() == null) lecturer.setHireDate(existing.getHireDate());
        validate(lecturer, id);
        Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Không tìm thấy hồ sơ nhân sự tương ứng với giảng viên"));
        copyEmployeeFields(lecturer, employee);
        employeeRepository.save(employee);
        Lecturer saved = super.update(id, lecturer);
        if (Boolean.FALSE.equals(saved.getIsActive())) {
            deactivateAccount(lecturer.getUserId());
        }
        return hydrate(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Không tìm thấy hồ sơ nhân sự tương ứng với giảng viên"));
        super.delete(id);
        employee.setIsActive(false);
        employee.setDeletedAt(LocalDateTime.now());
        employeeRepository.save(employee);
        deactivateAccount(employee.getUserId());
    }

    private void validate(Lecturer lecturer, UUID currentId) {
        lecturer.setLecturerCode(clean(lecturer.getLecturerCode()));
        lecturer.setFullName(clean(lecturer.getFullName()));
        lecturer.setEmail(clean(lecturer.getEmail()));
        lecturer.setPhone(clean(lecturer.getPhone()));
        if (lecturer.getLecturerCode() != null) {
            lecturer.setLecturerCode(lecturer.getLecturerCode().toUpperCase(Locale.ROOT));
        }
        if (lecturer.getEmail() != null) {
            lecturer.setEmail(lecturer.getEmail().toLowerCase(Locale.ROOT));
        }
        if (lecturer.getLecturerCode() == null || lecturer.getLecturerCode().length() < 3
                || lecturer.getLecturerCode().length() > 50 || lecturer.getFullName() == null
                || lecturer.getDepartmentId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Mã giảng viên (3-50 ký tự), họ tên và khoa là bắt buộc");
        }
        departmentRepository.findByIdAndDeletedAtIsNull(lecturer.getDepartmentId())
                .filter(item -> !Boolean.FALSE.equals(item.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoa không tồn tại hoặc đã ngừng hoạt động"));
        boolean duplicateCode = currentId == null
                ? repository.existsByLecturerCodeAndDeletedAtIsNull(lecturer.getLecturerCode())
                : repository.existsByLecturerCodeAndDeletedAtIsNullAndIdNot(
                        lecturer.getLecturerCode(), currentId);
        if (duplicateCode) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã giảng viên đã tồn tại");
        }
        if (lecturer.getEmail() != null) {
            if (!lecturer.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email giảng viên không hợp lệ");
            }
            boolean duplicateEmail = currentId == null
                    ? employeeRepository.existsByEmailAndDeletedAtIsNull(lecturer.getEmail())
                    : employeeRepository.existsByEmailAndDeletedAtIsNullAndIdNot(
                            lecturer.getEmail(), currentId);
            if (duplicateEmail) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email giảng viên đã được sử dụng");
            }
        }
        if (lecturer.getPhone() != null && !lecturer.getPhone().matches("^[+0-9() .-]{7,20}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại giảng viên không hợp lệ");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void deactivateAccount(UUID userId) {
        if (userId != null) {
            jdbcTemplate.update("""
                    UPDATE users
                    SET is_active = 0, updated_at = ?
                    WHERE id = ?
                    """, LocalDateTime.now(), userId);
        }
    }

    private Employee toEmployee(Lecturer lecturer) {
        Employee employee = new Employee();
        copyEmployeeFields(lecturer, employee);
        employee.setEmployeeCode(lecturer.getLecturerCode());
        employee.setCreatedAt(LocalDateTime.now());
        return employee;
    }

    private void copyEmployeeFields(Lecturer lecturer, Employee employee) {
        employee.setUserId(lecturer.getUserId());
        employee.setEmployeeCode(lecturer.getLecturerCode());
        employee.setFullName(lecturer.getFullName());
        employee.setGender(lecturer.getGender());
        employee.setDateOfBirth(lecturer.getDateOfBirth());
        employee.setEmail(lecturer.getEmail());
        employee.setPhone(lecturer.getPhone());
        employee.setDepartmentId(lecturer.getDepartmentId());
        employee.setPositionId(lecturer.getPositionId());
        employee.setHireDate(lecturer.getHireDate());
    }

    private Lecturer hydrate(Lecturer lecturer) {
        employeeRepository.findByIdAndDeletedAtIsNull(lecturer.getId()).ifPresent(employee -> {
            lecturer.setUserId(employee.getUserId());
            lecturer.setFullName(employee.getFullName());
            lecturer.setGender(employee.getGender());
            lecturer.setDateOfBirth(employee.getDateOfBirth());
            lecturer.setEmail(employee.getEmail());
            lecturer.setPhone(employee.getPhone());
            lecturer.setDepartmentId(employee.getDepartmentId());
            lecturer.setPositionId(employee.getPositionId());
            lecturer.setHireDate(employee.getHireDate());
        });
        return lecturer;
    }
}
