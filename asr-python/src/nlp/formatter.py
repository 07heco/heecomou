import re

def is_chinese_char(ch: str) -> bool:
    return "\u4e00" <= ch <= "\u9fff" or "\u3400" <= ch <= "\u4dbf"


def is_english_char(ch: str) -> bool:
    return "a" <= ch.lower() <= "z"


def is_digit_char(ch: str) -> bool:
    return "0" <= ch <= "9"


def format_cn_en_mixed(text: str) -> str:
    if not text:
        return ""

    result: list[str] = []
    for i, ch in enumerate(text):
        if ch == " ":
            continue

        if i > 0:
            prev = text[i - 1]
            prev_is_cn = is_chinese_char(prev)
            prev_is_en = is_english_char(prev) or is_digit_char(prev)
            curr_is_cn = is_chinese_char(ch)
            curr_is_en = is_english_char(ch) or is_digit_char(ch)

            if (prev_is_cn and curr_is_en) or (prev_is_en and curr_is_cn):
                result.append(" ")

        result.append(ch)

    return "".join(result).strip()


def normalize_punctuation(text: str) -> str:
    result = text

    result = result.replace("\u201c", "\u201c")
    result = result.replace("\u201d", "\u201d")
    result = result.replace("\uff0c", "\uff0c")
    result = result.replace("\u3002", "\u3002")
    result = result.replace("\uff1b", "\uff1b")

    result = result.replace(",", "\uff0c")
    result = result.replace(";", "\uff1b")
    result = result.replace(":", "\uff1a")

    result = re.sub(r"\s+", " ", result)

    result = re.sub(
        r"\s+([" + re.escape("\u201c\u201d\uff0c\u3002\uff1b\uff01") + r"])",
        r"\1",
        result,
    )
    result = re.sub(
        r"([" + re.escape("\u201c") + r"])\s+",
        r"\1",
        result,
    )

    result = result.replace(" ?", "?")
    result = result.replace(" !", "!")
    result = result.replace(" .", ".")

    return result.strip()


def format_text(text: str, language: str = "zh") -> str:
    if not text:
        return ""

    result = text.strip()

    if language == "zh":
        result = format_cn_en_mixed(result)

    result = normalize_punctuation(result)

    return result


def format_numeric(text: str) -> str:
    def _replace(m):
        return f"\u767e\u5206\u4e4b{m.group(1)}"
    result = re.sub(r"(\d+)%", _replace, text)
    return result
