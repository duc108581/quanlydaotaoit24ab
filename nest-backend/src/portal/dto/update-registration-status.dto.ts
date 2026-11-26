import { IsIn } from 'class-validator';

export class UpdateRegistrationStatusDto {
  @IsIn(['CONFIRMED', 'CANCELLED'])
  status!: 'CONFIRMED' | 'CANCELLED';
}
