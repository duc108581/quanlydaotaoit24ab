package com.example.quanlydaotao.service;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.entity.SchoolYear;
import com.example.quanlydaotao.repository.SchoolYearRepository;
import java.util.Locale;
import java.util.UUID;

@Service
public class SchoolYearService extends AbstractCrudService<SchoolYear> {

    private final SchoolYearRepository repository;

    public SchoolYearService(SchoolYearRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    public SchoolYear create(SchoolYear entity) {
        validate(entity, null);
        return super.create(entity);
    }

    @Override
    public SchoolYear update(UUID id, SchoolYear entity) {
        validate(entity, id);
        return super.update(id, entity);
    }

    private void validate(SchoolYear year, UUID currentId) {
        year.setCode(clean(year.getCode()));
        if (year.getCode() != null) year.setCode(year.getCode().toUpperCase(Locale.ROOT));
        year.setName(clean(year.getName()));
        year.setDescription(clean(year.getDescription()));
        year.setNote(clean(year.getNote()));
        if (year.getCode() == null || year.getName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã và tên năm học là bắt buộc");
        }
        if (year.getCode().length() > 100 || year.getName().length() > 255
                || (year.getDescription() != null && year.getDescription().length() > 255)
                || (year.getNote() != null && year.getNote().length() > 255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin năm học vượt quá độ dài cho phép");
        }
        boolean duplicate = currentId == null
                ? repository.existsByCode(year.getCode())
                : repository.existsByCodeAndIdNot(year.getCode(), currentId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã năm học đã tồn tại");
        }
        if (year.getStartDate() != null && year.getEndDate() != null
                && year.getStartDate().isAfter(year.getEndDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ngày bắt đầu năm học phải trước hoặc bằng ngày kết thúc");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
