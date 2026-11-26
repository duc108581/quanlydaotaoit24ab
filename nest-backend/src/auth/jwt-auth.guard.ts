import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { Request } from 'express';
import { AuthUser, JwtClaims } from './auth-user';

type AuthenticatedRequest = Request & { user?: AuthUser };

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(private readonly jwt: JwtService) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const authorization = request.headers.authorization;
    if (!authorization?.startsWith('Bearer ')) {
      throw new UnauthorizedException('Vui lòng đăng nhập.');
    }

    let claims: JwtClaims;
    try {
      claims = await this.jwt.verifyAsync<JwtClaims>(authorization.slice(7), {
        algorithms: ['HS256'],
        issuer: 'academic-portal',
      });
    } catch {
      throw new UnauthorizedException(
        'Access token không hợp lệ hoặc đã hết hạn.',
      );
    }

    const roles = claims.roles ?? (claims.role ? [claims.role] : []);
    if (!claims.sub || roles.length === 0) {
      throw new UnauthorizedException(
        'Access token thiếu thông tin người dùng hoặc vai trò.',
      );
    }
    request.user = {
      id: claims.sub,
      username: claims.username ?? '',
      fullName: claims.fullName ?? '',
      roles: roles.map((role) => role.toUpperCase()),
      studentCode: claims.studentCode ?? undefined,
    };
    return true;
  }
}
