"""Rule-based Chinese punctuation restoration."""

import re
import logging

logger = logging.getLogger(__name__)

QUESTION_KEYWORDS = {
    "什么", "怎么", "为什么", "吗", "呢", "哪", "谁",
    "如何", "多少", "几", "干嘛", "能否", "可否",
}

EXCLAMATION_KEYWORDS = {
    "太棒了", "太好了", "真棒", "太好了", "加油",
    "注意", "小心", "快", "赶紧", "马上",
}

COMMA_PATTERNS = [
    (r"(但是|可是|不过|然而|虽然|因为|所以|如果|而且|然后|接着|另外)"
     r"(?=\S)", r"\1，"),
    (r"(\S)(但是|可是|不过|然而|虽然|因为|所以|如果|而且|然后|接着|另外)"
     r"(?=\S)", r"\1，\2"),
    (r"(首先|其次|最后|第一|第二|第三|另外|此外|总之|综上)"
     r"(?=\S)", r"\1，"),
    (r"(\S{2,})(对吧|好吧|行吧|可以吧|好吗)", r"\1，\2"),
]

ENGLISH_SENTENCE_BOUNDARY = re.compile(
    r"([a-zA-Z])([A-Z])"
)

PUNCT_SET = frozenset("。，！？、；：…—\"\"''（）")


def restore_punctuation(
    text: str,
    language: str = "zh",
    add_comma: bool = True,
    add_period: bool = True,
) -> str:
    if not text or not text.strip():
        return ""

    text = text.strip()

    if language == "zh":
        text = _apply_comma_patterns(text) if add_comma else text
        text = _add_sentence_marks(text, add_period)
        text = _deduplicate_punctuation(text)
        text = _fix_spacing_around_punctuation(text)

    return text


def _apply_comma_patterns(text: str) -> str:
    for pattern, repl in COMMA_PATTERNS:
        text = re.sub(pattern, repl, text)
    return text


def _add_sentence_marks(text: str, add_period: bool) -> str:
    if not text:
        return text

    text = _add_question_marks(text)
    text = _add_exclamation_marks(text)

    if add_period and not _ends_with_punctuation(text):
        text = text + "。"

    return text


def _add_question_marks(text: str) -> str:
    has_keyword = any(kw in text for kw in QUESTION_KEYWORDS)

    if has_keyword and not text.endswith("？") and not text.endswith("?"):
        if "吗" in text or "呢" in text or "吧" in text:
            text = text.rstrip("。，！?") + "？"

    return text


def _add_exclamation_marks(text: str) -> str:
    has_keyword = any(kw in text for kw in EXCLAMATION_KEYWORDS)

    if has_keyword and not text.endswith("！") and not text.endswith("!"):
        if text.endswith("。"):
            text = text[:-1] + "！"
        elif not _ends_with_punctuation(text):
            text = text + "！"

    return text


def _ends_with_punctuation(text: str) -> bool:
    if not text:
        return False
    return text[-1] in PUNCT_SET


def _deduplicate_punctuation(text: str) -> str:
    return re.sub(r"([。，！？、；：])\1+", r"\1", text)


def _fix_spacing_around_punctuation(text: str) -> str:
    text = re.sub(r"\s+([。，！？、；：…])", r"\1", text)
    text = re.sub(r"([。，！？、])[，。！？、]+$", r"\1", text)
    return text


def segment_sentences(text: str) -> list[str]:
    if not text:
        return []

    parts = re.split(r"([。！？\n])", text)
    sentences = []

    for i in range(0, len(parts) - 1, 2):
        sent = parts[i].strip()
        punct = parts[i + 1] if i + 1 < len(parts) else ""
        if sent:
            sentences.append(sent + punct)

    if len(parts) % 2 == 1 and parts[-1].strip():
        sentences.append(parts[-1].strip())

    return sentences
