import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { Mic, User, Mail, Lock, Eye, EyeOff, Loader2 } from 'lucide-react';

function SoundWaveDecoration() {
  return (
    <div className="flex items-end justify-center gap-1 h-10">
      {[4, 8, 6, 10, 5, 9, 7, 6, 8, 4].map((h, i) => (
        <div
          key={i}
          className="w-1 rounded-full bg-white/20 animate-wave"
          style={{ height: `${h * 3}px`, animationDelay: `${i * 0.15}s` }}
        />
      ))}
    </div>
  );
}

export default function RegisterPage() {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const { register } = useAuthStore();
  const navigate = useNavigate();

  const validate = (): string | null => {
    if (!username.trim()) return '请输入用户名';
    if (username.trim().length < 3) return '用户名至少 3 个字符';
    if (username.trim().length > 20) return '用户名最多 20 个字符';
    if (!/^[a-zA-Z0-9_]+$/.test(username.trim())) return '用户名只能包含字母、数字和下划线';
    if (!email.trim()) return '请输入邮箱';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) return '请输入有效的邮箱地址';
    if (!password) return '请输入密码';
    if (password.length < 6) return '密码至少 6 个字符';
    if (password.length > 30) return '密码最多 30 个字符';
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setLoading(true);
    try {
      await register({ username: username.trim(), email: email.trim(), password });
      navigate('/');
    } catch (err: any) {
      const status = err.response?.status;
      const msg = err.response?.data?.message;
      if (status === 429) {
        setError('操作过于频繁，请稍后再试');
      } else if (status === 409) {
        setError('用户名或邮箱已存在');
      } else if (status === 400) {
        setError(msg || '请求参数有误，请检查输入');
      } else if (msg) {
        setError(msg);
      } else {
        setError('注册失败，请检查网络连接');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-br from-deep-900 via-deep-800 to-deep-700 bg-grid" />
      <div className="absolute top-1/4 left-1/4 h-64 w-64 rounded-full bg-primary-600/10 blur-3xl" />
      <div className="absolute bottom-1/4 right-1/4 h-72 w-72 rounded-full bg-accent-500/10 blur-3xl" />

      <div className="relative z-10 w-full max-w-[420px] px-6 animate-fade-in">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-white/10 backdrop-blur">
            <Mic className="h-7 w-7 text-white" />
          </div>
          <h1 className="font-display text-3xl text-white">创建账号</h1>
          <p className="mt-2 text-sm text-white/60">加入 HeecoMou，开启智能语音体验</p>
        </div>

        <div className="mb-8">
          <SoundWaveDecoration />
        </div>

        <div className="card">
          {error && (
            <div className="mb-5 flex items-center gap-2 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600">
              <div className="h-1.5 w-1.5 rounded-full bg-red-500" />
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label htmlFor="username" className="mb-1.5 block text-sm font-medium text-deep-700">
                用户名
              </label>
              <div className="relative">
                <User className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
                <input
                  id="username"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="3-20位字母、数字或下划线"
                  className="input-field pl-10"
                  autoComplete="username"
                />
              </div>
            </div>

            <div>
              <label htmlFor="email" className="mb-1.5 block text-sm font-medium text-deep-700">
                邮箱
              </label>
              <div className="relative">
                <Mail className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
                <input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="example@email.com"
                  className="input-field pl-10"
                  autoComplete="email"
                />
              </div>
            </div>

            <div>
              <label htmlFor="password" className="mb-1.5 block text-sm font-medium text-deep-700">
                密码
              </label>
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="6-30位密码"
                  className="input-field pl-10 pr-10"
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <button type="submit" disabled={loading} className="btn-primary w-full">
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  注册中...
                </span>
              ) : (
                '注  册'
              )}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-gray-500">
            已有账号？{' '}
            <Link to="/login" className="font-medium text-accent-500 hover:text-accent-600 transition-colors">
              去登录
            </Link>
          </p>
        </div>

        <p className="mt-6 text-center text-xs text-white/40">
          HeecoMou 智能语音输入法 — 让表达更自由
        </p>
      </div>
    </div>
  );
}
