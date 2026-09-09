package com.alal.notes.domain.findreplace

import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class FindOptions(
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val regex: Boolean = false,
)

data class FindMatch(val start: Int, val end: Int)

sealed interface FindResult {
    data class Matches(val matches: List<FindMatch>) : FindResult
    data class Error(val message: String) : FindResult
    data object Empty : FindResult
}

object FindReplace {

    fun compile(query: String, options: FindOptions): Result<Pattern> {
        if (query.isEmpty()) return Result.failure(IllegalArgumentException("empty"))
        var flags = 0
        if (!options.caseSensitive) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        if (options.regex) flags = flags or Pattern.MULTILINE
        var body = if (options.regex) query else Pattern.quote(query)
        if (options.wholeWord) {
            // \b is ASCII-centric; use script-agnostic lookarounds instead.
            body = "(?<![\\p{L}\\p{N}\\p{M}_])(?:$body)(?![\\p{L}\\p{N}\\p{M}_])"
        }
        return try {
            Result.success(Pattern.compile(body, flags))
        } catch (e: PatternSyntaxException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    fun find(text: CharSequence, query: String, options: FindOptions, limit: Int = 5000): FindResult {
        if (query.isEmpty()) return FindResult.Empty
        val pattern = compile(query, options).getOrElse { e ->
            return FindResult.Error(e.describe())
        }
        val out = ArrayList<FindMatch>()
        try {
            val m = pattern.matcher(text)
            while (m.find()) {
                if (m.end() == m.start()) continue // zero-width match; Matcher.find() advances by itself
                out += FindMatch(m.start(), m.end())
                if (out.size >= limit) break
            }
        } catch (e: Exception) {
            return FindResult.Error(e.describe())
        }
        return FindResult.Matches(out)
    }

    /** Replace one match. In regex mode `$1` style references work; otherwise the text is literal. */
    fun replaceOne(text: String, match: FindMatch, query: String, replacement: String, options: FindOptions): Result<String> {
        val pattern = compile(query, options).getOrElse { return Result.failure(it) }
        val segment = text.substring(match.start, match.end)
        val m = pattern.matcher(segment)
        val replaced = try {
            if (m.matches()) m.replaceFirst(if (options.regex) replacement else Matcher.quoteReplacement(replacement))
            else if (options.regex) replacement else replacement
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return Result.success(text.substring(0, match.start) + replaced + text.substring(match.end))
    }

    /** Returns the new text and the number of replacements. */
    fun replaceAll(text: String, query: String, replacement: String, options: FindOptions): Result<Pair<String, Int>> {
        val pattern = compile(query, options).getOrElse { return Result.failure(it) }
        return try {
            val m = pattern.matcher(text)
            val sb = StringBuilder(text.length)
            var count = 0
            val rep = if (options.regex) replacement else Matcher.quoteReplacement(replacement)
            while (m.find()) {
                if (m.end() == m.start()) continue
                m.appendReplacement(sb, rep)
                count++
            }
            m.appendTail(sb)
            Result.success(sb.toString() to count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Throwable.describe(): String = when (this) {
        is PatternSyntaxException -> description ?: "invalid pattern"
        else -> message ?: "invalid pattern"
    }
}
