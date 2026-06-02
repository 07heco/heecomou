import logging
from collections import defaultdict
from typing import Optional

logger = logging.getLogger(__name__)


class HomophoneCorrector:
    """基于 pypinyin 的同音/近音词候选生成与上下文搭配评分纠正。

    通过对每个识别词生成同音/近音候选，再利用 CollocationModel 的上下文搭配评分
    来判断是否需要替换为更合理的候选词，从而纠正 Whisper 对专有名词和领域术语的
    同音字识别错误。
    """

    def __init__(self, collocation_model=None):
        self._collocation_model = collocation_model
        self._custom_mappings: dict[str, str] = {}
        self._known_words: set = set()

    def set_collocation_model(self, model):
        self._collocation_model = model

    def add_known_word(self, word: str):
        self._known_words.add(word)

    def add_known_words(self, words: list[str]):
        for w in words:
            self._known_words.add(w)

    def add_custom_mapping(self, wrong: str, correct: str):
        self._custom_mappings[wrong] = correct

    def get_homophones(self, word: str, max_candidates: int = 20) -> list[str]:
        if word in self._custom_mappings:
            return [self._custom_mappings[word]]

        if word in self._known_words:
            return []

        candidates = []
        pinyin_str = self._get_pinyin(word)
        if not pinyin_str:
            return []

        seen = {word}

        for known in self._known_words:
            if known in seen:
                continue
            known_pinyin = self._get_pinyin(known)
            if known_pinyin and self._pinyin_similar(pinyin_str, known_pinyin):
                candidates.append(known)
                seen.add(known)
                if len(candidates) >= max_candidates:
                    break

        return candidates

    def correct(
        self,
        tokens: list[str],
        context_window: int = 2,
        threshold_ratio: float = 1.5,
    ) -> list[str]:
        if self._collocation_model is None:
            return list(tokens)

        result = list(tokens)
        for i, token in enumerate(tokens):
            if len(token) <= 1:
                continue

            candidates = self.get_homophones(token)
            if not candidates:
                continue

            start = max(0, i - context_window)
            end = min(len(tokens), i + context_window + 1)
            context = tokens[start:i] + tokens[i + 1:end]

            original_score = self._collocation_model.score_context(token, context)

            best_candidate = None
            best_score = original_score * threshold_ratio

            for candidate in candidates:
                score = self._collocation_model.score_context(candidate, context)
                if score > best_score:
                    best_score = score
                    best_candidate = candidate

            if best_candidate is not None:
                logger.info(
                    "HomophoneCorrector: '%s' → '%s' (score %.4f → %.4f)",
                    token, best_candidate, original_score, best_score,
                )
                result[i] = best_candidate

        return result

    def _get_pinyin(self, word: str) -> Optional[str]:
        try:
            import pypinyin
            return "".join(pypinyin.lazy_pinyin(word))
        except ImportError:
            return None

    def _pinyin_similar(self, p1: str, p2: str) -> bool:
        if p1 == p2:
            return True
        if len(p1) != len(p2):
            return False
        if len(p1) <= 3:
            return p1 == p2
        diff_count = sum(1 for a, b in zip(p1, p2) if a != b)
        return diff_count <= 2
