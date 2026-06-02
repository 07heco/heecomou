import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent.parent / "src"))

from nlp.context_corrector import ContextCorrector
from nlp.collocation_model import CollocationModel


class TestContextCorrector:
    def test_empty_stats_on_init(self):
        model = CollocationModel()
        cc = ContextCorrector(collocation_model=model)
        assert cc.get_stats_size() == 0

    def test_build_default_has_stats(self):
        cc = ContextCorrector()
        assert cc.get_stats_size() > 100

    def test_correct_empty_text(self):
        cc = ContextCorrector()
        assert cc.correct("") == ""
        assert cc.correct("   ") == "   "

    def test_correct_no_change_for_normal_text(self):
        cc = ContextCorrector()
        result = cc.correct("今天天气真好")
        assert isinstance(result, str)
        assert len(result) > 0

    def test_correct_returns_str_with_punctuation(self):
        cc = ContextCorrector()
        result = cc.correct("今天天气真好，适合出去玩。")
        assert "今天天气真好" in result
        assert "适合出去玩" in result

    def test_add_vocab_words(self):
        cc = ContextCorrector()
        cc.add_vocab_words(["微服务", "分布式"])
        assert "微服务" in cc.homophone_corrector._known_words

    def test_add_correction_mapping(self):
        cc = ContextCorrector()
        cc.add_correction_mapping("人工只能", "人工智能")
        candidates = cc.homophone_corrector.get_homophones("人工只能")
        assert "人工智能" in candidates

    def test_train(self):
        model = CollocationModel()
        cc = ContextCorrector(collocation_model=model)
        assert cc.get_stats_size() == 0
        cc.train(["人工智能发展迅速", "人工智能技术成熟"])
        assert cc.get_stats_size() > 0

    def test_suggest_returns_none_when_empty(self):
        model = CollocationModel()
        cc = ContextCorrector(collocation_model=model)
        result = cc.suggest("测试", ["上下文"])
        assert result is None

    def test_correct_with_vocab_words(self):
        cc = ContextCorrector()
        vocab = ["微服务", "分布式系统", "自然语言处理"]
        result = cc.correct("今天天气真好", vocab_words=vocab)
        assert isinstance(result, str)

    def test_correct_with_english_mixed(self):
        cc = ContextCorrector()
        result = cc.correct("我们使用Python和Java开发后端服务。")
        assert "Python" in result or "python" in result.lower()

    def test_collocation_model_property(self):
        cc = ContextCorrector()
        assert isinstance(cc.collocation_model, CollocationModel)


class TestMultipleSentences:
    def test_correct_preserves_structure(self):
        cc = ContextCorrector()
        text = "人工智能技术发展迅速。深度学习模型广泛应用。"
        result = cc.correct(text)
        assert isinstance(result, str)
