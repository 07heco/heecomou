import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { Mail, Camera, User, Clock, Key, Loader2, Check } from 'lucide-react';
import Toast from '@/components/Toast';

export default function ProfilePage() {
  const { user, updateProfile } = useAuthStore();

  const [nickname, setNickname] = useState(user?.nickname || '');
  const [email, setEmail] = useState(user?.email || '');
  const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl || '');
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const handleSave = async () => {
    if (nickname.length > 50) {
      setToast({ message: '昵称最多 50 个字符', type: 'error' });
      return;
    }
    if (avatarUrl.length > 500) {
      setToast({ message: '头像URL最多 500 个字符', type: 'error' });
      return;
    }
    setSaving(true);
    try {
      await updateProfile({
        nickname: nickname.trim() || undefined,
        email: email.trim() || undefined,
        avatarUrl: avatarUrl.trim() || undefined,
      });
      setToast({ message: '资料已更新', type: 'success' });
    } catch (err: any) {
      const msg = err.response?.data?.message || '更新失败';
      setToast({ message: msg, type: 'error' });
    } finally {
      setSaving(false);
    }
  };

  const displayName = nickname || user?.username || '?';

  return (
    <div className="animate-fade-in">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <h1 className="mb-6 font-display text-2xl text-deep-800">个人资料</h1>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="card flex flex-col items-center text-center lg:col-span-1">
          <div className="flex h-24 w-24 items-center justify-center rounded-full bg-gradient-to-br from-primary-400 to-primary-600 text-3xl font-bold text-white shadow-lg">
            {avatarUrl ? (
              <img src={avatarUrl} alt="" className="h-full w-full rounded-full object-cover" />
            ) : (
              displayName.charAt(0).toUpperCase()
            )}
          </div>
          <h2 className="mt-4 font-display text-lg text-deep-800">{displayName}</h2>
          <p className="text-sm text-gray-500">@{user?.username}</p>

          <div className="mt-6 w-full space-y-3">
            <div className="flex items-center gap-2 rounded-xl bg-surface-muted px-4 py-2.5 text-sm text-deep-600">
              <User className="h-4 w-4 text-gray-400" />
              <span className="text-gray-400">用户名：</span>
              <span className="font-medium">{user?.username}</span>
            </div>
            <div className="flex items-center gap-2 rounded-xl bg-surface-muted px-4 py-2.5 text-sm text-deep-600">
              <Clock className="h-4 w-4 text-gray-400" />
              <span className="text-gray-400">注册于：</span>
              <span className="font-medium">
                {user?.createdAt ? new Date(user.createdAt).toLocaleDateString('zh-CN') : '-'}
              </span>
            </div>
          </div>
        </div>

        <div className="card space-y-6 lg:col-span-2">
          <div>
            <h3 className="mb-5 font-display text-lg text-deep-800">编辑资料</h3>
            <div className="space-y-5">
              <div>
                <label htmlFor="nickname" className="mb-1.5 block text-sm font-medium text-deep-700">
                  昵称
                </label>
                <input
                  id="nickname"
                  type="text"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  placeholder="设置一个昵称"
                  maxLength={50}
                  className="input-field"
                />
                <p className="mt-1 text-xs text-gray-400">{nickname.length}/50</p>
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
                    placeholder="你的邮箱地址"
                    className="input-field pl-10"
                  />
                </div>
              </div>

              <div>
                <label htmlFor="avatar" className="mb-1.5 block text-sm font-medium text-deep-700">
                  头像 URL
                </label>
                <div className="relative">
                  <Camera className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
                  <input
                    id="avatar"
                    type="url"
                    value={avatarUrl}
                    onChange={(e) => setAvatarUrl(e.target.value)}
                    placeholder="https://example.com/avatar.jpg"
                    maxLength={500}
                    className="input-field pl-10"
                  />
                </div>
                <p className="mt-1 text-xs text-gray-400">留空使用默认字母头像</p>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-4 pt-2">
            <button onClick={handleSave} disabled={saving} className="btn-primary">
              {saving ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  保存中...
                </span>
              ) : (
                <span className="flex items-center gap-2">
                  <Check className="h-4 w-4" />
                  保存
                </span>
              )}
            </button>
            <Link
              to="/password"
              className="flex items-center gap-2 rounded-xl border border-surface-border px-4 py-3 text-sm font-medium text-deep-600 hover:bg-surface-muted transition-colors"
            >
              <Key className="h-4 w-4" />
              修改密码
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
