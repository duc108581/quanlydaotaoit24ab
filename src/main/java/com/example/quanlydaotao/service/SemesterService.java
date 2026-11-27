package com.example.quanlydaotao.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.entity.Semester;
import com.example.quanlydaotao.entity.SchoolYear;
import com.example.quanlydaotao.repository.SemesterRepository;
import com.example.quanlydaotao.repository.SchoolYearRepository;
import java.util.Locale;

@Service
public class SemesterService extends AbstractCrudService<Semester> {

    private final SchoolYearRepository schoolYearRepository;
    private final SemesterRepository semesterRepository;

    public SemesterService(SemesterRepository repository, SchoolYearRepository schoolYearRepository) {
        super(repository);
        this.semesterRepository = repository;
        this.schoolYearRepository = schoolYearRepository;
    }

    @Override
    public Semester create(Semester semester) {
        validate(semester, null);
        return super.create(semester);
    }

    @Override
    public Semester update(UUID id, Semester semester) {
        validate(semester, id);
        return super.update(id, semester);
    }

    private void validate(Semester semester, UUID currentId) {
        semester.setCode(clean(semester.getCode()));
        if (semester.getCode() != null) semester.setCode(semester.getCode().toUpperCase(Locale.ROOT));
        semester.setName(clean(semester.getName()));
        if (semester.getCode() == null || semester.getName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã và tên học kỳ là bắt buộc");
        }
        if (semester.getCode().length() > 100 || semester.getName().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin học kỳ vượt quá độ dài cho phép");
        }
        if (semester.getStartDate() != null && semester.getEndDate() != null
                && semester.getStartDate().isAfter(semester.getEndDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ngày bắt đầu học kỳ phải trước hoặc bằng ngày kết thúc");
        }
        if (semester.getSchoolYearId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn năm học cho học kỳ");
        }
        SchoolYear schoolYear = schoolYearRepository.findByIdAndDeletedAtIsNull(semester.getSchoolYearId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Năm học được chọn không tồn tại hoặc đã ngừng hoạt động"));
        if (Boolean.FALSE.equals(schoolYear.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể gắn học kỳ với năm học đã ngừng hoạt động");
        }
        boolean duplicate = currentId == null
                ? semesterRepository.existsBySchoolYearIdAndCode(semester.getSchoolYearId(), semester.getCode())
                : semesterRepository.existsBySchoolYearIdAndCodeAndIdNot(
                        semester.getSchoolYearId(), semester.getCode(), currentId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã học kỳ đã tồn tại trong năm học này");
        }
        if (schoolYear.getStartDate() != null && semester.getStartDate() != null
                && semester.getStartDate().isBefore(schoolYear.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Học kỳ không thể bắt đầu trước năm học");
        }
        if (schoolYear.getEndDate() != null && semester.getEndDate() != null
                && semester.getEndDate().isAfter(schoolYear.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Học kỳ không thể kết thúc sau năm học");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
