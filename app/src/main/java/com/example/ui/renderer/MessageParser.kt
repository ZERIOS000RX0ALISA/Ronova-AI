package com.example.ui.renderer

import com.example.data.model.ChartSeries
import com.example.data.model.MessageBlock

object MessageParser {

    /**
     * Parses raw assistant or user markdown content into a list of structured MessageBlock items.
     * Resilient to partial markdown streams (e.g. unclosed code fences during active token generation).
     */
    fun parse(rawContent: String): List<MessageBlock> {
        if (rawContent.isBlank()) return emptyList()

        val blocks = mutableListOf<MessageBlock>()
        val lines = rawContent.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            // 1. Code Fence (``` or ````)
            if (line.trimStart().startsWith("```")) {
                val fencePrefix = line.trimStart().takeWhile { it == '`' }
                val language = line.trimStart().removePrefix(fencePrefix).trim().takeIf { it.isNotEmpty() }
                val codeLines = mutableListOf<String>()
                i++
                var closed = false
                while (i < lines.size) {
                    val cur = lines[i]
                    if (cur.trimStart().startsWith(fencePrefix)) {
                        closed = true
                        i++
                        break
                    } else {
                        codeLines.add(cur)
                        i++
                    }
                }
                blocks.add(MessageBlock.Code(language = language, content = codeLines.joinToString("\n")))
                continue
            }

            // 2. Math Block ($$)
            if (line.trim().startsWith("$$")) {
                val mathLines = mutableListOf<String>()
                val restOfFirstLine = line.trim().removePrefix("$$").trim()
                if (restOfFirstLine.endsWith("$$") && restOfFirstLine.length > 2) {
                    // Single line math block: $$ formula $$
                    val formula = restOfFirstLine.removeSuffix("$$").trim()
                    blocks.add(MessageBlock.LatexBlock(content = formula, displayMode = true))
                    i++
                    continue
                }

                if (restOfFirstLine.isNotEmpty()) {
                    mathLines.add(restOfFirstLine)
                }
                i++
                var mathClosed = false
                while (i < lines.size) {
                    val cur = lines[i]
                    if (cur.trim().endsWith("$$")) {
                        val lastPart = cur.trim().removeSuffix("$$").trim()
                        if (lastPart.isNotEmpty()) mathLines.add(lastPart)
                        mathClosed = true
                        i++
                        break
                    } else {
                        mathLines.add(cur)
                        i++
                    }
                }
                blocks.add(MessageBlock.LatexBlock(content = mathLines.joinToString("\n"), displayMode = true))
                continue
            }

            // 3. Markdown Table (must have header line with | and divider line with |---|)
            if (isTableRow(line) && i + 1 < lines.size && isTableDivider(lines[i + 1])) {
                val headerCols = parseTableRow(line)
                i += 2 // skip header and divider
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && isTableRow(lines[i])) {
                    rows.add(parseTableRow(lines[i]))
                    i++
                }
                blocks.add(MessageBlock.Table(columns = headerCols, rows = rows))
                continue
            }

            // 4. Standalone Markdown Image (![alt](url))
            val imageRegex = """^!\[(.*?)\]\((https?://[^\s)]+)\)$""".toRegex()
            val imageMatch = imageRegex.find(line.trim())
            if (imageMatch != null) {
                val caption = imageMatch.groupValues[1].takeIf { it.isNotEmpty() }
                val url = imageMatch.groupValues[2]
                blocks.add(MessageBlock.ImageBlock(url = url, caption = caption))
                i++
                continue
            }

            // 5. Regular Markdown text / paragraph
            val textLines = mutableListOf<String>()
            while (i < lines.size) {
                val cur = lines[i]
                if (cur.trimStart().startsWith("```") ||
                    cur.trim().startsWith("$$") ||
                    (isTableRow(cur) && i + 1 < lines.size && isTableDivider(lines[i + 1])) ||
                    imageRegex.matches(cur.trim())
                ) {
                    break
                }
                textLines.add(cur)
                i++
            }
            if (textLines.isNotEmpty()) {
                val textContent = textLines.joinToString("\n")
                if (textContent.isNotBlank()) {
                    blocks.add(MessageBlock.Markdown(textContent))
                }
            }
        }

        return blocks
    }

    private fun isTableRow(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2
    }

    private fun isTableDivider(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) return false
        val parts = trimmed.split("|").filter { it.isNotBlank() }
        return parts.isNotEmpty() && parts.all { col -> col.trim().all { c -> c == '-' || c == ':' } }
    }

    private fun parseTableRow(line: String): List<String> {
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        return trimmed.split("|").map { it.trim() }
    }
}
