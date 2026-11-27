# Functional Gap Analysis - QuanLyDaoTao_V2

## 1. Pham vi va cach xac minh

Bao cao doi chieu source Spring Boot, NestJS, cac trang HTML/JavaScript cua ung dung, cac script SQL trong `database/`, va metadata SQL Server. Cac thu muc thu vien (`node_modules`, `target`, AdminLTE vendor) khong duoc xem la source nghiep vu.

Da ket noi SQL Server bang Windows Integrated Authentication va chi chay cac cau `SELECT` metadata/dem dong. Khong chay migration, DDL, DML, hay thay doi phan quyen. Ket qua live:

- Database `QuanLyDaoTao_V2` co **37 bang**.
- `dbo.users.email` da ton tai va co unique filtered index `UX_users_email` voi filter `email IS NOT NULL`.
- Cac bang `students`, `student_classes`, `employees`, `lecturers`, `course_sections`, `course_section_schedules`, `course_registrations`, thang diem, diem, ky thi, ket qua thi va thong bao hien co 0 dong.
- `roles` co 4 vai tro dang hoat dong: `ADMIN`, `STAFF`, `LECTURER`, `STUDENT`. `permissions` va `role_permissions` deu 0 dong.

**Luu y ve trang thai:** `HOAN THANH` ben duoi chi co nghia luong source co API va xu ly backend tuong ung; khong co nghia da duoc nghiem thu chuc nang tren SQL Server. Khong co test dang chay Spring/Nest ket noi SQL Server trong bo test hien tai.

## 2. Tong quan

| Nhom | Trang thai | Ket luan chinh |
|---|---|---|
| Dang nhap/JWT/refresh va tai khoan | CHUA HOAN THANH | Spring co API tai khoan va JWT; quen mat khau chi tao token trong bo nho, khong gui email. |
| Khoa -> nganh -> chuong trinh | CHUA HOAN THANH | Da bo sung CRUD portal ADMIN, dropdown lien ket, tim kiem/loc/xem chi tiet; validation backend va test H2 da co. Chua nghiem thu CRUD tren SQL Server that. |
| Nien khoa, nam hoc, hoc ky | CHUA HOAN THANH | Da bo sung CRUD portal ADMIN, phan biet nien khoa voi nam hoc, sua kieu nam va lien ket hoc ky; test H2 da co. Chua nghiem thu CRUD tren SQL Server that. |
| Lop sinh vien -> sinh vien -> tai khoan | HOAN THANH (source, H2) | Portal ADMIN co CRUD lop, loc/tim kiem/phan trang/chi tiet, dropdown quan he va ngung hoat dong; tao student + account van trong transaction. Chua nghiem thu SQL Server that. |
| Giang vien -> tai khoan -> phan cong | CHUA HOAN THANH | Tao giang vien co transaction; API phan cong co nhung chi cho mot phan cong active tren mot lop, mau giao dien lai cho phep vai tro chinh/phu. |
| Hoc phan -> lop hoc phan | CHUA HOAN THANH | CRUD co; khong co API quan ly lich/phong, phan cong va giao dien tuong ung. Portal da co menu quan ly lop sinh vien va danh muc major/program. |
| Dang ky/huy/dang ky lai | CHUA HOAN THANH | Nest co luong dang ky/huy; chua kiem tra tien quyet, trung lich, va loai dang ky lai luon la `FIRST`. |
| Thang diem/diem thanh phan/diem sinh vien | CHUA CO | Co bang SQL, khong co entity/API/service/repository/frontend nghiep vu; live khong co du lieu thang diem. |
| Ky thi/ket qua thi | CHUA CO | Co bang SQL va FK, khong co API/service/repository/frontend nghiep vu. |
| Thong bao/thong ke | CHUA HOAN THANH | Nest co thong bao theo user va bao cao dang ky; chua co thong bao chung/lich hoc va bao cao hoc tap. |
| RBAC ADMIN/STAFF/LECTURER/STUDENT | LOI | Spring chi cho ADMIN goi API (ngoai `/api/auth/me`); UI khong co dashboard Staff/Lecturer; bang permission chua duoc dung. |

## 3. Danh muc giao dien, API va xu ly

API CRUD Spring duoi day duoc expose qua `AbstractCrudController`: `GET /api/{resource}`, `GET /api/{resource}/{id}`, `POST /api/{resource}`, `PUT /api/{resource}/{id}`, `DELETE /api/{resource}/{id}`. Delete trong service co ban la xoa mem.

