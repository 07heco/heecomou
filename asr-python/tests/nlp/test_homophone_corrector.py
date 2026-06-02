import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent.parent / "src"))

from nlp.collocation_model import CollocationModel
from nlp.homophone_corrector import HomophoneCorrector


class TestHomophoneCorrector:
    def setup_method(self):
        self.corrector = HomophoneCorrector()

    def test_get_homophones_empty_without_model(self):
        candidates = self.corrector.get_homophones("测试")
        assert candidates == []

    def test_add_known_words(self):
        self.corrector.add_known_word("微服务")
        assert "微服务" in self.corrector._known_words

    def test_add_known_words_list(self):
        self.corrector.add_known_words(["微服务", "分布式"])
        assert "微服务" in self.corrector._known_words
        assert "分布式" in self.corrector._known_words

    def test_correct_without_model_returns_original(self):
        tokens = ["今天", "天气", "真", "好"]
        result = self.corrector.correct(tokens)
        assert result == tokens

    def test_get_homophones_with_custom_mapping(self):
        self.corrector.add_custom_mapping("人工只能", "人工智能")
        candidates = self.corrector.get_homophones("人工只能")
        assert "人工智能" in candidates

    def test_get_homophones_does_not_return_known_word(self):
        self.corrector.add_known_words(["微服务"])
        candidates = self.corrector.get_homophones("微服务")
        assert candidates == []

    def test_no_homophones_for_single_char(self):
        model = CollocationModel.build_default()
        corrector = HomophoneCorrector(model)
        tokens = ["今", "天", "天", "气"]
        result = corrector.correct(tokens)
        assert result == ["今", "天", "天", "气"]

    def test_add_known_words_multi(self):
        self.corrector.add_known_words(["微服务", "分布式", "容器化"])
        assert len(self.corrector._known_words) == 3


class TestHomophoneCorrectorWithModel:
    def setup_method(self):
        self.model = CollocationModel.build_default()
        self.corrector = HomophoneCorrector(self.model)

    def test_correct_returns_list(self):
        tokens = ["今天", "天气", "真", "好"]
        result = self.corrector.correct(tokens)
        assert isinstance(result, list)
        assert len(result) == len(tokens)

    def test_set_collocation_model(self):
        corrector = HomophoneCorrector()
        model = CollocationModel.build_default()
        corrector.set_collocation_model(model)
        assert corrector._collocation_model is model
