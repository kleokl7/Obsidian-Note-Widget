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
    fun toSpannable(styled: StyledText, palette: Palette, strike: Boolean = false): SpannableString {
        val s = SpannableString(styled.text)
        for (span in styled.spans) {
            if (span.start >= span.end) continue
            val whats: List<Any> = when (span.style) {
                InlineStyle.BOLD -> listOf(StyleSpan(Typeface.BOLD))
                InlineStyle.ITALIC -> listOf(StyleSpan(Typeface.ITALIC))
                InlineStyle.HIGHLIGHT -> listOf(BackgroundColorSpan(palette.highlightBg))
                InlineStyle.CODE -> listOf(
                    TypefaceSpan("monospace"),
                    BackgroundColorSpan(palette.codeBg),
                    ForegroundColorSpan(palette.codeText),
                )
                InlineStyle.STRIKE -> listOf(StrikethroughSpan())
                InlineStyle.LINK -> listOf(ForegroundColorSpan(palette.link))
            }
            for (what in whats) s.setSpan(what, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (strike && s.isNotEmpty()) {
            s.setSpan(StrikethroughSpan(), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    fun render(block: Block, palette: Palette): CharSequence = when (block.type) {
        BlockType.HEADING -> {
            val s = toSpannable(InlineStyler.style(block.text), palette)
            val size = when (block.level) { 1 -> 1.5f; 2 -> 1.3f; 3 -> 1.15f; else -> 1.05f }
            if (s.isNotEmpty()) {
                s.setSpan(RelativeSizeSpan(size), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(ForegroundColorSpan(palette.heading), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            s
        }
        BlockType.BULLET ->
            SpannableStringBuilder("    ".repeat(block.indent) + "•  ")
                .append(toSpannable(InlineStyler.style(block.text), palette))
        BlockType.QUOTE -> {
            val b = SpannableStringBuilder("▎ ").append(toSpannable(InlineStyler.style(block.text), palette))
            b.setSpan(ForegroundColorSpan(palette.muted), 0, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            b.setSpan(StyleSpan(Typeface.ITALIC), 0, b.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            b
        }
        BlockType.CODE -> {
            val s = SpannableString(block.text)
            if (s.isNotEmpty()) {
                s.setSpan(TypefaceSpan("monospace"), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(ForegroundColorSpan(palette.codeText), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            s
        }
        BlockType.RULE -> SpannableString("──────────").apply {
            setSpan(ForegroundColorSpan(palette.faint), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        BlockType.TASK, BlockType.PARAGRAPH -> toSpannable(InlineStyler.style(block.text), palette)
    }
}