| Nghiep vu | Giao dien frontend | Spring Boot: Controller / Service / Repository / API | NestJS | Trang thai va phat hien |
|---|---|---|---|---|
| Dang nhap, JWT, refresh, logout, tai khoan | [index.html](./src/main/resources/static/index.html), [portal.js](./src/main/resources/static/assets/js/portal.js), [nest.html](./src/main/resources/static/nest.html), [nest.js](./src/main/resources/static/assets/js/nest.js) | [AuthController.java](./src/main/java/com/example/quanlydaotao/controller/AuthController.java), [ApiAuthenticationInterceptor.java](./src/main/java/com/example/quanlydaotao/config/ApiAuthenticationInterceptor.java), [JwtTokenService.java](./src/main/java/com/example/quanlydaotao/service/JwtTokenService.java), [UserAccountProvisioningService.java](./src/main/java/com/example/quanlydaotao/service/UserAccountProvisioningService.java); JDBC truc tiep, khong co UserRepository. `/api/auth/login`, `/refresh`, `/me`, `/logout`, `/accounts`, `/accounts/{id}/status`, `/accounts/{id}/roles`, `/accounts/{id}/password`, `/roles`, `/independent-roles`. | Nest khong so huu auth; guard xac minh JWT do Spring cap. | **CHUA HOAN THANH.** `forgot-password` tra reset token ngay trong response, token chi nam trong `ConcurrentHashMap`, khong het han/khong gui email va mat khi restart. Form `nest.html` goi `/api/auth/register` nhung Spring khong co route nay. |
| Khoa -> nganh -> chuong trinh dao tao | [index.html](./src/main/resources/static/index.html), [portal.js](./src/main/resources/static/assets/js/portal.js) (tab Khoa/Ngành/Chương trình), [academic.html](./src/main/resources/static/academic.html), [academic.js](./src/main/resources/static/assets/js/academic.js) | [DepartmentController.java](./src/main/java/com/example/quanlydaotao/controller/DepartmentController.java) / [DepartmentService.java](./src/main/java/com/example/quanlydaotao/service/DepartmentService.java) / [DepartmentRepository.java](./src/main/java/com/example/quanlydaotao/repository/DepartmentRepository.java), `/api/departments`; [MajorController.java](./src/main/java/com/example/quanlydaotao/controller/MajorController.java) / [MajorService.java](./src/main/java/com/example/quanlydaotao/service/MajorService.java) / [MajorRepository.java](./src/main/java/com/example/quanlydaotao/repository/MajorRepository.java), `/api/majors`; [TrainingProgramController.java](./src/main/java/com/example/quanlydaotao/controller/TrainingProgramController.java) / [TrainingProgramService.java](./src/main/java/com/example/quanlydaotao/service/TrainingProgramService.java) / [TrainingProgramRepository.java](./src/main/java/com/example/quanlydaotao/repository/TrainingProgramRepository.java), `/api/training-programs`. | Khong co. | **CHUA HOAN THANH (SQL Server chua test).** Portal co CRUD, form dropdown khoa/nganh, loc theo quan he, tim kiem va xem chi tiet. Service validate FK, quan he khoa-nganh, ma trung theo unique key cua SQL Server, do dai va date. Kieu nam tuyen sinh/nam thanh lap la so nguyen `smallint`; test API hien dung H2. |
| Nien khoa -> lop sinh vien; nam hoc -> hoc ky | [index.html](./src/main/resources/static/index.html), [portal.js](./src/main/resources/static/assets/js/portal.js) (tab Niên khóa/Năm học/Học kỳ), [academic.html](./src/main/resources/static/academic.html), [academic.js](./src/main/resources/static/assets/js/academic.js) | [AcademicYearController.java](./src/main/java/com/example/quanlydaotao/controller/AcademicYearController.java) / [AcademicYearService.java](./src/main/java/com/example/quanlydaotao/service/AcademicYearService.java) / [AcademicYearRepository.java](./src/main/java/com/example/quanlydaotao/repository/AcademicYearRepository.java), `/api/academic-years`; [SchoolYearController.java](./src/main/java/com/example/quanlydaotao/controller/SchoolYearController.java) / [SchoolYearService.java](./src/main/java/com/example/quanlydaotao/service/SchoolYearService.java) / [SchoolYearRepository.java](./src/main/java/com/example/quanlydaotao/repository/SchoolYearRepository.java), `/api/school-years`; [SemesterController.java](./src/main/java/com/example/quanlydaotao/controller/SemesterController.java) / [SemesterService.java](./src/main/java/com/example/quanlydaotao/service/SemesterService.java) / [SemesterRepository.java](./src/main/java/com/example/quanlydaotao/repository/SemesterRepository.java), `/api/semesters`. | Khong co. | **CHUA HOAN THANH (SQL Server chua test).** Nien khoa la danh muc doc lap; nam hoc co nhieu hoc ky qua `school_year_id`. Form hoc ky chi gui FK qua dropdown va hien ten nam hoc; field legacy `schoolYearName` da bo. Backend validate ngay va ma hoc ky duy nhat trong nam hoc. |
| Lop sinh vien -> sinh vien -> tai khoan | [index.html](./src/main/resources/static/index.html), [portal.js](./src/main/resources/static/assets/js/portal.js) (trang lop SV va dropdown form SV), [students.html](./src/main/resources/static/students.html), [student.js](./src/main/resources/static/assets/js/student.js) | [StudentClassController.java](./src/main/java/com/example/quanlydaotao/controller/StudentClassController.java) / [StudentClassService.java](./src/main/java/com/example/quanlydaotao/service/StudentClassService.java) / [StudentClassRepository.java](./src/main/java/com/example/quanlydaotao/repository/StudentClassRepository.java), `/api/student-classes` CRUD va `PATCH /api/student-classes/{id}/status`; [StudentController.java](./src/main/java/com/example/quanlydaotao/controller/StudentController.java) / [StudentService.java](./src/main/java/com/example/quanlydaotao/service/StudentService.java) / [StudentRepository.java](./src/main/java/com/example/quanlydaotao/repository/StudentRepository.java), `/api/students`; form options `/api/student-form-options/classes` (them `includeInactive=true` de giu lop cu khi sua), `/departments`, `/majors`, `/training-programs`, `/statuses` qua [StudentFormOptionsController.java](./src/main/java/com/example/quanlydaotao/controller/StudentFormOptionsController.java). Tao account qua [UserAccountProvisioningService.java](./src/main/java/com/example/quanlydaotao/service/UserAccountProvisioningService.java); class/status history repositories cung duoc cap nhat trong StudentService. | Nest chi SELECT student de giai `user_id` thanh `student_id` cho nghiep vu cua Nest; khong tao/sua student. | **HOAN THANH source/H2; SQL Server E2E PENDING.** Trang ADMIN ho tro them/sua/xem/ngung kich hoat, search ma/ten, loc khoa/nganh/nien khoa/trang thai va phan trang. Dropdown hien ten/code, loc nganh theo khoa va chuong trinh theo nganh; form sinh vien chi tao moi voi lop active, khi sua van giu lop hien tai inactive. Service kiem tra ma trung, FK va quan he, khoa dong bo thao tac them student voi ngung/xoa lop bang pessimistic lock; khong soft-delete lop co student/history. Tao profile + account/role + email trong transaction. |
| Giang vien -> tai khoan -> phan cong | [portal.js](./src/main/resources/static/assets/js/portal.js), [academic.html](./src/main/resources/static/academic.html), [academic.js](./src/main/resources/static/assets/js/academic.js) | [LecturerController.java](./src/main/java/com/example/quanlydaotao/controller/LecturerController.java) / [LecturerService.java](./src/main/java/com/example/quanlydaotao/service/LecturerService.java) / [LecturerRepository.java](./src/main/java/com/example/quanlydaotao/repository/LecturerRepository.java), `/api/lecturers`; employee profile qua [EmployeeRepository.java](./src/main/java/com/example/quanlydaotao/repository/EmployeeRepository.java). Phan cong: [LecturerCourseClassController.java](./src/main/java/com/example/quanlydaotao/controller/LecturerCourseClassController.java) / [LecturerCourseClassService.java](./src/main/java/com/example/quanlydaotao/service/LecturerCourseClassService.java) / [LecturerCourseClassRepository.java](./src/main/java/com/example/quanlydaotao/repository/LecturerCourseClassRepository.java), `/api/lecturer-course-classes`. | Khong co. | **CHUA HOAN THANH.** Tao giang vien va employee/account/role co transaction. FK `lecturers.id -> employees.id`, employee -> users va assignment -> lecturer/section ton tai. Service chan hon mot assignment active tren mot lop, nhung UI cho chon “Giảng viên chính/phụ”; khong the thuc hien nhieu giang vien/section theo giao dien. Chua co lecturer self-service, roster hoac gradebook. |
| Hoc phan -> lop hoc phan | [portal.js](./src/main/resources/static/assets/js/portal.js), [academic.html](./src/main/resources/static/academic.html), [academic.js](./src/main/resources/static/assets/js/academic.js) | [CourseController.java](./src/main/java/com/example/quanlydaotao/controller/CourseController.java) / [CourseService.java](./src/main/java/com/example/quanlydaotao/service/CourseService.java) / [CourseRepository.java](./src/main/java/com/example/quanlydaotao/repository/CourseRepository.java), `/api/courses`; [CourseSectionController.java](./src/main/java/com/example/quanlydaotao/controller/CourseSectionController.java) / [CourseSectionService.java](./src/main/java/com/example/quanlydaotao/service/CourseSectionService.java) / [CourseSectionRepository.java](./src/main/java/com/example/quanlydaotao/repository/CourseSectionRepository.java), `/api/course-sections`; options `/api/academic-form-options/{courses,rooms,buildings,grading-scales}`. | Nest chi doc `courses` va `course_sections` de phuc vu registration/report. | **CHUA HOAN THANH.** Co CRUD course/section va kiem tra scale/assignment khi mo lop. `CourseService` dang la generic CRUD, khong validate khoa, credits hay ma hoc phan. Portal da co menu lop sinh vien va CRUD major/program; giao dien quan ly assignment, phong hoc va lich hoc van chua co. Live khong co section nao. |
| Phong hoc -> lich hoc -> lop hoc phan | [academic.html](./src/main/resources/static/academic.html), [academic.js](./src/main/resources/static/assets/js/academic.js) chi co lookup endpoint; khong co resource lich hoc/phong | Co `BuildingRepository` va `RoomRepository`; chi co API read-only rooms/buildings trong [AcademicFormOptionsController.java](./src/main/java/com/example/quanlydaotao/controller/AcademicFormOptionsController.java). **Khong co** Building/Room controller/service, `course_section_schedules` entity/controller/service/repository. | Khong co. | **CHUA CO.** DB co `course_section_schedules` va FK section/room; code khong tao/sua/xem lich hoc. `CourseSection` cung khong map room/schedule. |
| Dang ky, huy, duyet va dang ky lai | [index.html](./src/main/resources/static/index.html), [portal.js](./src/main/resources/static/assets/js/portal.js), [nest.html](./src/main/resources/static/nest.html), [nest.js](./src/main/resources/static/assets/js/nest.js) | Khong co controller/service/repository ghi registration; phu hop quyet dinh Spring khong so huu registration. | [PortalController](./nest-backend/src/portal/portal.controller.ts), [PortalService](./nest-backend/src/portal/portal.service.ts), `DatabaseService` raw SQL; GET `/api/course-registrations/open-sections`, GET `/mine`, POST `/course-registrations`, DELETE `/{id}`, GET `/admin/all`, PATCH `/admin/{id}/status`. | **CHUA HOAN THANH.** Nest ghi dung `course_registrations`; live co FK student/section va cac cot registration can thiet, hien 0 dong. Dang ky lai sau huy co the tao lan moi, nhung `registration_type` van luon `FIRST`; dem `attempt_number` theo student+section, khong phai theo hoc phan. Khong kiem tra `course_prerequisites`, trung lich, diem/tin chi toi da; bang tien quyet va lich hoc hien 0 dong. API duyet khong cap nhat cot `reviewed_by`/`reviewed_at` dang co trong schema. |
| Thang diem -> thanh phan diem -> diem sinh vien | Khong co giao dien nghiep vu; form section chi co dropdown thang diem. | Chi co read-only `GET /api/academic-form-options/grading-scales`; khong co entity/controller/service/repository cho grading scale rules, grade components, component grades hay final grades. | Khong co. | **CHUA CO.** Live `grading_scales`, `grading_scale_rules`, `course_section_grade_components`, `registration_component_grades`, `registration_final_grades` deu 0 dong. Lop khong mo duoc neu khong co scale ACTIVE; do vay can tao/duyet du lieu thang diem truoc. |
| Ky thi -> ket qua thi | Khong co. | Khong co `Exam`/`ExamResult` entity, controller, service, repository hay API. | Khong co. | **CHUA CO.** Bang `exams` va `exam_results` ton tai trong DB nhung hien 0 dong; khong co luong nhap/duyet ket qua. |
| Thong bao | [portal.js](./src/main/resources/static/assets/js/portal.js), [nest.js](./src/main/resources/static/assets/js/nest.js), [nest.html](./src/main/resources/static/nest.html) | Khong co API/service ghi thong bao. | `PortalController` GET `/api/notifications`, PATCH `/api/notifications/:id/read`; `PortalService` doc theo user va tu tao thong bao khi dang ky/huy/duyet. | **CHUA HOAN THANH.** Nest dung bang `notifications` va loc theo user; chua co tao/thong bao chung, thong bao lich hoc, quan tri noi dung hay phan trang. Live 0 thong bao. |
| Thong ke/bao cao | [portal.js](./src/main/resources/static/assets/js/portal.js), [nest.js](./src/main/resources/static/assets/js/nest.js), [nest.html](./src/main/resources/static/nest.html) | Khong co API bao cao hoc tap. | `PortalController` GET `/api/reports/registrations`, `/course-sections`, `/registrations-by-section`; truy van SQL trong `PortalService`. | **CHUA HOAN THANH.** Chi co so lieu dang ky/suc chua, chua co diem, ket qua thi, hoc phi hay bao cao theo role. UI dashboard khi API loi co the thay bang 0/danh sach rong, khong phan biet loi va khong co du lieu. |
| RBAC va dashboard theo role | [portal.js](./src/main/resources/static/assets/js/portal.js), [index.html](./src/main/resources/static/index.html) | [ApiAuthenticationInterceptor.java](./src/main/java/com/example/quanlydaotao/config/ApiAuthenticationInterceptor.java) chan moi `/api/**` theo ADMIN, ngoai `/api/auth/me`; roles duoc load trong [AuthController.java](./src/main/java/com/example/quanlydaotao/controller/AuthController.java). | `JwtAuthGuard`, `RolesGuard` va decorator `@Roles`; endpoint student/Admin duoc gate. | **LOI.** `STAFF` va `LECTURER` khong co dashboard/quyen Spring; `portal.js` chi chia admin va “student”, nen lecturer/staff bi dua vao student menu. `permissions`/`role_permissions` khong duoc dung va live dang rong. |

