package com.example.quanlydaotao.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.quanlydaotao.entity.StudentClass;
import com.example.quanlydaotao.service.StudentClassService;

@RestController
@RequestMapping("/api/student-classes")
public class StudentClassController extends AbstractCrudController<StudentClass> {

    private final StudentClassService service;

    public StudentClassController(StudentClassService service) {
        super(service);
        this.service = service;
    }

    @PatchMapping("/{id}/status")
    public StudentClass setStatus(@PathVariable UUID id, @RequestBody(required = false) Map<String, Boolean> request) {
        Boolean active = request == null ? null : request.get("isActive");
        if (active == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái isActive là bắt buộc");
        }
        return service.setActive(id, active);
    }
}
