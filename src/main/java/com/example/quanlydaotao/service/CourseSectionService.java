package com.example.quanlydaotao.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.entity.CourseSection;
import com.example.quanlydaotao.repository.CourseRepository;
import com.example.quanlydaotao.repository.CourseSectionRepository;
import com.example.quanlydaotao.repository.LecturerCourseClassRepository;
import com.example.quanlydaotao.repository.SemesterRepository;

@Service
public class CourseSectionService extends AbstractCrudService<CourseSection> {

    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;
    private final LecturerCourseClassRepository lecturerAssignments;
    private final JdbcTemplate jdbcTemplate;

    public CourseSectionService(
            CourseSectionRepository repository,
            SemesterRepository semesterRepository,
            CourseRepository courseRepository,
            LecturerCourseClassRepository lecturerAssignments,
            JdbcTemplate jdbcTemplate) {
        super(repository);
        this.semesterRepository = semesterRepository;
        this.courseRepository = courseRepository;
        this.lecturerAssignments = lecturerAssignments;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public CourseSection create(CourseSection section) {
        validate(section);
        return super.create(section);
    }

    @Override
    @Transactional
    public CourseSection update(UUID id, CourseSection section) {
        section.setId(id);
        validate(section);
        return super.update(id, section);
    }

    private void validate(CourseSection section) {
        if (section.getStatus() == null || section.getStatus().isBlank()) {
            section.setStatus("DRAFT");
        } else {
            section.setStatus(section.getStatus().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (!java.util.Set.of("DRAFT", "OPEN", "CLOSED", "COMPLETED", "CANCELLED")
                .contains(section.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái lớp học phần không hợp lệ");
        }
        if (section.getSemesterId() == null
                || semesterRepository.findByIdAndDeletedAtIsNull(section.getSemesterId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn học kỳ còn hiệu lực");
        }
        if (section.getCourseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn học phần");
        }
        courseRepository.findByIdAndDeletedAtIsNull(section.getCourseId())
                .filter(course -> !Boolean.FALSE.equals(course.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Học phần không tồn tại hoặc đã ngừng hoạt động"));
        if (section.getGradingScaleId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Vui lòng chọn thang điểm cho lớp học phần");
        }
        if ("OPEN".equals(section.getStatus())) {
            if (section.getId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Cần lưu lớp học phần và phân công giảng viên trước khi mở lớp");
            }
            if (!lecturerAssignments.existsByCourseSectionIdAndIsActiveTrueAndDeletedAtIsNull(section.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Không thể mở lớp khi chưa phân công giảng viên phụ trách");
            }
            Integer activeScaleCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM grading_scales
                    WHERE id = ?
                      AND scale_status = N'ACTIVE'
                    """, Integer.class, section.getGradingScaleId());
            if (activeScaleCount == null || activeScaleCount != 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Không thể mở lớp khi thang điểm chưa hoạt động");
            }
        }
        if (section.getMinStudents() != null && section.getMaxStudents() != null
                && section.getMinStudents() > section.getMaxStudents()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Sĩ số tối thiểu không được lớn hơn sĩ số tối đa");
        }
        if (section.getMaxStudents() != null && section.getMaxStudents() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sĩ số tối đa phải lớn hơn 0");
        }
        if (section.getMinStudents() != null && section.getMinStudents() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sĩ số tối thiểu không được âm");
        }
        if (section.getRegistrationStart() != null && section.getRegistrationEnd() != null
                && section.getRegistrationStart().isAfter(section.getRegistrationEnd())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Thời gian bắt đầu đăng ký phải trước hoặc bằng thời gian kết thúc");
        }
    }
}
