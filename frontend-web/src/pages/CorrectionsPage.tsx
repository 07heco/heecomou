import { useState, useEffect, useCallback } from 'react';
import { correctionApi } from '@/api/client';
import type { CorrectionHistory } from '@/types/api';

export default function CorrectionsPage() {
  const [items, setItems] = useState<CorrectionHistory[]>([]);
  const [limit, setLimit] = useState(20);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await correctionApi.list(limit);
      if (res.data.code === 200) {
        setItems(res.data.data);
      }
    } catch {
      setMessage('加载纠错历史失败');
    } finally {
      setLoading(false);
    }
  }, [limit]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  return (
    <div className="animate-fade-in">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="font-display text-2xl text-deep-800">纠错历史</h1>
        <select
          className="input w-32"
          value={limit}
          onChange={(e) => setLimit(Number(e.target.value))}
        >
          <option value="10">10 条</option>
          <option value="20">20 条</option>
          <option value="50">50 条</option>
          <option value="100">100 条</option>
        </select>
      </div>

      {message && (
        <div className="mb-4 rounded-lg border border-primary-200 bg-primary-50 px-4 py-2.5 text-sm text-primary-700">
          {message}
          <button onClick={() => setMessage('')} className="ml-3 float-right">&times;</button>
        </div>
      )}

      <div className="card overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">加载中...</div>
        ) : items.length === 0 ? (
          <div className="p-8 text-center">
            <p className="text-gray-500">暂无纠错记录</p>
            <p className="mt-1 text-sm text-gray-400">
              当语音识别结果被用户手工纠正后，纠错记录会显示在这里
            </p>
          </div>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-100 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">
                <th className="px-4 py-3">原始文本</th>
                <th className="px-4 py-3">纠正文本</th>
                <th className="px-4 py-3">来源</th>
                <th className="px-4 py-3 text-right">时间</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {items.map((item) => (
                <tr key={item.id} className="hover:bg-surface-muted transition-colors">
                  <td className="px-4 py-3">
                    <span className="text-sm text-gray-600 line-through">{item.originalText}</span>
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-sm font-medium text-primary-700">{item.correctedText}</span>
                  </td>
                  <td className="px-4 py-3">
                    <span className="rounded-full bg-surface-muted px-2 py-0.5 text-xs text-gray-600">
                      {item.source}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right text-sm text-gray-500">
                    {new Date(item.createdAt).toLocaleString('zh-CN')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
