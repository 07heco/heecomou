import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent.parent / "src"))

from nlp.collocation_model import CollocationModel


class TestCollocationModel:
    def test_empty_model(self):
        model = CollocationModel()
        assert model.get_stats_size() == 0

    def test_train_populates_ngrams(self):
        model = CollocationModel()
        model.train(["人工智能技术正在改变世界"])
        assert model.get_stats_size() > 0

    def test_score_bigram_exists(self):
        model = CollocationModel()
        model.train(["人工智能发展迅速", "人工智能技术"])
        score = model.score_bigram("智", "能")
        assert score > 0

    def test_score_bigram_missing(self):
        model = CollocationModel()
        model.train(["hello world"])
        score = model.score_bigram("不", "存在")
        assert score == 0.0

    def test_score_context(self):
        model = CollocationModel()
        model.train(["人工智能发展迅速", "人工智能应用广泛", "人工智能技术成熟"])
        ctx_score = model.score_context("智能", ["人工", "技术"])
        assert ctx_score >= 0

    def test_score_sequence(self):
        model = CollocationModel()
        model.train(["自然语言处理技术的发展"])
        score = model.score_sequence(["自然", "语言", "处理"])
        assert score >= 0

    def test_build_default(self):
        model = CollocationModel.build_default()
        assert model.get_stats_size() > 100

    def test_train_file(self, tmp_path):
        corpus_path = tmp_path / "corpus.txt"
        corpus_path.write_text("人工智能发展迅速\n深度学习技术进步", encoding="utf-8")
        model = CollocationModel()
        model.train_file(str(corpus_path))
        assert model.get_stats_size() > 0

    def test_export_import(self):
        model = CollocationModel()
        model.train(["人工智能技术", "机器学习算法"])
        data = model.export_dict()
        model2 = CollocationModel()
        model2.import_dict(data)
        assert model2.get_stats_size() == model.get_stats_size()

    def test_train_corpus_with_segment_func(self):
        model = CollocationModel()
        texts = ["人工智能技术正在改变世界。深度学习模型广泛应用。"]
        model.train_corpus(texts)
        assert model.get_stats_size() > 0

    def test_load_correction_history(self):
        model = CollocationModel()
        corrections = [
            {"correctedText": "人工智能技术发展迅速"},
            {"corrected_text": "分布式系统架构设计"},
        ]
        model.load_correction_history(corrections)
        assert model.get_stats_size() > 0

    def test_score_bigram_with_unigram_zero(self):
        model = CollocationModel()
        model._bigram["A|B"] = 5
        model._total_bigrams = 100
        score = model.score_bigram("A", "B")
        assert score > 0

    def test_score_context_empty(self):
        model = CollocationModel()
        model.train(["hello world"])
        score = model.score_context("hello", [])
        assert score >= 0

    def test_score_sequence_short(self):
        model = CollocationModel()
        assert model.score_sequence([]) == 0.0
        assert model.score_sequence(["a"]) == 0.0
