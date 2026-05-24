import { BookOpen, Package } from 'lucide-react';

export default function VocabularyPage() {
  return (
    <div className="animate-fade-in">
      <h1 className="mb-6 font-display text-2xl text-deep-800">词库管理</h1>

      <div className="card flex flex-col items-center py-16 text-center">
        <div className="mb-6 flex h-20 w-20 items-center justify-center rounded-2xl bg-gray-100">
          <BookOpen className="h-10 w-10 text-gray-400" />
        </div>
        <h2 className="font-display text-xl text-deep-800">词库管理功能即将上线</h2>
        <p className="mt-2 max-w-md text-sm text-gray-500">
          在 Phase 4 中，你将可以在这里添加、编辑和管理你的个性化词库，
          包括人名、地名、专业术语等自定义词汇，它们将被用于提高语音识别的准确率。
        </p>
        <div className="mt-8 flex items-center gap-2 rounded-xl bg-surface-muted px-4 py-2.5">
          <Package className="h-4 w-4 text-primary-600" />
          <span className="text-sm font-medium text-primary-600">Phase 4 · 预计上线</span>
        </div>
      </div>
    </div>
  );
}
