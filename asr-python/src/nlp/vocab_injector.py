import time
import logging
from collections import OrderedDict
from typing import Optional

logger = logging.getLogger(__name__)

COMMON_ERROR_PATTERNS = {
    "人工只能": "人工智能",
    "机器血洗": "机器学习",
    "深度血洗": "深度学习",
    "自燃语言": "自然语言",
    "为服务": "微服务",
    "分布试": "分布式",
    "荣誉器": "容器",
    "荣器化": "容器化",
    "微服四": "微服务",
}


class VocabInjector:

    def __init__(self, backend_url: Optional[str] = None, service_token: Optional[str] = None):
        self._backend_url = backend_url
        self._service_token = service_token
        self._cache: OrderedDict = OrderedDict()
        self._max_cache_size = 100
        self._cache_ttl = 300
        self._http_client = None

    def _get_http_client(self):
        if self._http_client is None:
            import httpx
            self._http_client = httpx.Client(timeout=httpx.Timeout(10.0))
        return self._http_client

    def inject(self, text: str, vocab_words: list[str], user_id: Optional[int] = None) -> str:
        if not text or not text.strip():
            return text

        if not vocab_words and user_id is not None and self._backend_url:
            vocab_words = self._get_vocab(user_id)

        result = text
        result = self._correct_common_errors(result)

        if vocab_words:
            result = self._apply_vocab_boost(result, vocab_words)

        return result

    def _correct_common_errors(self, text: str) -> str:
        result = text
        for wrong, correct in COMMON_ERROR_PATTERNS.items():
            result = result.replace(wrong, correct)
        return result

    def _apply_vocab_boost(self, text: str, vocab_words: list[str]) -> str:
        sorted_words = sorted(
            set(vocab_words),
            key=lambda w: len(w),
            reverse=True,
        )

        for word in sorted_words:
            if len(word) < 2:
                continue

            pinyin_variants = self._generate_pinyin_variants(word)
            for variant in pinyin_variants:
                if variant != word and len(variant) == len(word):
                    pos = text.find(variant)
                    if pos >= 0:
                        logger.info("VocabInjector: replaced '%s' → '%s'", variant, word)
                        text = text[:pos] + word + text[pos + len(variant):]
                        break

        return text

    def _generate_pinyin_variants(self, word: str) -> list[str]:
        variants = []
        try:
            import pypinyin
            pinyin_list = pypinyin.lazy_pinyin(word, style=pypinyin.Style.TONE3)
            pinyin_flat = "".join(pinyin_list)
            variants.append(pinyin_flat)

            pinyin_no_tone = pypinyin.lazy_pinyin(word)
            pinyin_no_tone_flat = "".join(pinyin_no_tone)
            variants.append(pinyin_no_tone_flat)

            for i in range(len(pinyin_list) - 1):
                swapped = list(pinyin_list)
                swapped[i], swapped[i + 1] = swapped[i + 1], swapped[i]
                variants.append("".join(swapped))
        except ImportError:
            pass
        return variants

    def _get_vocab(self, user_id: int) -> list[str]:
        cache_key = str(user_id)
        cached = self._cache.get(cache_key)
        if cached is not None:
            words, timestamp = cached
            if time.time() - timestamp < self._cache_ttl:
                return words
            del self._cache[cache_key]

        if not self._backend_url:
            return []

        try:
            client = self._get_http_client()
            headers = {"Content-Type": "application/json"}
            if self._service_token:
                headers["Authorization"] = f"Bearer {self._service_token}"

            url = f"{self._backend_url}/api/v1/vocabulary?page=1&size=500"
            resp = client.get(url, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                items = data.get("data", {}).get("items", [])
                if not items:
                    items = data.get("items", [])
                words = [item["word"] for item in items if item.get("word")]
                self._cache[cache_key] = (words, time.time())
                self._evict_if_needed()
                logger.info("VocabInjector: loaded %d words for user %d", len(words), user_id)
                return words
            else:
                logger.warning("VocabInjector: backend returned %d for user %d", resp.status_code, user_id)
        except Exception as e:
            logger.warning("VocabInjector: failed to fetch vocab for user %d: %s", user_id, e)

        return []

    def _evict_if_needed(self):
        while len(self._cache) > self._max_cache_size:
            self._cache.popitem(last=False)

    def preload(self, user_id: int, vocab_words: list[str]):
        cache_key = str(user_id)
        self._cache[cache_key] = (list(vocab_words), time.time())
        self._evict_if_needed()

    def clear_cache(self, user_id: Optional[int] = None):
        if user_id is not None:
            self._cache.pop(str(user_id), None)
        else:
            self._cache.clear()

    def get_cache_size(self) -> int:
        return len(self._cache)