## 4. Lien ket du lieu va cac phu thuoc

### Quan he da doi chieu voi SQL Server

- `departments`, `majors`, `training_programs`, `student_classes`, `academic_years`: code validate khoa/nganh/chuong trinh va lop; schema co cac bang va cac FK lien quan.
- `school_years -> semesters -> course_sections`; `courses -> course_sections`; `course_sections -> grading_scales`; cac FK chinh ton tai.
- `student_classes -> students.current_student_class_id`; `students -> users`; `student_class_history` va `student_status_history` lien ket ve student va danh muc tuong ung.
- `employees -> users`; `lecturers.id -> employees.id`; `lecturer_course_sections` lien ket giang vien va section.
- `course_section_schedules` lien ket section va rooms; `rooms -> buildings`.
- `course_registrations` lien ket student va section. Bang tien quyet lien ket hai hoc phan. Bang diem va ket qua thi lien ket den registration/section/exam theo schema.
- `notifications.user_id -> users`; `user_roles` lien ket users/roles; `role_permissions` lien ket roles/permissions.

### Chuoi phu thuoc nghiep vu

1. Khoa -> Nganh -> Chuong trinh dao tao -> Lop sinh vien.
2. Nien khoa -> Lop sinh vien; Nam hoc -> Hoc ky -> Lop hoc phan.
3. Lop sinh vien -> Ho so sinh vien -> `users` + `user_roles(STUDENT)`.
4. Nhan vien -> Giang vien -> Phan cong giang day; phan cong la dieu kien de mo lop.
5. Hoc phan + Hoc ky + Thang diem + Phan cong -> Lop hoc phan du dieu kien mo.
6. Lop hoc phan + Sinh vien -> Dang ky; tien quyet va lich hoc can duoc kiem tra truoc khi ghi.
7. Dang ky + Thanh phan diem + Thang diem -> Diem tong ket; Dang ky + Ky thi -> Ket qua thi.
8. Dang ky/duyet/huy -> Thong bao; cac bang dang ky/diem/thi -> bao cao.

