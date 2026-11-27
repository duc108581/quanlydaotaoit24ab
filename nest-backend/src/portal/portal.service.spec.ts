import { ConflictException } from '@nestjs/common';
import { validate } from 'class-validator';
import { Transaction } from 'mssql';
import { DatabaseService } from '../database/database.service';
import { CreateRegistrationDto } from './dto/create-registration.dto';
import { UpdateRegistrationStatusDto } from './dto/update-registration-status.dto';
import { PortalService } from './portal.service';

describe('PortalService registration rules', () => {
  function createService(responses: unknown[][]) {
    let responseIndex = 0;
    const query = jest.fn((statement?: string) =>
      Promise.resolve(responses[responseIndex++]),
    );
    const database = {
      query,
      withTransaction: jest.fn(
        (work: (transaction: Transaction) => Promise<unknown>) =>
          work({} as Transaction),
      ),
    } as unknown as DatabaseService;
    return { service: new PortalService(database), query };
  }

  const student = {
    id: 'user-id',
    username: 'student',
    fullName: 'Test Student',
    roles: ['STUDENT'],
    studentCode: 'SV001',
  };
  const studentRow = {
    id: 'student-id',
    userId: student.id,
  };
  const dto = { courseSectionId: '11111111-1111-4111-8111-111111111111' };
  const openSection = {
    id: dto.courseSectionId,
    status: 'open',
    isActive: true,
    deletedAt: null,
    isRegistrationOpen: true,
    maxStudents: 2,
  };

  it('rejects duplicate registrations before inserting', async () => {
    const { service, query } = createService([
      [studentRow],
      [openSection],
      [{ id: 'existing-registration' }],
    ]);

    await expect(service.createRegistration(student, dto)).rejects.toThrow(
      new ConflictException('Sinh viên đã đăng ký lớp học phần này.'),
    );
    expect(query).toHaveBeenCalledTimes(3);
  });

  it('rejects a registration when the section is already full', async () => {
    const { service, query } = createService([
      [studentRow],
      [openSection],
      [],
      [{ enrolled: 2 }],
    ]);

    await expect(service.createRegistration(student, dto)).rejects.toThrow(
      new ConflictException('Lớp học phần đã đủ sức chứa.'),
    );
    expect(query).toHaveBeenCalledTimes(4);
  });

  it('inserts a V2 registration with the next attempt number', async () => {
    const { service, query } = createService([
      [studentRow],
      [openSection],
      [],
      [{ enrolled: 1 }],
      [{ nextAttempt: 2 }],
      [{ id: 'new-registration' }],
      [],
      [
        {
          id: 'new-registration',
          studentCode: 'SV001',
          studentName: 'Test Student',
          sectionCode: 'SEC-1',
          sectionName: 'Section 1',
          courseName: 'Course 1',
          registrationDate: new Date(),
          status: 'REGISTERED',
        },
      ],
    ]);

    await service.createRegistration(student, dto);

    const statements = query.mock.calls.map(([statement]) => statement ?? '');
    expect(statements[0]).toContain('WHERE user_id = @userId');
    expect(statements[5]).toContain('INSERT INTO course_registrations');
    expect(statements[5]).toContain('is_active_registration');
    expect(query.mock.calls[5][0]).toContain('@attemptNumber');
  });

  it('marks a cancelled registration inactive in the V2 active-registration field', async () => {
    const { service, query } = createService([
      [studentRow],
      [{ studentId: studentRow.id, status: 'REGISTERED' }],
      [],
      [],
    ]);

    await service.cancelRegistration(student, 'registration-id');

    expect(query.mock.calls[2][0]).toContain('UPDATE course_registrations');
    expect(query.mock.calls[2][0]).toContain('is_active_registration = 0');
  });

  it('validates registration and review DTOs', async () => {
    const invalidRegistration = Object.assign(new CreateRegistrationDto(), {
      courseSectionId: 'not-a-uuid',
    });
    const invalidStatus = Object.assign(new UpdateRegistrationStatusDto(), {
      status: 'APPROVED',
    });

    expect(await validate(invalidRegistration)).toHaveLength(1);
    expect(await validate(invalidStatus)).toHaveLength(1);
  });
});
