package com.example.quanlydaotao.repository;

import java.util.UUID;

import com.example.quanlydaotao.entity.Employee;

public interface EmployeeRepository extends SoftDeleteRepository<Employee> {

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmailAndDeletedAtIsNullAndIdNot(String email, UUID id);
}
