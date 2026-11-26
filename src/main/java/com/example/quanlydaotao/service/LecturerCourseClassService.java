package com.example.quanlydaotao.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.entity.LecturerCourseClass;
import com.example.quanlydaotao.repository.CourseSectionRepository;
import com.example.quanlydaotao.repository.LecturerRepository;
import com.example.quanlydaotao.repository.LecturerCourseClassRepository;

@Service
public class LecturerCourseClassService extends AbstractCrudService<LecturerCourseClass> {

    private final LecturerCourseClassRepository repository;
    private final LecturerRepository lecturerRepository;
    private final CourseSectionRepository courseSectionRepository;

    public LecturerCourseClassService(
            LecturerCourseClassRepository repository,
            LecturerRepository lecturerRepository,
            CourseSectionRepository courseSectionRepository) {
        super(repository);
        this.repository = repository;
        this.lecturerRepository = lecturerRepository;
        this.courseSectionRepository = courseSectionRepository;
    }

    @Override
    @Transactional
    public LecturerCourseClass create(LecturerCourseClass assignment) {
        validate(assignment, null);
        assignment.setIsActive(true);
        return super.create(assignment);
    }

    @Override
    @Transactional
    public LecturerCourseClass update(UUID id, LecturerCourseClass assignment) {
        LecturerCourseClass current = get(id);
        if (!current.getLecturerId().equals(assignment.getLecturerId())
                || !current.getCourseSectionId().equals(assignment.getCourseSectionId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể sửa giảng viên hoặc lớp của bản ghi phân công; hãy kết thúc phân công cũ rồi tạo bản ghi mới");
        }
        validate(assignment, id);
        return super.update(id, assignment);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        LecturerCourseClass assignment = get(id);
        courseSectionRepository.findByIdAndDeletedAtIsNull(assignment.getCourseSectionId())
                .filter(section -> !"OPEN".equalsIgnoreCase(section.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Đóng lớp học phần trước khi kết thúc phân công giảng viên"));
        super.delete(id);
    }

    private void validate(LecturerCourseClass assignment, UUID currentId) {
        if (assignment.getLecturerId() == null || assignment.getCourseSectionId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cần chọn giảng viên và lớp học phần");
        }
        lecturerRepository.findByIdAndDeletedAtIsNull(assignment.getLecturerId())
                .filter(lecturer -> !Boolean.FALSE.equals(lecturer.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Giảng viên không tồn tại hoặc đã ngừng hoạt động"));
        courseSectionRepository.findByIdAndDeletedAtIsNull(assignment.getCourseSectionId())
                .filter(section -> !Boolean.FALSE.equals(section.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Lớp học phần không tồn tại hoặc đã ngừng hoạt động"));
        boolean duplicate = currentId == null
                ? repository.existsByCourseSectionIdAndIsActiveTrueAndDeletedAtIsNull(
                        assignment.getCourseSectionId())
                : repository.existsByCourseSectionIdAndIsActiveTrueAndDeletedAtIsNullAndIdNot(
                        assignment.getCourseSectionId(), currentId);
        if (duplicate) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Lớp học phần đã có giảng viên đang được phân công");
        }
    }
}