## 5. Loi mapping, API va schema/script can chu y

### Loi hoac han che da xac minh

1. **JWT tren giao dien:** portal ADMIN dung `springRequest` de gui bearer token va refresh khi can; `academic.js` va `student.js` legacy gui `nestAccessToken` neu co. Hai trang legacy khong tu refresh access token; khi het han can dang nhap lai trong portal.
2. **Mapping nam:** `establishedYear` va `admissionYear` tren cac form danh muc/sinh vien da chuyen sang so nguyen phu hop `Short`/`smallint`.
3. **Field legacy khong ton tai trong V2:** input `academicYearYear` da bo khoi form sinh vien; lop sinh vien la noi lien ket nien khoa. `academic.js` va portal khong gui `schoolYearName`; hoc ky hien dung `schoolYearId`.
4. **Unique key theo schema:** validation danh muc khop unique keys live: ma khoa va ma nien khoa/nam hoc global; ma nganh theo khoa; ma chuong trinh theo nganh; ma hoc ky theo nam hoc. Kiem tra duplicate bao gom ca ban ghi da xoa mem vi cac unique indexes live khong filter `deleted_at`.
4. **Lien ket assignment:** UI cho phep chon giang vien chinh/phu, nhung service va unique active index gioi han mot assignment active tren moi section. API entity co alias `courseClassId`, con cot V2 dung la `course_section_id`; khong nen doi ten cot DB theo alias nay.
5. **Dang ky lai va dieu kien dang ky:** Nest tinh attempt theo section va luon ghi `registration_type='FIRST'`; chua dung bang tien quyet/lich hoc de chan dang ky khong hop le.
6. **Endpoint `/auth/register` khong ton tai:** form self-register trong `nest.html` co giao dien nhung khong co route tuong ung tai Spring/Nest.
7. **Thong tin reset password:** token duoc tra truc tiep cho client va luu trong bo nho, khong co email, expiry hoac kho luu dung chung cho nhieu instance.
8. **Scripts legacy khong phai migration V2:** [quanlydaotao.sql](./database/quanlydaotao.sql) dung `USE [QuanLyDaoTao]` va tao cac ten cu `student_course_sections`, `lecturer_course_classes`; [fix-existing-schema.sql](./database/fix-existing-schema.sql) nham database `quanlydaotao` va cung dung ten bang cu. V2 hien dung `course_registrations`, `lecturer_course_sections`, `students.current_student_class_id`. Khong chay hai script legacy nay tren `QuanLyDaoTao_V2`.
9. [add-refresh-tokens.sql](./database/add-refresh-tokens.sql) khong co guard ten database va co DDL mau `users` theo schema legacy (`full_name`, `role_name`, `student_code`, `lecturer_code`), khac schema V2 hien tai (`display_name`, `roles`, `user_roles`). [add-notifications.sql](./database/add-notifications.sql) cung khong co guard ten database. Cac script V2 email, preflight, integrity va role co kiem tra database muc tieu.
10. Script phan quyen [prepare-v2-backend-database-roles.sql](./database/prepare-v2-backend-database-roles.sql) da tach write registration cho Nest va deny write `course_registrations` cho Spring; day la script thay doi quyen va chua duoc ap dung trong phien audit nay.

