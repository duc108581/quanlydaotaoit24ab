import {
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import type { Transaction } from 'mssql';
import { AuthUser } from '../auth/auth-user';
import { DatabaseService } from '../database/database.service';
import { CreateRegistrationDto } from './dto/create-registration.dto';

interface StudentRow {
  id: string;
}

interface SectionRow {
  id: string;
  status: string | null;
  isActive: boolean | null;
  deletedAt: Date | null;
  isRegistrationOpen: boolean;
  maxStudents: number | null;
}

interface IdRow {
  id: string;
}

interface CountRow {
  enrolled: number;
}

export interface RegistrationRow {
  id: string;
  studentCode: string;
  studentName: string;
  sectionCode: string;
  sectionName: string;
  courseName: string | null;
  registrationDate: Date | null;
  status: string;
}

@Injectable()
export class PortalService {
  constructor(private readonly database: DatabaseService) {}

  openSections() {
    return this.database.query(`
      SELECT cs.id, cs.code, cs.name, c.name AS courseName, c.credits,
             cs.max_students AS maxStudents, COUNT(r.id) AS enrolled
      FROM course_sections cs
      LEFT JOIN courses c ON c.id = cs.course_id
      LEFT JOIN student_course_sections r
        ON r.course_section_id = cs.id
       AND r.deleted_at IS NULL
       AND COALESCE(r.is_active, 1) = 1
       AND LOWER(COALESCE(r.status, '')) NOT IN ('cancelled', 'dropped')
      WHERE cs.deleted_at IS NULL
        AND COALESCE(cs.is_active, 1) = 1
        AND LOWER(COALESCE(cs.status, '')) = 'open'
        AND (cs.registration_start IS NULL OR cs.registration_start <= GETDATE())
        AND (cs.registration_end IS NULL OR cs.registration_end >= GETDATE())
      GROUP BY cs.id, cs.code, cs.name, c.name, c.credits, cs.max_students
      ORDER BY cs.code
    `);
  }

  async myRegistrations(user: AuthUser) {
    const student = await this.requireStudent(user);
    return this.registrationRows('r.student_id = @studentId', {
      studentId: student.id,
    });
  }

  async createRegistration(user: AuthUser, dto: CreateRegistrationDto) {
    const student = await this.requireStudent(user);
    return this.database.withTransaction(async (transaction) => {
      const sections = await this.database.query<SectionRow>(
        `SELECT id, status, is_active AS isActive, deleted_at AS deletedAt,
                CAST(CASE WHEN (registration_start IS NULL OR registration_start <= GETDATE())
                               AND (registration_end IS NULL OR registration_end >= GETDATE())
                          THEN 1 ELSE 0 END AS BIT) AS isRegistrationOpen,
                max_students AS maxStudents
         FROM course_sections WITH (UPDLOCK, HOLDLOCK)
         WHERE id = @sectionId`,
        { sectionId: dto.courseSectionId },
        transaction,
      );
      const section = sections[0];
      if (!section || section.deletedAt || section.isActive === false) {
        throw new NotFoundException(
          'Không tìm thấy lớp học phần đang hoạt động.',
        );
      }
      if (section.status?.toLowerCase() !== 'open') {
        throw new ConflictException('Lớp học phần hiện không mở đăng ký.');
      }
      if (!section.isRegistrationOpen) {
        throw new ConflictException('Đã ngoài thời gian đăng ký lớp học phần.');
      }

      const duplicates = await this.database.query<IdRow>(
        `SELECT id
         FROM student_course_sections WITH (UPDLOCK, HOLDLOCK)
         WHERE student_id = @studentId
           AND course_section_id = @sectionId
           AND deleted_at IS NULL
           AND COALESCE(is_active, 1) = 1
           AND LOWER(COALESCE(status, '')) NOT IN ('cancelled', 'dropped')`,
        { studentId: student.id, sectionId: dto.courseSectionId },
        transaction,
      );
      if (duplicates.length) {
        throw new ConflictException('Sinh viên đã đăng ký lớp học phần này.');
      }

      const counts = await this.database.query<CountRow>(
        `SELECT COUNT(id) AS enrolled
         FROM student_course_sections WITH (UPDLOCK, HOLDLOCK)
         WHERE course_section_id = @sectionId
           AND deleted_at IS NULL
           AND COALESCE(is_active, 1) = 1
           AND LOWER(COALESCE(status, '')) NOT IN ('cancelled', 'dropped')`,
        { sectionId: dto.courseSectionId },
        transaction,
      );
      if (
        section.maxStudents !== null &&
        Number(counts[0]?.enrolled ?? 0) >= section.maxStudents
      ) {
        throw new ConflictException('Lớp học phần đã đủ sức chứa.');
      }

      const inserted = await this.database.query<IdRow>(
        `INSERT INTO student_course_sections
           (id, student_id, course_section_id, registration_date, registration_type,
            status, created_at, is_active)
         OUTPUT INSERTED.id AS id
         VALUES (NEWID(), @studentId, @sectionId, GETDATE(), 'first', 'REGISTERED',
                 GETDATE(), 1)`,
        { studentId: student.id, sectionId: dto.courseSectionId },
        transaction,
      );
      const registrationId = inserted[0]?.id;
      if (!registrationId) {
        throw new Error('Không thể tạo đăng ký học phần.');
      }
      await this.addNotification(
        user.id,
        'Đăng ký học phần',
        'Yêu cầu đăng ký của bạn đã được gửi.',
        'REGISTRATION',
        transaction,
      );
      const rows = await this.registrationRows(
        'r.id = @registrationId',
        { registrationId },
        transaction,
      );
      if (!rows[0]) {
        throw new Error('Không thể tải đăng ký vừa tạo.');
      }
      return rows[0];
    });
  }

  async cancelRegistration(user: AuthUser, id: string) {
    const student = await this.requireStudent(user);
    return this.database.withTransaction(async (transaction) => {
      const rows = await this.database.query<{
        studentId: string;
        status: string;
      }>(
        `SELECT student_id AS studentId, status
         FROM student_course_sections WITH (UPDLOCK, HOLDLOCK)
         WHERE id = @id AND deleted_at IS NULL AND COALESCE(is_active, 1) = 1`,
        { id },
        transaction,
      );
      const current = rows[0];
      if (!current) {
        throw new NotFoundException('Không tìm thấy đăng ký.');
      }
      if (current.studentId.toLowerCase() !== student.id.toLowerCase()) {
        throw new ForbiddenException(
          'Bạn không thể hủy đăng ký của người khác.',
        );
      }
      if (
        ['CANCELLED', 'DROPPED'].includes((current.status ?? '').toUpperCase())
      ) {
        throw new ConflictException('Đăng ký này đã được hủy.');
      }
      await this.database.query(
        `UPDATE student_course_sections
         SET status = 'CANCELLED', updated_at = GETDATE()
         WHERE id = @id`,
        { id },
        transaction,
      );
      await this.addNotification(
        user.id,
        'Đã hủy đăng ký',
        'Đăng ký học phần của bạn đã được hủy.',
        'REGISTRATION',
        transaction,
      );
      return { message: 'Đã hủy đăng ký học phần.' };
    });
  }

  allRegistrations() {
    return this.registrationRows('1 = 1', {});
  }

  async updateRegistrationStatus(
    id: string,
    status: 'CONFIRMED' | 'CANCELLED',
  ) {
    return this.database.withTransaction(async (transaction) => {
      const rows = await this.database.query<{
        studentCode: string;
        status: string;
      }>(
        `SELECT s.code AS studentCode, r.status
         FROM student_course_sections r WITH (UPDLOCK, HOLDLOCK)
         JOIN students s ON s.id = r.student_id
         WHERE r.id = @id AND r.deleted_at IS NULL AND COALESCE(r.is_active, 1) = 1`,
        { id },
        transaction,
      );
      const current = rows[0];
      if (!current) {
        throw new NotFoundException('Không tìm thấy đăng ký.');
      }
      if (
        ['CANCELLED', 'DROPPED'].includes((current.status ?? '').toUpperCase())
      ) {
        throw new ConflictException('Đăng ký đã bị hủy và không thể duyệt.');
      }
      await this.database.query(
        `UPDATE student_course_sections
         SET status = @status, updated_at = GETDATE()
         WHERE id = @id`,
        { id, status },
        transaction,
      );
      await this.addNotificationForStudentCode(
        current.studentCode,
        'Cập nhật đăng ký học phần',
        `Yêu cầu đăng ký của bạn đã được ${status === 'CONFIRMED' ? 'duyệt' : 'từ chối'}.`,
        'REGISTRATION',
        transaction,
      );
      return { message: 'Đã cập nhật trạng thái đăng ký.', status };
    });
  }

  notifications(user: AuthUser) {
    return this.database.query(
      `SELECT id, title, content, type, created_at AS createdAt, read_at AS readAt
       FROM notifications
       WHERE user_id = @userId
       ORDER BY created_at DESC`,
      { userId: user.id },
    );
  }

  async markNotificationRead(user: AuthUser, id: string) {
    const updated = await this.database.query<IdRow>(
      `UPDATE notifications
       SET read_at = GETDATE()
       OUTPUT INSERTED.id AS id
       WHERE id = @id AND user_id = @userId AND read_at IS NULL`,
      { id, userId: user.id },
    );
    if (!updated.length) {
      const existing = await this.database.query<IdRow>(
        `SELECT id FROM notifications WHERE id = @id AND user_id = @userId`,
        { id, userId: user.id },
      );
      if (!existing.length) {
        throw new NotFoundException('Không tìm thấy thông báo.');
      }
    }
    return { message: 'Đã đánh dấu thông báo là đã đọc.' };
  }

  registrationSummary() {
    return this.database.query(`
      SELECT COUNT(DISTINCT r.student_id) AS registeredStudents,
             COUNT(r.id) AS totalRegistrations
      FROM student_course_sections r
      WHERE r.deleted_at IS NULL
        AND COALESCE(r.is_active, 1) = 1
        AND LOWER(COALESCE(r.status, '')) NOT IN ('cancelled', 'dropped')
    `);
  }

  courseSectionSummary() {
    return this.database.query(`
      SELECT COUNT(cs.id) AS openSections,
             COALESCE(SUM(CASE
               WHEN cs.max_students IS NOT NULL AND COALESCE(r.enrolled, 0) >= cs.max_students
               THEN 1 ELSE 0 END), 0) AS fullSections,
             COALESCE(SUM(CASE
               WHEN cs.max_students IS NULL THEN 0
               WHEN cs.max_students > COALESCE(r.enrolled, 0)
               THEN cs.max_students - COALESCE(r.enrolled, 0)
               ELSE 0 END), 0) AS remainingSeats
      FROM course_sections cs
      LEFT JOIN (
        SELECT course_section_id, COUNT(id) AS enrolled
        FROM student_course_sections
        WHERE deleted_at IS NULL
          AND COALESCE(is_active, 1) = 1
          AND LOWER(COALESCE(status, '')) NOT IN ('cancelled', 'dropped')
        GROUP BY course_section_id
      ) r ON r.course_section_id = cs.id
      WHERE cs.deleted_at IS NULL
        AND COALESCE(cs.is_active, 1) = 1
        AND LOWER(COALESCE(cs.status, '')) = 'open'
    `);
  }

  registrationsBySection() {
    return this.database
      .query(
        `
      SELECT cs.id, cs.code, cs.name, cs.max_students AS maxStudents,
             COUNT(r.id) AS enrolled
      FROM course_sections cs
      LEFT JOIN student_course_sections r
        ON r.course_section_id = cs.id
       AND r.deleted_at IS NULL
       AND COALESCE(r.is_active, 1) = 1
       AND LOWER(COALESCE(r.status, '')) NOT IN ('cancelled', 'dropped')
      WHERE cs.deleted_at IS NULL
      GROUP BY cs.id, cs.code, cs.name, cs.max_students
      ORDER BY cs.code
    `,
      )
      .then((rows: Array<Record<string, unknown>>) =>
        rows.map((row) => {
          const maximum =
            row.maxStudents === null || row.maxStudents === undefined
              ? null
              : Number(row.maxStudents);
          const enrolled = Number(row.enrolled ?? 0);
          return {
            ...row,
            maxStudents: maximum,
            enrolled,
            remainingSeats:
              maximum === null ? null : Math.max(maximum - enrolled, 0),
            isFull: maximum !== null && enrolled >= maximum,
          };
        }),
      );
  }

  private async requireStudent(user: AuthUser): Promise<StudentRow> {
    if (!user.studentCode) {
      throw new ForbiddenException('Tài khoản chưa liên kết hồ sơ sinh viên.');
    }
    const students = await this.database.query<StudentRow>(
      `SELECT id
       FROM students
       WHERE LOWER(code) = LOWER(@studentCode)
         AND deleted_at IS NULL
         AND COALESCE(is_active, 1) = 1`,
      { studentCode: user.studentCode },
    );
    if (!students[0]) {
      throw new ForbiddenException(
        'Không tìm thấy hồ sơ sinh viên đang hoạt động.',
      );
    }
    return students[0];
  }

  private registrationRows(
    predicate: string,
    parameters: Record<string, string>,
    transaction?: Transaction,
  ) {
    const query = `
      SELECT r.id, r.registration_date AS registrationDate, r.status,
             s.code AS studentCode, s.full_name AS studentName,
             cs.code AS sectionCode, cs.name AS sectionName,
             c.name AS courseName
      FROM student_course_sections r
      JOIN students s ON s.id = r.student_id
      JOIN course_sections cs ON cs.id = r.course_section_id
      LEFT JOIN courses c ON c.id = cs.course_id
      WHERE r.deleted_at IS NULL
        AND COALESCE(r.is_active, 1) = 1
        AND (${predicate})
      ORDER BY r.registration_date DESC
    `;
    return this.database.query<RegistrationRow>(query, parameters, transaction);
  }

  private async addNotification(
    userId: string,
    title: string,
    content: string,
    type: string,
    transaction: Transaction,
  ) {
    await this.database.query(
      `INSERT INTO notifications (id, user_id, title, content, type, created_at)
       VALUES (NEWID(), @userId, @title, @content, @type, GETDATE())`,
      { userId, title, content, type },
      transaction,
    );
  }

  private async addNotificationForStudentCode(
    studentCode: string,
    title: string,
    content: string,
    type: string,
    transaction: Transaction,
  ) {
    await this.database.query(
      `INSERT INTO notifications (id, user_id, title, content, type, created_at)
       SELECT NEWID(), id, @title, @content, @type, GETDATE()
       FROM users
       WHERE LOWER(student_code) = LOWER(@studentCode) AND is_active = 1`,
      { studentCode, title, content, type },
      transaction,
    );
  }
}
