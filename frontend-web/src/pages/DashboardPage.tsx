import { Link } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { User, Key, BookOpen, Sparkles, Clock } from 'lucide-react';

function daysSince(dateStr: string): number {
  const then = new Date(dateStr);
  const now = new Date();
  const diff = now.getTime() - then.getTime();
  return Math.max(1, Math.floor(diff / (1000 * 60 * 60 * 24)));
}

function SoundWaveBars() {
  return (
    <div className="absolute right-6 top-1/2 -translate-y-1/2 flex items-end gap-0.5 opacity-15">
      {[6, 10, 4, 12, 5, 9, 7, 8, 4, 11, 6, 9].map((h, i) => (
        <div
          key={i}
          className="w-1 rounded-full bg-primary-600 animate-wave"
          style={{ height: `${h * 2}px`, animationDelay: `${i * 0.12}s` }}
        />
      ))}
    </div>
  );
}

export default function DashboardPage() {
  const { user } = useAuthStore();
  const displayName = user?.nickname || user?.username || '用户';
  const days = user?.createdAt ? daysSince(user.createdAt) : 1;

  const shortcuts = [
    {
      to: '/profile',
      icon: User,
      title: '编辑资料',
      desc: '修改昵称、邮箱和头像',
      color: 'bg-primary-50 text-primary-600',
    },
    {
      to: '/password',
      icon: Key,
      title: '修改密码',
      desc: '更新你的登录密码',
      color: 'bg-amber-50 text-amber-600',
    },
    {
      to: '/vocabulary',
      icon: BookOpen,
      title: '词库管理',
      desc: '管理你的个性化词库',
      color: 'bg-gray-100 text-gray-500',
      disabled: true,
    },
  ];

  return (
    <div className="animate-fade-in space-y-6">
      <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-primary-600 via-primary-700 to-deep-800 p-8 text-white">
        <SoundWaveBars />
        <div className="relative z-10">
          <div className="flex items-center gap-2 mb-3">
            <Sparkles className="h-5 w-5 text-white/80" />
            <span className="text-sm font-medium text-white/70">欢迎使用 HeecoMou</span>
          </div>
          <h1 className="font-display text-3xl sm:text-4xl">
            {new Date().getHours() < 12 ? '早上好' : new Date().getHours() < 18 ? '下午好' : '晚上好'}，{displayName}
          </h1>
          <div className="mt-4 flex items-center gap-2 text-sm text-white/60">
            <Clock className="h-4 w-4" />
            <span>加入 HeecoMou {days} 天</span>
          </div>
        </div>
      </div>

      <div>
        <h2 className="mb-4 font-display text-xl text-deep-800">快捷操作</h2>
        <div className="grid gap-4 sm:grid-cols-3">
          {shortcuts.map((item) => (
            <div key={item.to}>
              {item.disabled ? (
                <div className="card group cursor-not-allowed opacity-50">
                  <div className={`mb-4 inline-flex h-10 w-10 items-center justify-center rounded-xl ${item.color}`}>
                    <item.icon className="h-5 w-5" />
                  </div>
                  <h3 className="font-display text-lg text-deep-800">{item.title}</h3>
                  <p className="mt-1 text-sm text-gray-500">{item.desc}</p>
                  <span className="mt-3 inline-block rounded-lg bg-gray-100 px-2.5 py-1 text-xs text-gray-400">
                    即将上线
                  </span>
                </div>
              ) : (
                <Link
                  to={item.to}
                  className="card group block hover:shadow-card-hover hover:-translate-y-1 transition-all duration-200"
                >
                  <div className={`mb-4 inline-flex h-10 w-10 items-center justify-center rounded-xl ${item.color}`}>
                    <item.icon className="h-5 w-5" />
                  </div>
                  <h3 className="font-display text-lg text-deep-800">{item.title}</h3>
                  <p className="mt-1 text-sm text-gray-500">{item.desc}</p>
                  <span className="mt-3 inline-flex items-center gap-1 text-sm font-medium text-primary-600 opacity-0 group-hover:opacity-100 transition-opacity">
                    立即前往 <span className="text-base">→</span>
                  </span>
                </Link>
              )}
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <h2 className="mb-4 font-display text-lg text-deep-800">账号概览</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-xl bg-surface-muted p-4">
            <p className="text-xs font-medium text-gray-400 uppercase tracking-wider">用户名</p>
            <p className="mt-1 font-medium text-deep-800">{user?.username || '-'}</p>
          </div>
          <div className="rounded-xl bg-surface-muted p-4">
            <p className="text-xs font-medium text-gray-400 uppercase tracking-wider">昵称</p>
            <p className="mt-1 font-medium text-deep-800">{user?.nickname || '未设置'}</p>
          </div>
          <div className="rounded-xl bg-surface-muted p-4">
            <p className="text-xs font-medium text-gray-400 uppercase tracking-wider">邮箱</p>
            <p className="mt-1 font-medium text-deep-800 break-all">{user?.email || '-'}</p>
          </div>
          <div className="rounded-xl bg-surface-muted p-4">
            <p className="text-xs font-medium text-gray-400 uppercase tracking-wider">用户 ID</p>
            <p className="mt-1 font-medium text-deep-800">{user?.id ?? '-'}</p>
          </div>
        </div>
      </div>
    </div>
  );
}
