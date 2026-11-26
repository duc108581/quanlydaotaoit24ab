import { ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { AuthUser } from './auth-user';
import { ROLES_KEY } from './roles.decorator';
import { RolesGuard } from './roles.guard';

describe('RolesGuard', () => {
  it('allows only a user with an explicitly required role', () => {
    const handler = () => undefined;
    Reflect.defineMetadata(ROLES_KEY, ['ADMIN'], handler);
    const request: { user: AuthUser } = {
      user: {
        id: 'user-id',
        username: 'student',
        fullName: 'Test Student',
        roles: ['STUDENT'],
      },
    };
    const context = {
      getHandler: () => handler,
      getClass: () => class {},
      switchToHttp: () => ({ getRequest: () => request }),
    } as ExecutionContext;
    const guard = new RolesGuard(new Reflector());

    expect(guard.canActivate(context)).toBe(false);
    request.user.roles = ['ADMIN'];
    expect(guard.canActivate(context)).toBe(true);
  });
});
