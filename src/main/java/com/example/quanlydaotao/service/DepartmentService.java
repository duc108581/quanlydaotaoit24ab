package com.example.quanlydaotao.service;

import java.util.UUID;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.entity.Department;
import com.example.quanlydaotao.repository.DepartmentRepository;

@Service
public class DepartmentService extends AbstractCrudService<Department> {

    private final DepartmentRepository repository;

    public DepartmentService(DepartmentRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    public Department create(Department department) {
        validate(department, null);
        return super.create(department);
    }

    @Override
    public Department update(UUID id, Department department) {
        validate(department, id);
        return super.update(id, department);
    }

    private void validate(Department department, UUID currentId) {
        department.setCode(clean(department.getCode()));
        if (department.getCode() != null) department.setCode(department.getCode().toUpperCase(Locale.ROOT));
        department.setName(clean(department.getName()));
        if (department.getCode() == null || department.getName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã và tên khoa là bắt buộc");
        }
        if (department.getCode().length() > 100 || department.getName().length() > 255
                || (department.getDescription() != null && department.getDescription().length() > 500)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin khoa vượt quá độ dài cho phép");
        }
        if (department.getEstablishedYear() != null && department.getEstablishedYear() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Năm thành lập phải là năm dương");
        }
        boolean duplicate = currentId == null
                ? repository.existsByCode(department.getCode())
                : repository.existsByCodeAndIdNot(department.getCode(), currentId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã khoa đã tồn tại");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
