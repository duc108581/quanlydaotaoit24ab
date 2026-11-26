package com.example.quanlydaotao.entity;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "employees")
public class Employee extends AuditedEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "employee_code", length = 50, nullable = false, unique = true)
    private String employeeCode;

    @Column(name = "full_name", length = 255, nullable = false)
    private String fullName;

    @Column(name = "gender", length = 30)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public UUID getPositionId() { return positionId; }
    public void setPositionId(UUID positionId) { this.positionId = positionId; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
}
