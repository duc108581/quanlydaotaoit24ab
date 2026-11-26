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
        boolean duplicate = currentId == null
                ? repository.existsByCodeAndDeletedAtIsNull(department.getCode())
                : repository.existsByCodeAndDeletedAtIsNullAndIdNot(department.getCode(), currentId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã khoa đã tồn tại");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
