from nlp.vocab_injector import VocabInjector, COMMON_ERROR_PATTERNS


class TestVocabInjector:

    def setup_method(self):
        self.injector = VocabInjector()

    def test_empty_text(self):
        assert self.injector.inject("", ["微服务"]) == ""
        assert self.injector.inject("   ", ["微服务"]) == "   "

    def test_common_error_correction(self):
        assert self.injector.inject("人工只能", []) == "人工智能"
        assert self.injector.inject("机器血洗", []) == "机器学习"
        assert self.injector.inject("深度血洗", []) == "深度学习"
        assert self.injector.inject("自燃语言", []) == "自然语言"

    def test_no_vocab_no_change(self):
        text = "今天天气真好"
        assert self.injector.inject(text, []) == text

    def test_inject_with_vocab_words(self):
        vocab = ["微服务架构", "分布式系统", "容器化"]
        text = "今天天气真好"
        assert self.injector.inject(text, vocab) == text

    def test_longest_match_first(self):
        vocab = ["微服务", "微服务架构"]
        injector = VocabInjector()
        text = "我们使用微服务架构"
        result = injector.inject(text, vocab)
        assert "微服务架构" in result

    def test_common_error_with_vocab(self):
        vocab = ["微服务"]
        text = "为服务架构很流行"
        result = self.injector.inject(text, vocab)
        assert "微服务" in result

    def test_cache_eviction(self):
        injector = VocabInjector()
        for i in range(105):
            injector.preload(user_id=i, vocab_words=[f"word_{i}"])
        assert injector.get_cache_size() <= injector._max_cache_size

    def test_clear_cache(self):
        injector = VocabInjector()
        injector.preload(user_id=1, vocab_words=["test"])
        assert injector.get_cache_size() == 1
        injector.clear_cache(user_id=1)
        assert injector.get_cache_size() == 0

    def test_clear_all_cache(self):
        injector = VocabInjector()
        injector.preload(user_id=1, vocab_words=["a"])
        injector.preload(user_id=2, vocab_words=["b"])
        injector.clear_cache()
        assert injector.get_cache_size() == 0

    def test_preload_and_retrieve(self):
        injector = VocabInjector()
        injector.preload(user_id=42, vocab_words=["微服务", "分布式"])
        assert injector.get_cache_size() == 1

    def test_inject_with_user_id_no_backend(self):
        injector = VocabInjector()
        injector.preload(user_id=1, vocab_words=["容器化"])
        text = "容器化技术"
        result = injector.inject(text, vocab_words=[], user_id=1)
        assert "容器化" in result
