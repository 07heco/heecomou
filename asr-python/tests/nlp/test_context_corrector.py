import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent.parent / "src"))

from nlp.context_corrector import ContextCorrector


class TestContextCorrector:
    def test_empty_stats(self):
        cc = ContextCorrector()
        assert cc.get_stats_size() == 0

    def test_train_populates_stats(self):
        cc = ContextCorrector()
        cc.train(["人工智能 是 未来", "人工智能 改变 世界"])
        assert cc.get_stats_size() > 0

    def test_suggest_returns_none_when_no_stats(self):
        cc = ContextCorrector()
        result = cc.suggest("测试", ["上下文"])
        assert result is None

    def test_train_and_suggest(self):
        cc = ContextCorrector()
        cc.train(["人工智能 发展 迅速", "人工智能 应用 广泛", "人工智能 技术"])
        result = cc.suggest("人工智能", ["技术"])
        assert result is not None

    def test_context_window_respected(self):
        cc = ContextCorrector(context_window=1)
        cc.train(["a b c d e"])
        result = cc.suggest("c", ["b"])
        assert result is not None
        result_far = cc.suggest("c", ["a"])
        assert result_far is None

    def test_suggest_with_empty_context(self):
        cc = ContextCorrector()
        cc.train(["hello world"])
        result = cc.suggest("hello", [])
        assert result is None


class TestMultipleSentences:
    def test_frequent_pair_ranks_higher(self):
        cc = ContextCorrector()
        for _ in range(5):
            cc.train(["人工智能 发展"])
        cc.train(["人工智能 落后"])

        result = cc.suggest("人工智能", ["发展"])
        assert result == "发展"