### Hien thi loi khong ro rang

- `portal.js` nhieu cho `catch(() => [])` hoac hien thi metric `0`; API 401/403/500 co the bi hien nhu danh sach rong.
- Dashboard admin co text co dinh “SQL Server đã kết nối”, khong duoc kiem tra qua health endpoint.
- Old `students.html` lien ket create/edit ve `index.html?page=form`, nhung `index.html` hien tai la portal moi va khong xu ly query `page=form`; day la navigation legacy khong dong bo.

## 6. Phan quyen API hien tai va khoang trong

| Backend/API | Quy tac dang co | Phan tich |
|---|---|---|
| Spring `/api/auth/login`, `/forgot-password`, `/reset-password`, `/refresh`, `/logout` | Public theo interceptor | Login/refresh/logout hoat dong o source; forgot/reset chua co delivery email va reset token an toan. |
| Spring `/api/auth/me` | Can access JWT hop le; khong bat buoc ADMIN | Tra thong tin claim/account co ban. |
| Cac Spring `/api/**` khac | Chi ADMIN | Du an chua mo quyen STAFF/LECTURER/STUDENT; khong co scope theo user cho cac API nay. |
| Nest `/api/course-registrations/open-sections`, `/mine`, POST registrations, DELETE registration | JWT va role STUDENT | `mine`/huy dung `user_id` tu JWT de tim student va chan huy ho so cua nguoi khac. |
| Nest `/api/course-registrations/admin/**`, `/api/reports/**` | JWT va role ADMIN | Toan bo danh sach/bao cao quan tri. |
| Nest `/api/notifications`, PATCH read | JWT; khong gioi han role cu the | Truy van/update co dieu kien `user_id = JWT sub`, khong doc/mark notification cua user khac. |

