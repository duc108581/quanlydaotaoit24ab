import { ExecutionContext } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { JwtAuthGuard } from './jwt-auth.guard';

describe('JwtAuthGuard', () => {
  const secret = 'test-jwt-secret-that-is-at-least-thirty-two-bytes-long';

  it('accepts the shared Spring issuer and assigns the verified role claims', async () => {
    const jwt = new JwtService({
      secret,
      signOptions: { algorithm: 'HS256', issuer: 'academic-portal' },
    });
    const token = await jwt.signAsync({
      sub: 'user-id',
      username: 'student',
      fullName: 'Test Student',
      role: 'STUDENT',
      studentCode: 'SV001',
    });
    const request = { headers: { authorization: `Bearer ${token}` } };
    const context = {
      switchToHttp: () => ({ getRequest: () => request }),
    } as ExecutionContext;
    const guard = new JwtAuthGuard(jwt);

    await expect(guard.canActivate(context)).resolves.toBe(true);
    expect(request).toHaveProperty('user.roles', ['STUDENT']);
    expect(request).toHaveProperty('user.studentCode', 'SV001');
  });

  it('rejects a token without the shared issuer', async () => {
    const issuerJwt = new JwtService({
      secret,
      signOptions: { algorithm: 'HS256' },
    });
    const token = await issuerJwt.signAsync({ sub: 'user-id', role: 'ADMIN' });
    const context = {
      switchToHttp: () => ({
        getRequest: () => ({ headers: { authorization: `Bearer ${token}` } }),
      }),
    } as ExecutionContext;
    const guard = new JwtAuthGuard(issuerJwt);

    await expect(guard.canActivate(context)).rejects.toThrow(
      'Access token không hợp lệ hoặc đã hết hạn.',
    );
  });
});
