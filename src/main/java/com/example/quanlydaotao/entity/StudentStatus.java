package com.example.quanlydaotao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_statuses")
public class StudentStatus extends AuditedEntity {

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
