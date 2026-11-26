import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  UseGuards,
} from '@nestjs/common';
import { CurrentUser } from '../auth/current-user.decorator';
import type { AuthUser } from '../auth/auth-user';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../auth/roles.decorator';
import { RolesGuard } from '../auth/roles.guard';
import { CreateRegistrationDto } from './dto/create-registration.dto';
import { UpdateRegistrationStatusDto } from './dto/update-registration-status.dto';
import { PortalService } from './portal.service';

@Controller()
@UseGuards(JwtAuthGuard, RolesGuard)
export class PortalController {
  constructor(private readonly portal: PortalService) {}

  @Get('course-registrations/open-sections')
  @Roles('STUDENT')
  openSections() {
    return this.portal.openSections();
  }

  @Get('course-registrations/mine')
  @Roles('STUDENT')
  myRegistrations(@CurrentUser() user: AuthUser) {
    return this.portal.myRegistrations(user);
  }

  @Post('course-registrations')
  @Roles('STUDENT')
  createRegistration(
    @CurrentUser() user: AuthUser,
    @Body() dto: CreateRegistrationDto,
  ) {
    return this.portal.createRegistration(user, dto);
  }

  @Delete('course-registrations/:id')
  @Roles('STUDENT')
  cancelRegistration(
    @CurrentUser() user: AuthUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.portal.cancelRegistration(user, id);
  }

  @Get('course-registrations/admin/all')
  @Roles('ADMIN')
  allRegistrations() {
    return this.portal.allRegistrations();
  }

  @Patch('course-registrations/admin/:id/status')
  @Roles('ADMIN')
  updateRegistrationStatus(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateRegistrationStatusDto,
  ) {
    return this.portal.updateRegistrationStatus(id, dto.status);
  }

  @Get('notifications')
  notifications(@CurrentUser() user: AuthUser) {
    return this.portal.notifications(user);
  }

  @Patch('notifications/:id/read')
  markNotificationRead(
    @CurrentUser() user: AuthUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.portal.markNotificationRead(user, id);
  }

  @Get('reports/registrations')
  @Roles('ADMIN')
  registrationSummary() {
    return this.portal.registrationSummary();
  }

  @Get('reports/course-sections')
  @Roles('ADMIN')
  courseSectionSummary() {
    return this.portal.courseSectionSummary();
  }

  @Get('reports/registrations-by-section')
  @Roles('ADMIN')
  registrationsBySection() {
    return this.portal.registrationsBySection();
  }
}
