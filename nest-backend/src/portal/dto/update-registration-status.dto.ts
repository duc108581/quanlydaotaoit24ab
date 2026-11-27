import { IsIn } from 'class-validator';

export class UpdateRegistrationStatusDto {
  @IsIn(['CONFIRMED', 'REJECTED'])
  status!: 'CONFIRMED' | 'REJECTED';
}
