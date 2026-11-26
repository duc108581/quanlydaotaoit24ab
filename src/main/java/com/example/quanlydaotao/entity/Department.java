package com.example.quanlydaotao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "departments")
public class Department extends AuditedEntity {

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "established_year")
    private Short establishedYear;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Short getEstablishedYear() { return establishedYear; }
    public void setEstablishedYear(Short establishedYear) { this.establishedYear = establishedYear; }
}
