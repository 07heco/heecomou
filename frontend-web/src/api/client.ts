import axios from 'axios';
import type {
  ApiResponse,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RefreshRequest,
  UserVO,
  UpdateProfileRequest,
  ChangePasswordRequest,
  VocabVO,
  VocabRequest,
  VocabListResponse,
  VocabSyncRequest,
  VocabSyncResponse,
  CorrectionRequest,
  CorrectionHistory,
} from '@/types/api';

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
});

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token!);
    }
  });
  failedQueue = [];
};

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (originalRequest.url === '/auth/refresh') {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('user');
        window.location.href = '/login';
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return api(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = localStorage.getItem('refresh_token');
      if (!refreshToken) {
        localStorage.removeItem('access_token');
        localStorage.removeItem('user');
        window.location.href = '/login';
        return Promise.reject(error);
      }

      try {
        const { data } = await axios.post<ApiResponse<LoginResponse>>(
          '/api/v1/auth/refresh',
          { refreshToken } as RefreshRequest
        );
        const { accessToken, refreshToken: newRefreshToken } = data.data;
        localStorage.setItem('access_token', accessToken);
        localStorage.setItem('refresh_token', newRefreshToken);
        processQueue(null, accessToken);
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('user');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export const authApi = {
  register: (data: RegisterRequest) =>
    api.post<ApiResponse<UserVO>>('/auth/register', data),

  login: (data: LoginRequest) =>
    api.post<ApiResponse<LoginResponse>>('/auth/login', data),

  refresh: (data: RefreshRequest) =>
    api.post<ApiResponse<LoginResponse>>('/auth/refresh', data),

  logout: () => api.post<ApiResponse<null>>('/auth/logout'),
};

export const userApi = {
  me: () => api.get<ApiResponse<UserVO>>('/user/me'),

  updateProfile: (data: UpdateProfileRequest) =>
    api.put<ApiResponse<UserVO>>('/user/profile', data),

  changePassword: (data: ChangePasswordRequest) =>
    api.put<ApiResponse<null>>('/user/password', data),
};

export const vocabApi = {
  list: (page = 1, size = 20) =>
    api.get<ApiResponse<VocabListResponse>>('/vocabulary', { params: { page, size } }),

  search: (keyword: string, page = 1, size = 20) =>
    api.get<ApiResponse<VocabListResponse>>('/vocabulary/search', {
      params: { keyword, page, size },
    }),

  getById: (id: number) =>
    api.get<ApiResponse<VocabVO>>(`/vocabulary/${id}`),

  add: (data: VocabRequest) =>
    api.post<ApiResponse<VocabVO>>('/vocabulary', data),

  update: (id: number, data: VocabRequest) =>
    api.put<ApiResponse<VocabVO>>(`/vocabulary/${id}`, data),

  delete: (id: number) =>
    api.delete<ApiResponse<null>>(`/vocabulary/${id}`),

  sync: (data: VocabSyncRequest) =>
    api.post<ApiResponse<VocabSyncResponse>>('/vocabulary/sync', data),
};

export const correctionApi = {
  list: (limit = 20) =>
    api.get<ApiResponse<CorrectionHistory[]>>('/corrections', {
      params: { limit },
    }),

  submit: (data: CorrectionRequest) =>
    api.post<ApiResponse<CorrectionHistory>>('/corrections', data),
};

export default api;