Nest guard chi validate JWT/claims, khong truy van lai trang thai user/role trong DB cho moi request; token cu van co the duoc chap nhan toi khi het han neu tai khoan/role vua bi khoa/thu hoi. Can xac nhan policy truoc khi sua; bao cao nay khong tu mo quyen.

## 7. Kiem thu va muc do xac nhan

- Spring integration tests dang dung profile H2 (`application-test.properties` co `jdbc:h2:mem`, `ddl-auto=create-drop`), khong phai SQL Server.
- Nest unit tests mock `DatabaseService`; e2e test hien chi kiem tra route goc Hello World, khong kiem tra registration bang SQL Server.
- Trong phien nay da chay truy van SQL Server chi doc: xac nhan database co 37 bang, schema/FK/unique indexes cua 6 bang danh muc va `users.email`/`UX_users_email`; **khong** chay luong HTTP ghi du lieu, SQL write hay migration.
- Test moi `mvnw clean test`: 13 tests pass, 0 failure/error tren H2 (AcademicManagementApiTests 4; AuthJwtApiTests 8; application smoke 1). `node --check portal.js` va `git diff --check` pass. Test bao gom CRUD/status/validation/lop inactive va history, dropdown lop, transaction class lock; logout refresh-token revoke va protected endpoint khong token bi 401. Chua kiem thu thao tac CRUD/logout qua SQL Server.
- Do do, chua co bang chung SQL Server runtime cho login/refresh/logout, tao student/lecturer va transaction rollback, dang ky/huy/duyet/dang ky lai, notifications, reports, RBAC grants, hoac cac API CRUD.
- Live data de test cac luong nay dang thieu: khong co student/class/lecturer/section/registration; khong co grading scale/rule/component, exam/result, schedule/prerequisite.

