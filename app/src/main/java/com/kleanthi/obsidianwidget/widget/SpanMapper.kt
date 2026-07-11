package com.kleanthi.obsidianwidget.widget

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.kleanthi.obsidianwidget.core.Block
import com.kleanthi.obsidianwidget.core.BlockType
import com.kleanthi.obsidianwidget.core.InlineStyle
import com.kleanthi.obsidianwidget.core.InlineStyler
import com.kleanthi.obsidianwidget.core.StyledText

object SpanMapper {
    private const val LINK_COLOR = 0xFF8B7EC8.toInt()
    private const val HIGHLIGHT_BG = 0x59FFD54F // translucent amber
    private const val QUOTE_COLOR = 0xFF9E9E9E.toInt()

    fun toSpannable(styled: StyledText, strike: Boolean = false): SpannableString {
        val s = SpannableString(styled.text)
        for (span in styled.spans) {
            if (span.start >= span.end) continue
            val what: Any = when (span.style) {
                InlineStyle.BOLD -> StyleSpan(Typeface.BOLD)
                InlineStyle.ITALIC -> StyleSpan(Typeface.ITALIC)
                InlineStyle.HIGHLIGHT -> BackgroundColorSpan(HIGHLIGHT_BG)
                InlineStyle.CODE -> TypefaceSpan("monospace")
                InlineStyle.STRIKE -> StrikethroughSpan()
                InlineStyle.LINK -> ForegroundColorSpan(LINK_COLOR)
            }
            s.setSpan(what, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (strike && s.isNotEmpty()) {
            s.setSpan(StrikethroughSpan(), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    fun render(block: Block): CharSequence = when (block.type) {
        BlockType.HEADING -> {
            val s = toSpannable(InlineStyler.style(block.text))
            val size = when (block.level) { 1 -> 1.5f; 2 -> 1.3f; 3 -> 1.15f; else -> 1.05f }
            if (s.isNotEmpty()) {
                s.setSpan(RelativeSizeSpan(size), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            s
        }
        BlockType.BULLET ->
            SpannableStringBuilder("    ".repeat(block.indent) + "•  ")
                .append(toSpannable(InlineStyler.style(block.text)))
        BlockType.QUOTE -> {
            val b = SpannableStringBuilder("▎ ").append(toSpannable(InlineStyler.style(block.text)))
            b.setSpan(ForegroundColorSpan(QUOTE_COLOR), 0, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            b.setSpan(StyleSpan(Typeface.ITALIC), 0, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            b
        }
        BlockType.CODE -> {
            val s = SpannableString(block.text)
            if (s.isNotEmpty()) s.setSpan(TypefaceSpan("monospace"), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s
        }
        BlockType.RULE -> "──────────"
        BlockType.TASK, BlockType.PARAGRAPH -> toSpannable(InlineStyler.style(block.text))
    }
}
