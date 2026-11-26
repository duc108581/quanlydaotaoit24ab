package com.example.quanlydaotao.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.entity.CourseSection;
import com.example.quanlydaotao.entity.Student;
import com.example.quanlydaotao.entity.StudentCourseSection;
import com.example.quanlydaotao.repository.CourseSectionRepository;
import com.example.quanlydaotao.repository.StudentRepository;
import com.example.quanlydaotao.repository.StudentCourseSectionRepository;

@Service
public class StudentCourseSectionService extends AbstractCrudService<StudentCourseSection> {

    private final StudentCourseSectionRepository registrationRepository;
    private final StudentRepository studentRepository;
    private final CourseSectionRepository courseSectionRepository;

    public StudentCourseSectionService(
            StudentCourseSectionRepository repository,
            StudentRepository studentRepository,
            CourseSectionRepository courseSectionRepository) {
        super(repository);
        this.registrationRepository = repository;
        this.studentRepository = studentRepository;
        this.courseSectionRepository = courseSectionRepository;
    }

    @Override
    @Transactional
    public StudentCourseSection create(StudentCourseSection registration) {
        if (registration.getStatus() == null || registration.getStatus().isBlank()) {
            registration.setStatus("REGISTERED");
        }
        String requestedStatus = registration.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!"REGISTERED".equals(requestedStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Đăng ký mới phải bắt đầu ở trạng thái REGISTERED");
        }
        if (registration.getStudentId() != null && registration.getCourseSectionId() != null) {
            StudentCourseSection cancelled = registrationRepository
                    .findFirstByStudentIdAndCourseSectionIdAndStatusAndDeletedAtIsNullOrderByRegistrationDateDesc(
                            registration.getStudentId(), registration.getCourseSectionId(), "CANCELLED")
                    .orElseGet(() -> registrationRepository
                            .findFirstByStudentIdAndCourseSectionIdAndStatusAndDeletedAtIsNullOrderByRegistrationDateDesc(
                                    registration.getStudentId(), registration.getCourseSectionId(), "DROPPED")
                            .orElse(null));
            if (cancelled != null) {
                registration.setId(cancelled.getId());
                registration.setAttemptNumber(cancelled.getAttemptNumber());
                registration.setRegistrationType(cancelled.getRegistrationType());
                registration.setCreatedAt(cancelled.getCreatedAt());
                registration.setCreatedBy(cancelled.getCreatedBy());
                registration.setReviewedBy(null);
                registration.setReviewedAt(null);
                registration.setStatus("REGISTERED");
                registration.setActiveRegistration(true);
                return save(registration, cancelled.getId());
            }
        }
        return save(registration, null);
    }

    @Override
    @Transactional
    public StudentCourseSection update(UUID id, StudentCourseSection registration) {
        return save(registration, id);
    }

    private StudentCourseSection save(StudentCourseSection registration, UUID currentId) {
        if (registration.getStudentId() == null || registration.getCourseSectionId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cần chọn sinh viên và lớp học phần");
        }
        Student student = studentRepository.findById(registration.getStudentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sinh viên không tồn tại"));
        if (student.getDeletedAt() != null || Boolean.FALSE.equals(student.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sinh viên đã ngừng hoạt động");
        }
        CourseSection section = courseSectionRepository.findByIdForUpdate(registration.getCourseSectionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Lớp học phần không tồn tại hoặc đã bị xóa"));
        String status = registration.getStatus() == null ? "REGISTERED"
                : registration.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of(
                "REGISTERED", "CONFIRMED", "STUDYING", "COMPLETED", "CANCELLED", "DROPPED")
                .contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái đăng ký không hợp lệ");
        }
        registration.setStatus(status);
        boolean cancelled = "CANCELLED".equals(status) || "DROPPED".equals(status);
        if (!cancelled && (Boolean.FALSE.equals(section.getIsActive())
                || !"OPEN".equalsIgnoreCase(section.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lớp học phần hiện không mở đăng ký");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!cancelled && ((section.getRegistrationStart() != null && now.isBefore(section.getRegistrationStart()))
                || (section.getRegistrationEnd() != null && now.isAfter(section.getRegistrationEnd())))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đã ngoài thời gian đăng ký lớp học phần");
        }

        if (!cancelled) {
            if (registrationRepository.existsActiveRegistration(
                    registration.getStudentId(), registration.getCourseSectionId(), currentId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Sinh viên đã đăng ký lớp học phần này");
            }
            if (section.getMaxStudents() != null
                    && registrationRepository.countActiveByCourseSectionId(section.getId()) >= section.getMaxStudents()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Lớp học phần đã đủ sức chứa");
            }
            registration.setActiveRegistration(true);
        } else {
            registration.setActiveRegistration(false);
        }
        if (registration.getRegistrationDate() == null) {
            registration.setRegistrationDate(now);
        }
        if (registration.getAttemptNumber() == null || registration.getAttemptNumber() < 1) {
            registration.setAttemptNumber(1);
        }
        if (registration.getRegistrationType() == null || registration.getRegistrationType().isBlank()) {
            registration.setRegistrationType("FIRST");
        } else {
            registration.setRegistrationType(registration.getRegistrationType().trim().toUpperCase(Locale.ROOT));
        }
        return currentId == null ? super.create(registration) : super.update(currentId, registration);
    }
}
