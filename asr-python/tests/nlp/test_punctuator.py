import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "src"))

from nlp.punctuator import (
    restore_punctuation,
    segment_sentences,
    _ends_with_punctuation,
    _deduplicate_punctuation,
)


class TestRestorePunctuation:
    def test_empty_text(self):
        assert restore_punctuation("") == ""

    def test_whitespace_only(self):
        assert restore_punctuation("   ") == ""

    def test_adds_period_at_end(self):
        result = restore_punctuation("今天天气很好")
        assert result.endswith("。")

    def test_does_not_double_period(self):
        result = restore_punctuation("今天天气很好。")
        assert result.endswith("。")
        assert result.count("。") == 1

    def test_adds_comma_after_but(self):
        result = restore_punctuation("今天天气很好但是有点冷")
        assert "，但是" in result or "，有点" in result

    def test_adds_comma_after_because(self):
        result = restore_punctuation("我迟到了因为路上堵车")
        assert "，因为" in result

    def test_adds_question_mark_for_ma(self):
        result = restore_punctuation("你吃饭了吗")
        assert "？" in result

    def test_adds_question_mark_for_ne(self):
        result = restore_punctuation("你在干什么呢")
        assert "？" in result

    def test_keeps_existing_punctuation(self):
        result = restore_punctuation("你好。")
        assert result == "你好。"

    def test_adds_comma_for_first(self):
        result = restore_punctuation("首先我们要了解背景")
        assert result.startswith("首先，")

    def test_english_sentence_boundary(self):
        result = restore_punctuation("hello world")
        assert result.endswith("。")

    def test_deduplicate_punctuation(self):
        result = _deduplicate_punctuation("你好。。。")
        assert result == "你好。"


class TestSegmentSentences:
    def test_single_sentence(self):
        result = segment_sentences("今天天气很好。")
        assert len(result) == 1
        assert result[0] == "今天天气很好。"

    def test_multiple_sentences(self):
        result = segment_sentences("你好。今天天气怎么样？太好了！")
        assert len(result) == 3
        assert result[0] == "你好。"
        assert result[1] == "今天天气怎么样？"
        assert result[2] == "太好了！"

    def test_no_punctuation(self):
        result = segment_sentences("今天天气很好")
        assert len(result) == 1
        assert result[0] == "今天天气很好"

    def test_empty_text(self):
        result = segment_sentences("")
        assert result == []

    def test_newline_separation(self):
        result = segment_sentences("第一行。\n第二行。")
        assert len(result) >= 2


class TestEndsWithPunctuation:
    def test_period(self):
        assert _ends_with_punctuation("你好。")

    def test_comma(self):
        assert _ends_with_punctuation("你好，")

    def test_no_punctuation(self):
        assert not _ends_with_punctuation("你好")

    def test_empty(self):
        assert not _ends_with_punctuation("")

    def test_question(self):
        assert _ends_with_punctuation("你好？")

    def test_exclamation(self):
        assert _ends_with_punctuation("你好！")


class TestDeduplicatePunctuation:
    def test_double_period(self):
        assert _deduplicate_punctuation("你好。。") == "你好。"

    def test_triple_comma(self):
        assert _deduplicate_punctuation("你好，，，") == "你好，"

    def test_mixed(self):
        result = _deduplicate_punctuation("你好。。这个，，行吗？？")
        assert result == "你好。这个，行吗？"

    def test_no_duplicates(self):
        assert _deduplicate_punctuation("你好。") == "你好。"
