import { create } from 'zustand';
import { authApi, userApi } from '@/api/client';
import type { UserVO, LoginRequest, RegisterRequest, UpdateProfileRequest } from '@/types/api';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserVO | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  login: (req: LoginRequest) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  fetchUser: () => Promise<void>;
  updateProfile: (req: UpdateProfileRequest) => Promise<void>;
  initAuth: () => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  refreshToken: null,
  user: null,
  isAuthenticated: false,
  isLoading: true,

  login: async (req: LoginRequest) => {
    const { data } = await authApi.login(req);
    const { accessToken, refreshToken } = data.data;
    localStorage.setItem('access_token', accessToken);
    localStorage.setItem('refresh_token', refreshToken);

    const userRes = await userApi.me();
    const user = userRes.data.data;
    localStorage.setItem('user', JSON.stringify(user));

    set({
      accessToken,
      refreshToken,
      user,
      isAuthenticated: true,
      isLoading: false,
    });
  },

  register: async (req: RegisterRequest) => {
    await authApi.register(req);
  },

  logout: async () => {
    try {
      await authApi.logout();
    } catch {
      // ignore
    }
    get().clearAuth();
  },

  fetchUser: async () => {
    try {
      const { data } = await userApi.me();
      const user = data.data;
      localStorage.setItem('user', JSON.stringify(user));
      set({ user, isAuthenticated: true, isLoading: false });
    } catch {
      get().clearAuth();
      set({ isLoading: false });
    }
  },

  updateProfile: async (req: UpdateProfileRequest) => {
    const { data } = await userApi.updateProfile(req);
    const user = data.data;
    localStorage.setItem('user', JSON.stringify(user));
    set({ user });
  },

  initAuth: () => {
    const accessToken = localStorage.getItem('access_token');
    const refreshToken = localStorage.getItem('refresh_token');
    const cached = localStorage.getItem('user');

    if (accessToken && cached) {
      try {
        const user = JSON.parse(cached) as UserVO;
        set({ accessToken, refreshToken, user, isAuthenticated: true, isLoading: false });
        return;
      } catch {
        // invalid cached user
      }
    }
    set({ isLoading: false });
  },

  clearAuth: () => {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('user');
    set({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      isLoading: false,
    });
  },
}));
