from collections import Counter
from typing import Optional


class ContextCorrector:
    def __init__(self, context_window: int = 3):
        self._context_window = context_window
        self._stats: Counter = Counter()

    def train(self, sentences: list[str]):
        for sentence in sentences:
            tokens = sentence.split()
            for i in range(len(tokens)):
                start = max(0, i - self._context_window)
                end = min(len(tokens), i + self._context_window + 1)
                for j in range(start, end):
                    if i != j:
                        pair = tokens[i] + "|" + tokens[j]
                        self._stats[pair] += 1

    def suggest(self, word: str, context: list[str]) -> Optional[str]:
        candidates: list[tuple[str, int]] = []
        for ctx_word in context:
            pair_fwd = ctx_word + "|" + word
            pair_rev = word + "|" + ctx_word
            candidates.append((ctx_word, self._stats.get(pair_fwd, 0)))
            candidates.append((ctx_word, self._stats.get(pair_rev, 0)))

        if not candidates:
            return None

        candidates.sort(key=lambda x: x[1], reverse=True)
        total = sum(c for _, c in candidates)
        if total == 0:
            return None

        return candidates[0][0]

    def get_stats_size(self) -> int:
        return len(self._stats)
