package com.example.quanlydaotao.service;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.quanlydaotao.entity.AcademicYear;
import com.example.quanlydaotao.repository.AcademicYearRepository;
import java.util.Locale;
import java.util.UUID;

@Service
public class AcademicYearService extends AbstractCrudService<AcademicYear> {

    private final AcademicYearRepository repository;

    public AcademicYearService(AcademicYearRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    public AcademicYear create(AcademicYear entity) {
        validate(entity, null);
        return super.create(entity);
    }

    @Override
    public AcademicYear update(UUID id, AcademicYear entity) {
        validate(entity, id);
        return super.update(id, entity);
    }

    private void validate(AcademicYear year, UUID currentId) {
        year.setCode(clean(year.getCode()));
        if (year.getCode() != null) year.setCode(year.getCode().toUpperCase(Locale.ROOT));
        year.setName(clean(year.getName()));
        year.setYear(clean(year.getYear()));
        year.setDescription(clean(year.getDescription()));
        if (year.getCode() == null || year.getName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã và tên niên khóa là bắt buộc");
        }
        if (year.getCode().length() > 100 || year.getName().length() > 100
                || (year.getYear() != null && year.getYear().length() > 20)
                || (year.getDescription() != null && year.getDescription().length() > 255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin niên khóa vượt quá độ dài cho phép");
        }
        boolean duplicate = currentId == null
                ? repository.existsByCode(year.getCode())
                : repository.existsByCodeAndIdNot(year.getCode(), currentId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã niên khóa đã tồn tại");
        }
        if (year.getStartDate() != null && year.getEndDate() != null
                && year.getStartDate().isAfter(year.getEndDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ngày bắt đầu niên khóa phải trước hoặc bằng ngày kết thúc");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
