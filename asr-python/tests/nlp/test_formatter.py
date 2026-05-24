import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent.parent / "src"))

from nlp.formatter import (
    format_cn_en_mixed,
    normalize_punctuation,
    format_text,
    format_numeric,
    is_chinese_char,
    is_english_char,
)


class TestIsChineseChar:
    def test_chinese_characters(self):
        assert is_chinese_char("\u4e2d")
        assert is_chinese_char("\u56fd")
        assert is_chinese_char("\u4e00")
        assert is_chinese_char("\u4e16")

    def test_non_chinese(self):
        assert not is_chinese_char("a")
        assert not is_chinese_char("1")
        assert not is_chinese_char("!")
        assert not is_chinese_char(" ")


class TestIsEnglishChar:
    def test_english_letters(self):
        assert is_english_char("a")
        assert is_english_char("Z")
        assert is_english_char("m")

    def test_non_english(self):
        assert not is_english_char("1")
        assert not is_english_char("!")
        assert not is_english_char("\u4e2d")


class TestFormatCnEnMixed:
    def test_adds_space_between_cn_and_en(self):
        result = format_cn_en_mixed("\u6211\u7528Python")
        assert result == "\u6211\u7528 Python"

    def test_adds_space_between_en_and_cn(self):
        result = format_cn_en_mixed("Python\u5f00\u53d1")
        assert result == "Python \u5f00\u53d1"

    def test_adds_space_between_cn_and_digit(self):
        result = format_cn_en_mixed("\u7b2c3\u4e2a")
        assert result == "\u7b2c 3 \u4e2a"

    def test_no_extra_space_between_cn(self):
        result = format_cn_en_mixed("\u5927\u5b66\u751f")
        assert result == "\u5927\u5b66\u751f"

    def test_empty_string(self):
        assert format_cn_en_mixed("") == ""

    def test_all_chinese(self):
        result = format_cn_en_mixed("\u4f60\u597d\u4e16\u754c")
        assert result == "\u4f60\u597d\u4e16\u754c"

    def test_all_english(self):
        result = format_cn_en_mixed("Hello World")
        assert result == "HelloWorld"

    def test_strips_outer_spaces(self):
        result = format_cn_en_mixed("  Hello  ")
        assert result == "Hello"


class TestNormalizePunctuation:
    def test_english_comma_to_chinese(self):
        result = normalize_punctuation("\u4f60\u597d, \u4e16\u754c")
        assert "\uff0c" in result

    def test_strips_spaces_around_punctuation(self):
        result = normalize_punctuation("\u4f60\u597d \uff0c \u4e16\u754c")
        assert result == "\u4f60\u597d\uff0c \u4e16\u754c"

    def test_empty_string(self):
        assert normalize_punctuation("") == ""


class TestFormatText:
    def test_format_mixed_cn_en(self):
        result = format_text("\u6211\u7528Python\u5f00\u53d1", "zh")
        assert result == "\u6211\u7528 Python \u5f00\u53d1"

    def test_format_english_no_space_insertion(self):
        result = format_text("Hello World", "en")
        assert result == "Hello World"

    def test_format_empty(self):
        assert format_text("") == ""


class TestFormatNumeric:
    def test_percent_conversion(self):
        result = format_numeric("50%")
        assert "\u767e\u5206\u4e4b" in result


class TestMixedScenarios:
    def test_complex_mixed_sentence(self):
        text = "\u6211\u5728\u7528java\u5f00\u53d1, \u4f46\u662f\u4e5f\u7528python"
        result = format_text(text, "zh")
        assert "java" in result
        assert "python" in result

    def test_numbers_in_chinese(self):
        result = format_text("\u4ef7\u683c100\u5143", "zh")
        assert "100" in result
