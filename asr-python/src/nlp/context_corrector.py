import re
import logging
from collections import Counter
from typing import Optional

from .collocation_model import CollocationModel
from .homophone_corrector import HomophoneCorrector

logger = logging.getLogger(__name__)

_TOKEN_PATTERN = re.compile(r"([\u4e00-\u9fff])|([^\u4e00-\u9fff]+)")


class ContextCorrector:
    """NLP 后处理上下文纠错管线。

    整合 CollocationModel（n-gram 搭配评分）和 HomophoneCorrector（同音词纠正），
    对 Whisper 识别文本进行多级纠正：

    1. Tokenize — 将文本拆分为 token 序列（中文字符 + 非中文块）
    2. Homophone — 对每个中文 token 检测是否可能是专有名词/术语的同音误识别，
       利用 n-gram 搭配模型评分决定是否替换
    3. Assemble — 将修正后的 token 序列拼接回完整文本

    Usage:
        model = CollocationModel.build_default()
        corrector = ContextCorrector(model)
        corrected = corrector.correct("今天学习了深度血洗和自然语言处理")
        # → "今天学习了深度学习和自然语言处理"
    """

    def __init__(
        self,
        collocation_model: Optional[CollocationModel] = None,
        context_window: int = 3,
        threshold_ratio: float = 1.5,
    ):
        self._context_window = context_window
        self._threshold_ratio = threshold_ratio
        self._collocation_model = collocation_model or CollocationModel.build_default()
        self._homophone_corrector = HomophoneCorrector(self._collocation_model)
        self._stats: Counter = Counter()

    @property
    def collocation_model(self) -> CollocationModel:
        return self._collocation_model

    @property
    def homophone_corrector(self) -> HomophoneCorrector:
        return self._homophone_corrector

    def add_vocab_words(self, words: list[str]):
        self._homophone_corrector.add_known_words(words)

    def add_correction_mapping(self, wrong: str, correct: str):
        self._homophone_corrector.add_custom_mapping(wrong, correct)

    def train(self, sentences: list[str]):
        self._collocation_model.train(sentences)

    def train_file(self, path: str):
        self._collocation_model.train_file(path)

    def load_correction_history(self, corrections: list[dict]):
        self._collocation_model.load_correction_history(corrections)

    def correct(self, text: str, vocab_words: Optional[list[str]] = None) -> str:
        if not text or not text.strip():
            return text

        if vocab_words:
            self._homophone_corrector.add_known_words(vocab_words)

        tokens, is_chinese = self._tokenize(text)
        if not tokens:
            return text

        chinese_tokens = [t for t, c in zip(tokens, is_chinese) if c]
        chinese_indices = [i for i, c in enumerate(is_chinese) if c]

        corrected_chinese = self._homophone_corrector.correct(
            chinese_tokens,
            context_window=self._context_window,
            threshold_ratio=self._threshold_ratio,
        )

        for idx, new_val in zip(chinese_indices, corrected_chinese):
            tokens[idx] = new_val

        return "".join(tokens)

    def suggest(self, word: str, context: list[str]) -> Optional[str]:
        candidates = self._homophone_corrector.get_homophones(word)
        if not candidates:
            return None
        original_score = self._collocation_model.score_context(word, context)
        best_candidate = None
        best_score = original_score * self._threshold_ratio
        for candidate in candidates:
            score = self._collocation_model.score_context(candidate, context)
            if score > best_score:
                best_score = score
                best_candidate = candidate
        return best_candidate

    def get_stats_size(self) -> int:
        return self._collocation_model.get_stats_size()

    def _tokenize(self, text: str) -> tuple[list[str], list[bool]]:
        tokens = []
        is_chinese = []
        for m in _TOKEN_PATTERN.finditer(text):
            if m.group(1):
                tokens.append(m.group(1))
                is_chinese.append(True)
            elif m.group(2):
                tokens.append(m.group(2))
                is_chinese.append(False)
        return tokens, is_chinese
