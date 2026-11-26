package com.example.quanlydaotao.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_registrations")
public class StudentCourseSection extends AuditedEntity {

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "course_section_id")
    private UUID courseSectionId;

    @Column(name = "registration_date")
    private LocalDateTime registrationDate;

    @Column(name = "registration_type", length = 30, nullable = false)
    private String registrationType;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Column(name = "is_active_registration", nullable = false)
    private Boolean activeRegistration = true;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "note", length = 500)
    private String note;

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public UUID getCourseSectionId() { return courseSectionId; }
    public void setCourseSectionId(UUID courseSectionId) { this.courseSectionId = courseSectionId; }
    public LocalDateTime getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(LocalDateTime registrationDate) { this.registrationDate = registrationDate; }
    public String getRegistrationType() { return registrationType; }
    public void setRegistrationType(String registrationType) { this.registrationType = registrationType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public Boolean getActiveRegistration() { return activeRegistration; }
    public void setActiveRegistration(Boolean activeRegistration) { this.activeRegistration = activeRegistration; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