### Checklist SQL Server that truoc khi go-live

Chay tren QA database duoc phe duyet, khong phai production:

1. Xac nhan dung server/database, schema 37 bang, email column/index, FK/unique/check constraints va migration inventory.
2. Dung hai SQL runtime users tach biet; xac minh effective permissions: Spring khong INSERT/UPDATE/DELETE `course_registrations`; Nest khong ghi users, roles, students, employees, lecturers va cac bang hoc vu do Spring so huu.
3. Login ADMIN/STUDENT/LECTURER; kiem tra JWT issuer/audience-policy, roles, het han; refresh token rotate va token cu bi tu choi; logout thu hoi refresh token.
4. Tao student: kiem tra `users`, `user_roles(STUDENT)`, `students`, class/status history, dong bo email; tao profile loi phai rollback ca account.
5. Tao lecturer: kiem tra `users`, role, `employees`, `lecturers`; email/profile dong bo va rollback khi insert lecturer that bai.
6. Thu duplicate username/email voi khac hoa-thuong/whitespace; thu invalid class/khoa/nganh/program va inactive account.
7. Tao du lieu thang diem/rules/components hop le, section, schedule/room va assignment; mo lop chi khi cac prerequisite da thoa.
8. Dang ky: lop mo/dong, gioi han thoi gian, day slot, het cho, duplicate, tien quyet, student khong co profile; kiem tra atomicity va concurrent requests.
9. Huy/duyet/tu choi/dang ky lai: status transition, active flag, attempt/type, `reviewed_by/reviewed_at`, notifications va quyen khong the thao tac registration cua user khac.
10. Nhap component/final grade, exam/result; xac minh quan he den dung registration/section, thang diem version/snapshot va permissions cua lecturer duoc phan cong.
11. Kiem tra notification list/read chi trong scope user; bao cao doi chieu truc tiep voi SQL queries.
12. Chay test rollback, xung dot FK/unique, va xem log loi; ghi lai SQL Server version/compatibility, migration versions va ket qua test. Khong ghi credential/secret vao bao cao.

## 8. De xuat thu tu 4 Sprint va phu thuoc

| Sprint de xuat | Pham vi | Tien dieu kien / dau ra |
|---|---|---|
| Sprint 1 - Nen tang va danh muc | Chot schema/migration V2, phan quyen va role matrix; lam cho CRUD portal co JWT va loi ro rang; hoan thien khoa/nganh/chuong trinh, nien khoa/nam hoc/hoc ky/lop SV, tao student/lecturer; kiem thu SQL Server QA. | Phai co backup/DBA approval cho thay doi SQL; can chot quyen STAFF/LECTURER/STUDENT. Khong chay legacy scripts. |
| Sprint 2 - Mo lop va lich giang day | Quan ly phong/toa nha/lich; course section; assignment giang vien; quan ly thang diem, rule, component; gate mo lop theo assignment + scale + lich. | Phu thuoc Sprint 1 va co data catalog, semester, course, lecturer, room; can nap thang diem da duoc nghiep vu phe duyet. |
| Sprint 3 - Dang ky NestJS | Bo sung validation tien quyet, trung lich, capacity/concurrency; quy tac dang ky lai/attempt/type; duyet/huy; notification va scope user. | Phu thuoc section OPEN, schedule, assignment va registration tables; Nest la noi duy nhat ghi `course_registrations`. |
| Sprint 4 - Ket qua, thi va bao cao | Nhap/tinh/duyet diem thanh phan va diem tong ket; ky thi/ket qua thi; dashboards theo role; bao cao dang ky + diem/thi; SQL Server E2E va nghiem thu. | Phu thuoc registration, grading scale/components va role policy; Nest chi lam reports/notifications theo ownership da chot, Spring quan ly nghiep vu diem/thi. |

## 9. Ket luan

Source hien co portal ADMIN quan ly lop sinh vien va dropdown lop tren form sinh vien, cung logout client/server refresh-token; Spring tao student/account/profile trong transaction. Tuy nhien, chua the ket luan da san sang SQL Server: moi kiem thu code va API da chay tren H2. Access JWT la stateless va chua bi thu hoi ngay khi logout; token access cu chi het hieu luc khi het han. UI/API cho assignment, phong/lich, diem/thi con thieu; RBAC STAFF/LECTURER can policy da duoc xac nhan; registration NestJS chua dap ung day du tien quyet/trung lich/dang ky lai. Khong co test nghiem thu chuc nang tren SQL Server trong phien nay.

