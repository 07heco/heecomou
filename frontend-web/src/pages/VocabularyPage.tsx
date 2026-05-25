import { useState, useEffect, useCallback } from 'react';
import { Search, Plus, Edit2, Trash2, RefreshCw, X } from 'lucide-react';
import { vocabApi } from '@/api/client';
import type { VocabVO, VocabRequest } from '@/types/api';

interface VocabFormData {
  word: string;
  pinyin: string;
  category: string;
}

const initialForm: VocabFormData = { word: '', pinyin: '', category: '' };

export default function VocabularyPage() {
  const [items, setItems] = useState<VocabVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<VocabFormData>(initialForm);
  const [message, setMessage] = useState('');

  const fetchList = useCallback(async (p = page, kw = keyword) => {
    setLoading(true);
    try {
      const res = kw
        ? await vocabApi.search(kw, p, 20)
        : await vocabApi.list(p, 20);
      if (res.data.code === 200) {
        setItems(res.data.data.items);
        setTotal(res.data.data.total);
      }
    } catch (err) {
      setMessage('加载失败');
    } finally {
      setLoading(false);
    }
  }, [page, keyword]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  const handleSearch = () => {
    setPage(1);
    fetchList(1, keyword);
  };

  const openAdd = () => {
    setForm(initialForm);
    setEditingId(null);
    setShowForm(true);
  };

  const openEdit = (item: VocabVO) => {
    setForm({
      word: item.word,
      pinyin: item.pinyin || '',
      category: item.category || '',
    });
    setEditingId(item.id);
    setShowForm(true);
  };

  const handleSubmit = async () => {
    if (!form.word.trim()) {
      setMessage('请输入词汇');
      return;
    }
    const data: VocabRequest = {
      word: form.word.trim(),
      pinyin: form.pinyin.trim() || undefined,
      category: form.category.trim() || undefined,
    };
    try {
      if (editingId) {
        await vocabApi.update(editingId, data);
        setMessage('更新成功');
      } else {
        await vocabApi.add(data);
        setMessage('添加成功');
      }
      setShowForm(false);
      fetchList();
    } catch (err) {
      setMessage('操作失败');
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('确定删除该词汇？')) return;
    try {
      await vocabApi.delete(id);
      setMessage('删除成功');
      fetchList();
    } catch (err) {
      setMessage('删除失败');
    }
  };

  const handleSync = async () => {
    try {
      const res = await vocabApi.sync({ version: 0, limit: 500 });
      if (res.data.code === 200) {
        setMessage(`同步完成 (${res.data.data.items.length} 项)`);
        fetchList();
      }
    } catch (err) {
      setMessage('同步失败');
    }
  };

  const totalPages = Math.ceil(total / 20);

  return (
    <div className="animate-fade-in">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="font-display text-2xl text-deep-800">词库管理</h1>
        <div className="flex gap-2">
          <button onClick={handleSync} className="btn-secondary flex items-center gap-1.5">
            <RefreshCw className="h-4 w-4" /> 同步
          </button>
          <button onClick={openAdd} className="btn-primary flex items-center gap-1.5">
            <Plus className="h-4 w-4" /> 添加词汇
          </button>
        </div>
      </div>

      {message && (
        <div className="mb-4 rounded-lg border border-primary-200 bg-primary-50 px-4 py-2.5 text-sm text-primary-700">
          {message}
          <button onClick={() => setMessage('')} className="ml-3 float-right">&times;</button>
        </div>
      )}

      <div className="mb-4 flex gap-2">
        <input
          className="input flex-1"
          placeholder="搜索词汇..."
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
        />
        <button onClick={handleSearch} className="btn-secondary flex items-center gap-1.5">
          <Search className="h-4 w-4" /> 搜索
        </button>
      </div>

      <div className="card overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">加载中...</div>
        ) : items.length === 0 ? (
          <div className="p-8 text-center">
            <p className="text-gray-500">暂无词汇</p>
            <button onClick={openAdd} className="mt-3 btn-primary inline-flex items-center gap-1.5">
              <Plus className="h-4 w-4" /> 添加第一个词汇
            </button>
          </div>
        ) : (
          <>
            <table className="w-full">
              <thead>
                <tr className="border-b border-gray-100 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">
                  <th className="px-4 py-3">词汇</th>
                  <th className="px-4 py-3">拼音</th>
                  <th className="px-4 py-3">分类</th>
                  <th className="px-4 py-3">词频</th>
                  <th className="px-4 py-3 text-right">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {items.map((item) => (
                  <tr key={item.id} className="hover:bg-surface-muted transition-colors">
                    <td className="px-4 py-3">
                      <span className="font-semibold text-deep-800">{item.word}</span>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500">{item.pinyin || '-'}</td>
                    <td className="px-4 py-3">
                      {item.category ? (
                        <span className="rounded-full bg-surface-muted px-2 py-0.5 text-xs text-gray-600">
                          {item.category}
                        </span>
                      ) : (
                        <span className="text-sm text-gray-400">-</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500">{item.frequency}</td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-1">
                        <button
                          onClick={() => openEdit(item)}
                          className="rounded p-1.5 text-gray-400 hover:bg-surface-muted hover:text-primary-600"
                        >
                          <Edit2 className="h-4 w-4" />
                        </button>
                        <button
                          onClick={() => handleDelete(item.id)}
                          className="rounded p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-500"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {totalPages > 1 && (
              <div className="flex items-center justify-between border-t border-gray-100 px-4 py-3">
                <span className="text-sm text-gray-500">共 {total} 条</span>
                <div className="flex gap-1">
                  <button
                    onClick={() => setPage((p) => Math.max(1, p - 1))}
                    disabled={page <= 1}
                    className="rounded px-3 py-1.5 text-sm text-gray-600 hover:bg-surface-muted disabled:opacity-30"
                  >
                    上一页
                  </button>
                  <span className="flex items-center px-3 py-1.5 text-sm font-medium text-deep-800">
                    {page} / {totalPages}
                  </span>
                  <button
                    onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                    disabled={page >= totalPages}
                    className="rounded px-3 py-1.5 text-sm text-gray-600 hover:bg-surface-muted disabled:opacity-30"
                  >
                    下一页
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="card w-full max-w-md animate-fade-in">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="font-display text-lg text-deep-800">
                {editingId ? '编辑词汇' : '添加词汇'}
              </h2>
              <button onClick={() => setShowForm(false)} className="rounded p-1 text-gray-400 hover:text-deep-800">
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="space-y-4">
              <div>
                <label className="mb-1 block text-sm font-medium text-deep-700">词汇 *</label>
                <input
                  className="input w-full"
                  value={form.word}
                  onChange={(e) => setForm({ ...form, word: e.target.value })}
                  placeholder="例如: 微服务架构"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-deep-700">拼音</label>
                <input
                  className="input w-full"
                  value={form.pinyin}
                  onChange={(e) => setForm({ ...form, pinyin: e.target.value })}
                  placeholder="例如: wei fu wu jia gou"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-deep-700">分类</label>
                <input
                  className="input w-full"
                  value={form.category}
                  onChange={(e) => setForm({ ...form, category: e.target.value })}
                  placeholder="例如: 技术术语"
                />
              </div>
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <button onClick={() => setShowForm(false)} className="btn-secondary">取消</button>
              <button onClick={handleSubmit} className="btn-primary">
                {editingId ? '保存' : '添加'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
