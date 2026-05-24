export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface UserVO {
  id: number;
  username: string;
  nickname: string | null;
  email: string;
  phone: string | null;
  avatarUrl: string | null;
  createdAt: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  email: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  username: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface UpdateProfileRequest {
  nickname?: string;
  email?: string;
  avatarUrl?: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}