Database hien co dung 37 bang va schema lien ket chinh cho V2; cac bang nghiep vu cot loi dang rong. Chi nen chuyen sang trien khai sprint tiep theo sau khi xac nhan ma tran role, chon dung script V2 va tao du lieu QA nghiep vu can thiet. Cap nhat nay chi sua source va tai lieu; khong chay migration hay SQL ghi du lieu, khong thay doi database grants.

## 10. Cap nhat 2026-11-27: Quan ly lop sinh vien va logout

### Da hoan thien trong source

- ADMIN co trang quan ly lop sinh vien: them, sua, xem chi tiet, ngung/kich hoat, tim kiem ma/ten, loc khoa/nganh/nien khoa/trang thai, phan trang va lam moi.
- Dropdown quan he hien ma/ten thay vi UUID; nganh loc theo khoa, chuong trinh loc theo nganh. Form sinh vien lay lop tu API, chi cho chon lop active khi tao moi va van giu gia tri lop inactive hien tai khi sua ho so.
- Backend validate required fields, ma trung, do dai theo schema (`code` 100, `name` 255), FK va quan he khoa-nganh-chuong trinh-nien khoa. Soft-delete bi chan neu lop co student hoac class history.
- Pessimistic lock tren lop dong bo viec tao/chuyen student voi ngung hoat dong/xoa lop trong transaction; thao tac tao student/account/role va dong bo email van nam trong transaction hien co.
- `PATCH /api/student-classes/{id}/status` la endpoint doi trang thai. Cac API quan tri van duoc bao ve theo quy tac ADMIN hien co.
- Logout goi `POST /api/auth/logout` de thu hoi refresh token, sau do luon xoa access/refresh token, user/role va giao dien authenticated khoi client; loi server logout van dua nguoi dung ve dang nhap. Access JWT khong bi revoke tuc thi boi backend stateless.

### Schema, test va gioi han

- Schema SQL Server da duoc kiem tra truoc khi code: 37 bang; `student_classes.code NVARCHAR(100)`, `name NVARCHAR(255)`; bat buoc `academic_year_id`, `department_id`, `major_id`, `training_program_id`; advisor nullable; khoa/nganh/program co FK; `students.current_student_class_id` lien ket lop; `student_class_history` luu lich su va co unique filtered index cho current history. Khong tao migration, khong chay SQL write.
- `mvnw clean test`: 13 passed, 0 failed/errors, chay tren H2 (`ddl-auto=create-drop`); JavaScript `node --check portal.js` pass; `git diff --check` pass.
- Chua chay UI end-to-end tren browser hoac CRUD/logout voi SQL Server. Database live luc kiem tra khong co student/class/history/section/registration de test nghiep vu. Can chay checklist QA SQL Server sau khi DBA/owner phe duyet.

### De xuat dang ky theo lop sinh vien (chua trien khai)

- Co the dung `student_classes` va `students.current_student_class_id` de lay danh sach sinh vien hien tai cua lop; moi ket qua dang ky van tao mot hang theo student trong `course_registrations`.
- Khong can bang lien ket lop-sinh-vien voi hoc-phan, va khong can schema migration cho preview/commit neu dung schema/index hien co. `course_registrations` da gan tung student voi `course_section`; unique indexes chan dang ky active trung student+section va trung attempt number theo student+section, nhung khong tu dong chan cung mot hoc phan o hai section khac nhau trong cung hoc ky.
- De xuat API NestJS (Nest la owner duy nhat ghi registrations): `POST /api/admin/course-registrations/bulk/preview` nhan `studentClassId`, `courseSectionId`, tra tung student eligible/ly do; `POST /api/admin/course-registrations/bulk/execute` nhan danh sach `studentIds` duoc ADMIN chon ro rang, revalidate va ghi trong mot transaction. Khong tu dong chon/ghi ca lop; neu bat ky student nao trong danh sach chon khong con du dieu kien hoac capacity khong du, rollback batch va tra ly do theo student.
- Preview/execute can kiem tra student active, lop/section/school-term dang dung thoi gian, prerequisites va ket qua dat, trung lich theo schedule, course trung trong hoc ky, attempts/type, capacity va permission ADMIN. Serialize capacity bang khoa row `course_sections` trong transaction. De chong dang ky cung course o cac section song song, khoa mot parent course/stable key chung trong transaction khi check student+course+semester; khoa section rieng khong du de tranh race condition giua hai section. FK/unique index hien co tiep tuc chong duplicate student+section/attempt. Can xac nhan quy tac retry/partial results va chinh sach cho phep hoc lai; neu muon rang buoc cross-section o database thi can danh gia rieng kha nang migration/index.
- Database grant script hien duoc bao cao chua cap Nest quyen `SELECT` tren `student_classes`; DBA can review/quyet dinh cap least-privilege SELECT truoc khi Nest dung API nay. Chua thay doi grant.
