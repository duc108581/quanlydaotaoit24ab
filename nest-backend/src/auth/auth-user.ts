export interface AuthUser {
  id: string;
  username: string;
  fullName: string;
  roles: string[];
  studentCode?: string;
}

export interface JwtClaims {
  sub?: string;
  username?: string;
  fullName?: string;
  role?: string;
  roles?: string[];
  studentCode?: string | null;
}
