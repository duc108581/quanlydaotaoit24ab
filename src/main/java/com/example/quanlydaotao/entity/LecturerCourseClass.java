
package com.example.quanlydaotao.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "lecturer_course_sections")
public class LecturerCourseClass extends AuditedEntity {

    @Column(name = "lecturer_id")
    private UUID lecturerId;

    @Column(name = "course_section_id", nullable = false)
    private UUID courseSectionId;

    @Column(name = "role", length = 50)
    private String role;

    public UUID getLecturerId() {
        return lecturerId;
    }

    public void setLecturerId(UUID lecturerId) {
        this.lecturerId = lecturerId;
    }

    public UUID getCourseSectionId() {
        return courseSectionId;
    }

    public void setCourseSectionId(UUID courseSectionId) {
        this.courseSectionId = courseSectionId;
    }

    @Transient
    public UUID getCourseClassId() {
        return courseSectionId;
    }

    public void setCourseClassId(UUID courseClassId) {
        this.courseSectionId = courseClassId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
