package com.example.quanlydaotao.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_class_history")
public class StudentClassHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "student_class_id", nullable = false)
    private UUID studentClassId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_current", nullable = false)
    private Boolean current = true;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public UUID getStudentClassId() { return studentClassId; }
    public void setStudentClassId(UUID studentClassId) { this.studentClassId = studentClassId; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public Boolean getCurrent() { return current; }
    public void setCurrent(Boolean current) { this.current = current; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public UUID getChangedBy() { return changedBy; }
    public void setChangedBy(UUID changedBy) { this.changedBy = changedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
