package com.example.quanlydaotao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.quanlydaotao.entity.Course;
import com.example.quanlydaotao.entity.CourseSection;
import com.example.quanlydaotao.entity.StudentStatus;
import com.example.quanlydaotao.repository.CourseRepository;
import com.example.quanlydaotao.repository.CourseSectionRepository;
import com.example.quanlydaotao.repository.StudentStatusRepository;
import com.example.quanlydaotao.repository.StudentStatusHistoryRepository;
import com.example.quanlydaotao.service.JwtTokenService;
import com.example.quanlydaotao.service.JwtTokenService.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AcademicManagementApiTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseSectionRepository courseSectionRepository;

    @Autowired
    private StudentStatusRepository studentStatusRepository;

    @Autowired
    private StudentStatusHistoryRepository studentStatusHistoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, String> studentAccountIds = new HashMap<>();
    private String adminToken;

    @BeforeEach
    void createAdminAccessToken() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id UUID PRIMARY KEY,
                    username VARCHAR(100) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    display_name VARCHAR(255),
                    email VARCHAR(254),
                    is_active INTEGER NOT NULL,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS roles (
                    id UUID PRIMARY KEY,
                    code VARCHAR(50) NOT NULL UNIQUE,
                    is_active INTEGER NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_roles (
                    id UUID PRIMARY KEY,
                    user_id UUID NOT NULL,
                    role_id UUID NOT NULL,
                    revoked_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS grading_scales (
                    id UUID PRIMARY KEY,
                    code VARCHAR(100) NOT NULL,
                    version_no SMALLINT NOT NULL,
                    name VARCHAR(200) NOT NULL,
                    minimum_score DECIMAL(5, 2) NOT NULL,
                    maximum_score DECIMAL(5, 2) NOT NULL,
                    rounding_scale TINYINT NOT NULL,
                    rounding_mode VARCHAR(40) NOT NULL,
                    scale_status VARCHAR(40) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    created_by UUID,
                    activated_at TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                MERGE INTO roles (id, code, is_active)
                KEY (code)
                VALUES (?, 'STUDENT', 1)
                """, UUID.nameUUIDFromBytes("STUDENT".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbcTemplate.update("""
                MERGE INTO roles (id, code, is_active)
                KEY (code)
                VALUES (?, 'ADMIN', 1)
                """, UUID.nameUUIDFromBytes("ADMIN".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbcTemplate.update("""
                MERGE INTO roles (id, code, is_active)
                KEY (code)
                VALUES (?, 'LECTURER', 1)
                """, UUID.nameUUIDFromBytes("LECTURER".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        adminToken = jwtTokenService.issueAccessToken(new AuthenticatedUser(
                UUID.randomUUID().toString(), "test-admin", "Test Admin", List.of("ADMIN"), null));
    }

    @Test
    void staticPagesAndApiDocumentationAreReachable() throws Exception {
        for (String path : List.of(
                "/",
                "/academic.html",
                "/assets/js/student.js",
                "/assets/js/academic.js",
                "/api/student-form-options/classes",
                "/api/student-form-options/departments",
                "/api/student-form-options/majors",
                "/api/student-form-options/training-programs",
                "/api/student-form-options/statuses",
                "/api/academic-form-options/courses",
                "/api/academic-form-options/rooms",
                "/api/academic-form-options/buildings",
                "/api/academic-form-options/grading-scales",
                "/v3/api-docs")) {
            HttpResponse<String> response = send("http://localhost:" + port + path, "GET", null);
            assertEquals(200, response.statusCode(), () -> "GET " + path + " failed: " + response.body());
        }
    }

    @Test
    void catalogCrudValidatesRelationshipsAndUsesSmallintYearValues() throws Exception {
        String suffix = UUID.randomUUID().toString();
        List<String> cleanupUrls = new ArrayList<>();
        String firstDepartmentId = null;
        String secondDepartmentId = null;
        String firstMajorId = null;
        String secondMajorId = null;
        String schoolYearId = null;
        String secondSchoolYearId = null;
        try {
            String departmentsUrl = "http://localhost:" + port + "/api/departments";
            assertEquals(401, sendWithoutAuthorization(departmentsUrl, "GET", null).statusCode());
            String studentToken = jwtTokenService.issueAccessToken(new AuthenticatedUser(
                    UUID.randomUUID().toString(), "test-student", "Test Student", List.of("STUDENT"), null));
            assertEquals(403, sendWithToken(departmentsUrl, "GET", null, studentToken).statusCode());

            firstDepartmentId = create("/api/departments", """
                    {"code":"DEP-A-%s","name":"First department","establishedYear":1998}
                    """.formatted(suffix), cleanupUrls);
            JsonNode department = objectMapper.readTree(send(
                    departmentsUrl + "/" + firstDepartmentId, "GET", null).body());
            assertEquals(1998, department.path("establishedYear").asInt());
            secondDepartmentId = create("/api/departments", """
                    {"code":"DEP-B-%s","name":"Second department"}
                    """.formatted(suffix), cleanupUrls);

            firstMajorId = create("/api/majors", """
                    {"code":"MAJ-%s","name":"First major","departmentId":"%s"}
                    """.formatted(suffix, firstDepartmentId), cleanupUrls);
            HttpResponse<String> duplicateMajor = send(
                    departmentsUrl.replace("/departments", "/majors"), "POST", """
                    {"code":"MAJ-%s","name":"Duplicate major","departmentId":"%s"}
                    """.formatted(suffix, firstDepartmentId));
            assertEquals(409, duplicateMajor.statusCode(), duplicateMajor.body());
            secondMajorId = create("/api/majors", """
                    {"code":"MAJ-%s","name":"Major in another department","departmentId":"%s"}
                    """.formatted(suffix, secondDepartmentId), cleanupUrls);

            HttpResponse<String> mismatchedProgram = send(
                    "http://localhost:" + port + "/api/training-programs", "POST", """
                    {"code":"TP-BAD-%s","name":"Mismatched program","departmentId":"%s","majorId":"%s"}
                    """.formatted(suffix, firstDepartmentId, secondMajorId));
            assertEquals(400, mismatchedProgram.statusCode(), mismatchedProgram.body());
            String programId = create("/api/training-programs", """
                    {"code":"TP-%s","name":"First program","departmentId":"%s","majorId":"%s","admissionYear":2024}
                    """.formatted(suffix, firstDepartmentId, firstMajorId), cleanupUrls);
            JsonNode program = objectMapper.readTree(send(
                    "http://localhost:" + port + "/api/training-programs/" + programId, "GET", null).body());
            assertEquals(2024, program.path("admissionYear").asInt());

            schoolYearId = create("/api/school-years", """
                    {"code":"SY-%s","name":"2025-2026","startDate":"2025-08-01","endDate":"2026-07-31"}
                    """.formatted(suffix), cleanupUrls);
            HttpResponse<String> invalidSemester = send(
                    "http://localhost:" + port + "/api/semesters", "POST", """
                    {"code":"SEM-BAD-%s","name":"Out of range","schoolYearId":"%s","startDate":"2025-07-31","endDate":"2025-12-31"}
                    """.formatted(suffix, schoolYearId));
            assertEquals(400, invalidSemester.statusCode(), invalidSemester.body());
            String semesterId = create("/api/semesters", """
                    {"code":"SEM-%s","name":"First semester","schoolYearId":"%s","startDate":"2025-08-01","endDate":"2025-12-31"}
                    """.formatted(suffix, schoolYearId), cleanupUrls);
            JsonNode semester = objectMapper.readTree(send(
                    "http://localhost:" + port + "/api/semesters/" + semesterId, "GET", null).body());
            assertEquals(schoolYearId, semester.path("schoolYearId").asText());
            HttpResponse<String> duplicateSemester = send(
                    "http://localhost:" + port + "/api/semesters", "POST", """
                    {"code":"SEM-%s","name":"Duplicate semester","schoolYearId":"%s"}
                    """.formatted(suffix, schoolYearId));
            assertEquals(409, duplicateSemester.statusCode(), duplicateSemester.body());
            secondSchoolYearId = create("/api/school-years", """
                    {"code":"SY2-%s","name":"2026-2027"}
                    """.formatted(suffix), cleanupUrls);
            create("/api/semesters", """
                    {"code":"SEM-%s","name":"Same code in another year","schoolYearId":"%s"}
                    """.formatted(suffix, secondSchoolYearId), cleanupUrls);
        } finally {
            for (int i = cleanupUrls.size() - 1; i >= 0; i--) {
                send(cleanupUrls.get(i), "DELETE", null);
            }
        }
    }

    @Test
    void studentClassCrudEnforcesSchemaAndPreservesCurrentClassHistory() throws Exception {
        String suffix = UUID.randomUUID().toString();
        List<String> cleanupUrls = new ArrayList<>();
        String studentId = null;
        UUID studentUserId = null;
        try {
            String academicYearId = create("/api/academic-years", """
                    {"code":"AY-CLASS-%s","name":"Class cohort"}
                    """.formatted(suffix), cleanupUrls);
            String departmentId = create("/api/departments", """
                    {"code":"DEP-CLASS-%s","name":"Class department"}
                    """.formatted(suffix), cleanupUrls);
            String majorId = create("/api/majors", """
                    {"code":"MAJ-CLASS-%s","name":"Class major","departmentId":"%s"}
                    """.formatted(suffix, departmentId), cleanupUrls);
            String trainingProgramId = create("/api/training-programs", """
                    {"code":"TP-CLASS-%s","name":"Class program","departmentId":"%s","majorId":"%s"}
                    """.formatted(suffix, departmentId, majorId), cleanupUrls);

            String longClassCode = "CLASS-" + suffix.replace("-", "") + "-LONG-CODE-OVER-FIFTY";
            String classPayload = """
                    {"code":"%s","name":"Class one","academicYearId":"%s","departmentId":"%s","majorId":"%s","trainingProgramId":"%s"}
                    """.formatted(longClassCode, academicYearId, departmentId, majorId, trainingProgramId);
            String firstClassId = create("/api/student-classes", classPayload, cleanupUrls);
            String secondClassId = create("/api/student-classes", """
                    {"code":"CLASS-B-%s","name":"Class two","academicYearId":"%s","departmentId":"%s","majorId":"%s","trainingProgramId":"%s"}
                    """.formatted(suffix, academicYearId, departmentId, majorId, trainingProgramId), cleanupUrls);

            JsonNode detail = objectMapper.readTree(send(
                    "http://localhost:" + port + "/api/student-classes/" + firstClassId, "GET", null).body());
            assertEquals(longClassCode.toUpperCase(java.util.Locale.ROOT), detail.path("code").asText());
            HttpResponse<String> updateClass = send(
                    "http://localhost:" + port + "/api/student-classes/" + firstClassId, "PUT", """
                    {"code":"%s","name":"Updated class name","academicYearId":"%s","departmentId":"%s","majorId":"%s","trainingProgramId":"%s","isActive":true}
                    """.formatted(longClassCode, academicYearId, departmentId, majorId, trainingProgramId));
            assertEquals(200, updateClass.statusCode(), updateClass.body());
            assertEquals("Updated class name", objectMapper.readTree(send(
                    "http://localhost:" + port + "/api/student-classes/" + firstClassId, "GET", null).body())
                    .path("name").asText());
            assertEquals(409, send("http://localhost:" + port + "/api/student-classes", "POST",
                    classPayload.replace(longClassCode, longClassCode.toLowerCase(java.util.Locale.ROOT))).statusCode());

            HttpResponse<String> deactivate = send(
                    "http://localhost:" + port + "/api/student-classes/" + firstClassId + "/status",
                    "PATCH", "{\"isActive\":false}");
            assertEquals(200, deactivate.statusCode(), deactivate.body());
            assertFalse(objectMapper.readTree(deactivate.body()).path("isActive").asBoolean());
            assertFalse(containsId(send(
                    "http://localhost:" + port + "/api/student-form-options/classes", "GET", null).body(), firstClassId));
            assertTrue(containsId(send(
                    "http://localhost:" + port + "/api/student-form-options/classes?includeInactive=true",
                    "GET", null).body(), firstClassId));
            HttpResponse<String> rejectedStudent = send("http://localhost:" + port + "/api/students", "POST", """
                    {"code":"SV%d","fullName":"Inactive Class Student","studentClassessId":"%s","initialPassword":"initial-password-123"}
                    """.formatted(100_000_000 + Math.floorMod(suffix.hashCode(), 899_999_999), firstClassId));
            assertEquals(400, rejectedStudent.statusCode(), rejectedStudent.body());

            HttpResponse<String> reactivate = send(
                    "http://localhost:" + port + "/api/student-classes/" + firstClassId + "/status",
                    "PATCH", "{\"isActive\":true}");
            assertEquals(200, reactivate.statusCode(), reactivate.body());

            HttpResponse<String> createdStudent = send("http://localhost:" + port + "/api/students", "POST", """
                    {"code":"SV%d","fullName":"Class History Student","studentClassessId":"%s","initialPassword":"initial-password-123"}
                    """.formatted(200_000_000 + Math.floorMod(suffix.hashCode(), 799_999_999), firstClassId));
            assertEquals(200, createdStudent.statusCode(), createdStudent.body());
            JsonNode student = objectMapper.readTree(createdStudent.body());
            studentId = student.path("id").asText();
            studentUserId = UUID.fromString(student.path("userId").asText());

            HttpResponse<String> transfer = send(
                    "http://localhost:" + port + "/api/students/" + studentId, "PUT",
                    "{\"studentClassessId\":\"" + secondClassId + "\"}");
            assertEquals(200, transfer.statusCode(), transfer.body());
            assertEquals(2, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM student_class_history WHERE student_id = ?",
                    Integer.class, UUID.fromString(studentId)));
            assertEquals(UUID.fromString(secondClassId), jdbcTemplate.queryForObject(
                    "SELECT student_class_id FROM student_class_history WHERE student_id = ? AND is_current = 1",
                    UUID.class, UUID.fromString(studentId)));
            assertEquals(409, send(
                    "http://localhost:" + port + "/api/student-classes/" + firstClassId, "DELETE", null).statusCode());
        } finally {
            if (studentId != null) {
                UUID id = UUID.fromString(studentId);
                jdbcTemplate.update("DELETE FROM student_class_history WHERE student_id = ?", id);
                jdbcTemplate.update("DELETE FROM student_status_history WHERE student_id = ?", id);
                jdbcTemplate.update("DELETE FROM students WHERE id = ?", id);
            }
            if (studentUserId != null) {
                jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", studentUserId);
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", studentUserId);
            }
            for (int i = cleanupUrls.size() - 1; i >= 0; i--) {
                send(cleanupUrls.get(i), "DELETE", null);
            }
        }
    }

    @Test
    void allAcademicResourcesSupportCrudAndSoftDelete() throws Exception {
        String suffix = UUID.randomUUID().toString();
        List<String> cleanupUrls = new ArrayList<>();
        String academicYearId = null;
        String departmentId = null;
        String majorId = null;
        String trainingProgramId = null;
        String studentClassId = null;
        String lecturerId = null;
        String courseId = null;
        String schoolYearId = null;
        String semesterId = null;
        String courseSectionId = null;
        String studentId = null;
        String templateStatusId = null;
        String assignedStatusId = null;

        try {
            academicYearId = create("/api/academic-years", """
                    {"code":"AY-SEED-%s","name":"Seed academic year","year":"2025-2029"}
                    """.formatted(suffix), cleanupUrls);
            departmentId = create("/api/departments", """
                    {"code":"DEP-SEED-%s","name":"Seed department"}
                    """.formatted(suffix), cleanupUrls);
            majorId = create("/api/majors", """
                    {"code":"MAJ-SEED-%s","name":"Seed major","departmentId":"%s"}
                    """.formatted(suffix, departmentId), cleanupUrls);
            trainingProgramId = create("/api/training-programs", """
                    {"code":"TP-SEED-%s","name":"Seed training program","departmentId":"%s","majorId":"%s"}
                    """.formatted(suffix, departmentId, majorId), cleanupUrls);
            studentClassId = create("/api/student-classes", """
                    {"code":"CLASS-SEED-%s","name":"Seed student class","academicYearId":"%s","departmentId":"%s","majorId":"%s","trainingProgramId":"%s"}
                    """.formatted(suffix, academicYearId, departmentId, majorId, trainingProgramId), cleanupUrls);
            String lecturerEmail = "lecturer-" + suffix + "@example.test";
            lecturerId = create("/api/lecturers", """
                    {"lecturerCode":"GV-SEED-%s","fullName":"Seed Lecturer","email":"%s","departmentId":"%s","initialPassword":"initial-password-123"}
                    """.formatted(suffix, lecturerEmail, departmentId), cleanupUrls);
            UUID lecturerUserId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM employees WHERE id = ?", UUID.class, UUID.fromString(lecturerId));
            assertEquals(lecturerEmail, jdbcTemplate.queryForObject(
                    "SELECT email FROM users WHERE id = ?", String.class, lecturerUserId));
            assertEquals(lecturerEmail, jdbcTemplate.queryForObject(
                    "SELECT email FROM employees WHERE id = ?", String.class, UUID.fromString(lecturerId)));
            assertEquals(1, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM user_roles assignedRole
                    JOIN roles assigned ON assigned.id = assignedRole.role_id
                    WHERE assignedRole.user_id = ?
                      AND assignedRole.revoked_at IS NULL
                      AND assigned.code = 'LECTURER'
                    """, Integer.class, lecturerUserId));
            String updatedLecturerEmail = "updated-lecturer-" + suffix + "@example.test";
            HttpResponse<String> lecturerEmailUpdate = send(
                    "http://localhost:" + port + "/api/lecturers/" + lecturerId, "PUT", """
                    {"lecturerCode":"GV-SEED-%s","fullName":"Seed Lecturer","email":"%s","departmentId":"%s","isActive":true}
                    """.formatted(suffix, updatedLecturerEmail, departmentId));
            assertEquals(200, lecturerEmailUpdate.statusCode(), lecturerEmailUpdate.body());
            assertEquals(updatedLecturerEmail, jdbcTemplate.queryForObject(
                    "SELECT email FROM users WHERE id = ?", String.class, lecturerUserId));
            assertEquals(updatedLecturerEmail, jdbcTemplate.queryForObject(
                    "SELECT email FROM employees WHERE id = ?", String.class, UUID.fromString(lecturerId)));

            Course course = new Course();
            course.setCode("COURSE-SEED-" + suffix);
            course.setName("Seed course");
            course.setCredits(new java.math.BigDecimal("3.00"));
            courseId = courseRepository.save(course).getId().toString();

            schoolYearId = create("/api/school-years", """
                    {"code":"SY-%s","name":"2025-2026","startDate":"2025-08-01","endDate":"2026-07-31"}
                    """.formatted(suffix), cleanupUrls);
            semesterId = create("/api/semesters", """
                    {"code":"SEED-%s","name":"Seed semester","schoolYearId":"%s","startDate":"2025-08-01","endDate":"2025-12-31"}
                    """.formatted(suffix, schoolYearId), cleanupUrls);
            UUID gradingScaleId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO grading_scales
                        (id, code, version_no, name, minimum_score, maximum_score, rounding_scale,
                         rounding_mode, scale_status, created_at)
                    VALUES (?, 'TEST', 1, 'Test scale', 0, 10, 2, 'HALF_UP', 'ACTIVE', CURRENT_TIMESTAMP)
                    """, gradingScaleId);
            courseSectionId = create("/api/course-sections", """
                    {"code":"SEED-%s","name":"Seed section","courseId":"%s","semesterId":"%s","gradingScaleId":"%s","status":"DRAFT"}
                    """.formatted(suffix, courseId, semesterId, gradingScaleId), cleanupUrls);
            String assignmentId = create("/api/lecturer-course-classes", """
                    {"lecturerId":"%s","courseSectionId":"%s","role":"PRIMARY"}
                    """.formatted(lecturerId, courseSectionId), cleanupUrls);
            HttpResponse<String> openSection = send(
                    "http://localhost:" + port + "/api/course-sections/" + courseSectionId, "PUT", """
                    {"code":"SEED-%s","name":"Seed section","courseId":"%s","semesterId":"%s","gradingScaleId":"%s","status":"OPEN"}
                    """.formatted(suffix, courseId, semesterId, gradingScaleId));
            assertEquals(200, openSection.statusCode(), openSection.body());
            jdbcTemplate.update(
                    "UPDATE grading_scales SET scale_status = 'INACTIVE' WHERE id = ?", gradingScaleId);
            HttpResponse<String> inactiveScaleOpen = send(
                    "http://localhost:" + port + "/api/course-sections/" + courseSectionId, "PUT", """
                    {"code":"SEED-%s","name":"Seed section","courseId":"%s","semesterId":"%s","gradingScaleId":"%s","status":"OPEN"}
                    """.formatted(suffix, courseId, semesterId, gradingScaleId));
            assertEquals(409, inactiveScaleOpen.statusCode());
            jdbcTemplate.update(
                    "UPDATE grading_scales SET scale_status = 'ACTIVE' WHERE id = ?", gradingScaleId);
            String studentCode = "SV" + (10_000_000 + Math.floorMod(suffix.hashCode(), 90_000_000));
            String studentEmail = "student-" + suffix + "@example.test";
            studentId = create("/api/students", """
                    {"code":"%s","fullName":"Test Student","email":"%s","studentClassessId":"%s","initialPassword":"initial-password-123"}
                    """.formatted(studentCode, studentEmail, studentClassId), cleanupUrls, 200);
            JsonNode createdStudent = objectMapper.readTree(send(
                    "http://localhost:" + port + "/api/students/" + studentId, "GET", null).body());
            UUID studentUserId = UUID.fromString(createdStudent.path("userId").asText());
            assertEquals(studentEmail, jdbcTemplate.queryForObject(
                    "SELECT email FROM users WHERE id = ?", String.class, studentUserId));
            assertEquals(studentEmail, jdbcTemplate.queryForObject(
                    "SELECT email FROM students WHERE id = ?", String.class, UUID.fromString(studentId)));
            assertEquals(1, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM user_roles assigned
                    JOIN roles role ON role.id = assigned.role_id
                    WHERE assigned.user_id = ?
                      AND role.code = 'STUDENT'
                    """, Integer.class, studentUserId));
            String updatedStudentEmail = "updated-student-" + suffix + "@example.test";
            HttpResponse<String> studentEmailUpdate = send(
                    "http://localhost:" + port + "/api/students/" + studentId, "PUT", """
                    {"email":"%s"}
                    """.formatted(updatedStudentEmail));
            assertEquals(200, studentEmailUpdate.statusCode(), studentEmailUpdate.body());
            assertEquals(updatedStudentEmail, jdbcTemplate.queryForObject(
                    "SELECT email FROM users WHERE id = ?", String.class, studentUserId));
            assertEquals(updatedStudentEmail, jdbcTemplate.queryForObject(
                    "SELECT email FROM students WHERE id = ?", String.class, UUID.fromString(studentId)));

            StudentStatus statusTemplate = new StudentStatus();
            statusTemplate.setCode("STATUS-" + suffix);
            statusTemplate.setName("Active");
            templateStatusId = studentStatusRepository.save(statusTemplate).getId().toString();

            HttpResponse<String> statusOptions = send(
                    "http://localhost:" + port + "/api/student-form-options/statuses", "GET", null);
            assertEquals(200, statusOptions.statusCode());
            assertTrue(containsId(statusOptions.body(), templateStatusId), statusOptions.body());

            String studentWithStatusAccount = createStudentAccount("status-" + suffix, "Student With Status");
            HttpResponse<String> studentWithStatus = send("http://localhost:" + port + "/api/students", "POST", """
                    {"userId":"%s","code":"SV%d","fullName":"Student With Status","studentClassessId":"%s","statusId":"%s"}
                    """.formatted(studentWithStatusAccount,
                            900_000_000 + Math.floorMod(suffix.hashCode(), 99_999_999),
                            studentClassId, templateStatusId));
            assertEquals(200, studentWithStatus.statusCode(), studentWithStatus.body());
            JsonNode studentWithStatusJson = objectMapper.readTree(studentWithStatus.body());
            String statusStudentId = studentWithStatusJson.path("id").asText();
            assignedStatusId = studentWithStatusJson.path("statusId").asText();
            assertEquals(templateStatusId, assignedStatusId);
            assertTrue(studentStatusHistoryRepository
                    .findFirstByStudentIdAndCurrentTrue(UUID.fromString(statusStudentId)).isPresent());
            cleanupUrls.add("http://localhost:" + port + "/api/students/" + statusStudentId);

            List<String> endpoints = List.of(
                    "/api/departments",
                    "/api/majors",
                    "/api/training-programs",
                    "/api/academic-years",
                    "/api/student-classes",
                    "/api/lecturers",
                    "/api/school-years",
                    "/api/semesters",
                    "/api/course-sections",
                    "/api/students");

            for (String endpoint : endpoints) {
                String url = "http://localhost:" + port + endpoint;
                String payload = payloadFor(endpoint, suffix, academicYearId, departmentId, majorId,
                        trainingProgramId, studentClassId, lecturerId, courseId, schoolYearId, semesterId,
                        courseSectionId, studentId, gradingScaleId.toString());
                int expectedCreateStatus = "/api/students".equals(endpoint) ? 200 : 201;
                String id = create(endpoint, payload, cleanupUrls, expectedCreateStatus);
                assertNotNull(id);

                HttpResponse<String> listResponse = send(url, "GET", null);
                assertEquals(200, listResponse.statusCode());
                assertTrue(containsId(listResponse.body(), id));

                HttpResponse<String> updateResponse = send(url + "/" + id, "PUT",
                        payloadFor(endpoint, suffix, academicYearId, departmentId, majorId, trainingProgramId,
                                studentClassId, lecturerId, courseId, schoolYearId, semesterId, courseSectionId,
                                studentId, gradingScaleId.toString(), false));
                assertEquals(200, updateResponse.statusCode(),
                        () -> "PUT " + endpoint + " failed: " + updateResponse.body());
                assertFalse(objectMapper.readTree(updateResponse.body()).get("isActive").asBoolean());

                HttpResponse<String> deleteResponse = send(url + "/" + id, "DELETE", null);
                assertEquals("/api/students".equals(endpoint) ? 200 : 204, deleteResponse.statusCode());
                cleanupUrls.remove(url + "/" + id);

                HttpResponse<String> getResponse = send(url + "/" + id, "GET", null);
                assertEquals(404, getResponse.statusCode());

                HttpResponse<String> finalListResponse = send(url, "GET", null);
                assertEquals(200, finalListResponse.statusCode());
                assertFalse(containsId(finalListResponse.body(), id));
            }
            assertEquals(404, send(
                    "http://localhost:" + port + "/api/student-course-sections",
                    "POST",
                    "{}").statusCode());
        } finally {
            for (int i = cleanupUrls.size() - 1; i >= 0; i--) {
                send(cleanupUrls.get(i), "DELETE", null);
            }
            if (courseId != null) {
                courseRepository.deleteById(UUID.fromString(courseId));
            }
            if (assignedStatusId != null) {
                studentStatusRepository.deleteById(UUID.fromString(assignedStatusId));
            }
            if (templateStatusId != null) {
                studentStatusRepository.deleteById(UUID.fromString(templateStatusId));
            }
        }
    }

    private String create(String endpoint, String payload, List<String> cleanupUrls) throws Exception {
        return create(endpoint, payload, cleanupUrls, 201);
    }

    private String create(String endpoint, String payload, List<String> cleanupUrls, int expectedStatus)
            throws Exception {
        String url = "http://localhost:" + port + endpoint;
        HttpResponse<String> response = send(url, "POST", payload);
        assertEquals(expectedStatus, response.statusCode(),
                () -> "POST " + endpoint + " failed: " + response.body());
        String id = objectMapper.readTree(response.body()).get("id").asText();
        cleanupUrls.add(url + "/" + id);
        return id;
    }

    private String payloadFor(
            String endpoint,
            String suffix,
            String academicYearId,
            String departmentId,
            String majorId,
            String trainingProgramId,
            String studentClassId,
            String lecturerId,
            String courseId,
            String schoolYearId,
            String semesterId,
            String courseSectionId,
            String studentId,
            String gradingScaleId) {
        return payloadFor(endpoint, suffix, academicYearId, departmentId, majorId, trainingProgramId,
                studentClassId, lecturerId, courseId, schoolYearId, semesterId, courseSectionId, studentId,
                gradingScaleId, true);
    }

    private String payloadFor(
            String endpoint,
            String suffix,
            String academicYearId,
            String departmentId,
            String majorId,
            String trainingProgramId,
            String studentClassId,
            String lecturerId,
            String courseId,
            String schoolYearId,
            String semesterId,
            String courseSectionId,
            String studentId,
            String gradingScaleId,
            boolean isActive) {
        return switch (endpoint) {
            case "/api/departments" -> """
                    {"code":"DEP-%s","name":"Test department","isActive":%s}
                    """.formatted(suffix, isActive);
            case "/api/majors" -> """
                    {"code":"MAJ-%s","name":"Test major","departmentId":"%s","isActive":%s}
                    """.formatted(suffix, departmentId, isActive);
            case "/api/training-programs" -> """
                    {"code":"TP-%s","name":"Test training program","departmentId":"%s","majorId":"%s","isActive":%s}
                    """.formatted(suffix, departmentId, majorId, isActive);
            case "/api/academic-years" -> """
                    {"code":"AY-%s","name":"Test academic year","year":"2025-2029","isActive":%s}
                    """.formatted(suffix, isActive);
            case "/api/student-classes" -> """
                    {"code":"CLASS-%s","name":"Test student class","academicYearId":"%s","departmentId":"%s","majorId":"%s","trainingProgramId":"%s","isActive":%s}
                    """.formatted(suffix, academicYearId, departmentId, majorId, trainingProgramId, isActive);
            case "/api/lecturers" -> """
                    {"lecturerCode":"GV-%s","fullName":"Test Lecturer","departmentId":"%s","initialPassword":"initial-password-123","isActive":%s}
                    """.formatted(suffix, departmentId, isActive);
            case "/api/school-years" -> """
                    {"code":"SY-TEST-%s","name":"2025-2026","startDate":"2025-08-01","endDate":"2026-07-31","isActive":%s}
                    """.formatted(suffix, isActive);
            case "/api/semesters" -> """
                    {"code":"SEM-%s","name":"Test semester","schoolYearId":"%s","startDate":"2025-08-01","endDate":"2025-12-31","isActive":%s}
                    """.formatted(suffix, schoolYearId, isActive);
            case "/api/course-sections" -> """
                    {"code":"CS-%s","name":"Test course section","courseId":"%s","semesterId":"%s","gradingScaleId":"%s","status":"DRAFT","isActive":%s}
                    """.formatted(suffix, courseId, semesterId, gradingScaleId, isActive);
            case "/api/students" -> """
                    {"userId":"%s","code":"SV%s","fullName":"Test Student","studentClassessId":"%s","isActive":%s}
                    """.formatted(createStudentAccount("loop-" + suffix, "Test Student"),
                            (10_000_000 + Math.floorMod(suffix.hashCode(), 90_000_000)) + 1,
                            studentClassId, isActive);
            case "/api/lecturer-course-classes" -> """
                    {"lecturerId":"%s","courseClassId":"%s","role":"primary","isActive":%s}
                    """.formatted(lecturerId, courseSectionId, isActive);
            default -> throw new IllegalArgumentException("Unsupported endpoint: " + endpoint);
        };
    }

    private boolean containsId(String responseBody, String id) throws Exception {
        for (JsonNode record : objectMapper.readTree(responseBody)) {
            JsonNode idNode = record.has("id") ? record.get("id") : record.get("ID");
            if (idNode != null && id.equalsIgnoreCase(idNode.asText())) {
                return true;
            }
        }
        return false;
    }

    private String createStudentAccount(String key, String displayName) {
        return studentAccountIds.computeIfAbsent(key, ignored -> {
            UUID userId = UUID.randomUUID();
            UUID studentRoleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM roles WHERE code = 'STUDENT'", UUID.class);
            jdbcTemplate.update("""
                    INSERT INTO users (id, username, password_hash, display_name, is_active, created_at)
                    VALUES (?, ?, 'test-hash', ?, 1, CURRENT_TIMESTAMP)
                    """, userId, "student-" + UUID.randomUUID(), displayName);
            jdbcTemplate.update(
                    "INSERT INTO user_roles (id, user_id, role_id) VALUES (?, ?, ?)",
                    UUID.randomUUID(), userId, studentRoleId);
            return userId.toString();
        });
    }

    private HttpResponse<String> send(String url, String method, String body) throws Exception {
        return sendWithToken(url, method, body, adminToken);
    }

    private HttpResponse<String> sendWithoutAuthorization(String url, String method, String body) throws Exception {
        return sendWithToken(url, method, body, null);
    }

    private HttpResponse<String> sendWithToken(String url, String method, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url));
        if (url.contains("/api/") && token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
