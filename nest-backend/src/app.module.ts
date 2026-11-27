import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { JwtModule } from '@nestjs/jwt';
import { DatabaseModule } from './database/database.module';
import { PortalController } from './portal/portal.controller';
import { PortalService } from './portal/portal.service';
import { JwtAuthGuard } from './auth/jwt-auth.guard';
import { RolesGuard } from './auth/roles.guard';

function validateEnvironment(environment: Record<string, unknown>) {
  const required = [
    'JWT_SECRET',
    'DB_SERVER',
    'DB_USER',
    'DB_PASSWORD',
    'DB_NAME',
  ];
  const missing = required.filter((key) => {
    const value = environment[key];
    return typeof value !== 'string' || !value.trim();
  });
  if (missing.length) {
    throw new Error(
      `Missing required environment variables: ${missing.join(', ')}`,
    );
  }
  const jwtSecret = environment.JWT_SECRET;
  if (typeof jwtSecret !== 'string' || jwtSecret.length < 32) {
    throw new Error('JWT_SECRET must contain at least 32 characters.');
  }
  if (environment.DB_NAME !== 'QuanLyDaoTao_V2') {
    throw new Error('DB_NAME must be QuanLyDaoTao_V2.');
  }
  return environment;
}

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true, validate: validateEnvironment }),
    JwtModule.registerAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        secret: config.getOrThrow<string>('JWT_SECRET'),
        signOptions: { algorithm: 'HS256' },
      }),
    }),
    DatabaseModule,
  ],
  controllers: [PortalController],
  providers: [PortalService, JwtAuthGuard, RolesGuard],
})
export class AppModule {}
