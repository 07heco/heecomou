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

export interface VocabVO {
  id: number;
  userId: number;
  word: string;
  pinyin: string | null;
  category: string | null;
  frequency: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface VocabRequest {
  word: string;
  pinyin?: string;
  category?: string;
}

export interface VocabListResponse {
  items: VocabVO[];
  total: number;
  page: number;
  size: number;
}

export interface VocabSyncRequest {
  version: number;
  limit: number;
}

export interface VocabSyncResponse {
  items: VocabVO[];
  hasMore: boolean;
  maxVersion: number;
}

export interface CorrectionRequest {
  originalText: string;
  correctedText: string;
  source: string;
}

export interface CorrectionHistory {
  id: number;
  userId: number;
  originalText: string;
  correctedText: string;
  source: string;
  createdAt: string;
}
