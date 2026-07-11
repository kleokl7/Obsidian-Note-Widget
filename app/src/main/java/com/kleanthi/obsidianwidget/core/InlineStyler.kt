package com.kleanthi.obsidianwidget.core

enum class InlineStyle { BOLD, ITALIC, HIGHLIGHT, CODE, STRIKE, LINK }

data class Span(val start: Int, val end: Int, val style: InlineStyle)

data class StyledText(val text: String, val spans: List<Span>)

object InlineStyler {
    private data class Pat(val regex: Regex, val style: InlineStyle, val textGroup: Int)

    // Order matters where matches start at the same index:
    // earlier entries win (bold `**` must beat italic `*`).
    private val pats = listOf(
        Pat(Regex("""\[\[([^\]|]+)\|([^\]]+)]]"""), InlineStyle.LINK, 2),
        Pat(Regex("""\[\[([^\]]+)]]"""), InlineStyle.LINK, 1),
        Pat(Regex("""\[([^\]]+)]\(([^)]+)\)"""), InlineStyle.LINK, 1),
        Pat(Regex("""`([^`]+)`"""), InlineStyle.CODE, 1),
        Pat(Regex("""\*\*([^*]+)\*\*"""), InlineStyle.BOLD, 1),
        Pat(Regex("""__([^_]+)__"""), InlineStyle.BOLD, 1),
        Pat(Regex("""==([^=]+)=="""), InlineStyle.HIGHLIGHT, 1),
        Pat(Regex("""~~([^~]+)~~"""), InlineStyle.STRIKE, 1),
        Pat(Regex("""\*([^*]+)\*"""), InlineStyle.ITALIC, 1),
        Pat(Regex("""_([^_]+)_"""), InlineStyle.ITALIC, 1),
    )

    fun style(raw: String): StyledText {
        val out = StringBuilder()
        val spans = mutableListOf<Span>()
        var pos = 0
        while (pos < raw.length) {
            var best: MatchResult? = null
            var bestPat: Pat? = null
            for (p in pats) {
                val m = p.regex.find(raw, pos) ?: continue
                if (best == null || m.range.first < best.range.first) {
                    best = m; bestPat = p
                }
            }
            if (best == null || bestPat == null) {
                out.append(raw, pos, raw.length)
                break
            }
            out.append(raw, pos, best.range.first)
            val inner = best.groupValues[bestPat.textGroup]
            val start = out.length
            out.append(inner)
            spans.add(Span(start, out.length, bestPat.style))
            pos = best.range.last + 1
        }
        return StyledText(out.toString(), spans)
    }
}
