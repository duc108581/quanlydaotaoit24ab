import { IsUUID } from 'class-validator';

export class CreateRegistrationDto {
  @IsUUID()
  courseSectionId!: string;
}
