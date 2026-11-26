package com.example.quanlydaotao.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_sections")
public class CourseSection extends AuditedEntity {

    @Column(name = "code", length = 100, nullable = false)
    private String code;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "semester_id", nullable = false)
    private UUID semesterId;

    @Column(name = "grading_scale_id")
    private UUID gradingScaleId;

    @Column(name = "max_students")
    private Integer maxStudents;

    @Column(name = "min_students")
    private Integer minStudents;

    @Column(name = "class_type", length = 100)
    private String classType;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "registration_start")
    private LocalDateTime registrationStart;

    @Column(name = "registration_end")
    private LocalDateTime registrationEnd;

    @Column(name = "note", length = 500)
    private String note;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public UUID getSemesterId() { return semesterId; }
    public void setSemesterId(UUID semesterId) { this.semesterId = semesterId; }
    public UUID getGradingScaleId() { return gradingScaleId; }
    public void setGradingScaleId(UUID gradingScaleId) { this.gradingScaleId = gradingScaleId; }
    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
    public Integer getMinStudents() { return minStudents; }
    public void setMinStudents(Integer minStudents) { this.minStudents = minStudents; }
    public String getClassType() { return classType; }
    public void setClassType(String classType) { this.classType = classType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getRegistrationStart() { return registrationStart; }
    public void setRegistrationStart(LocalDateTime registrationStart) { this.registrationStart = registrationStart; }
    public LocalDateTime getRegistrationEnd() { return registrationEnd; }
    public void setRegistrationEnd(LocalDateTime registrationEnd) { this.registrationEnd = registrationEnd; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
