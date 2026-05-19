package com.power.gitinsight.domain.ai

/**
 * team : gitInsight.
 * Class Name: Json
 * Description: Tiny dependency-free JSON helper. We don't pull in Gson / kotlinx.serialization just for the
 *              AI layer — requests are built from a few well-known shapes and responses always need exactly
 *              one string field. Anything more elaborate should grow a real parser.
 *
 * @author: power
 * on Date: 2026/05/19 Time: 14:18
 **/
internal object Json {

    /** Wrap [s] as a JSON string literal (with surrounding quotes), escaping control chars and quotes. */
    fun escape(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /**
     * Return the value of the first `"name":"..."` occurrence in [body], with backslash escapes decoded.
     * Returns null if the field is not present. NOT a general parser — assumes the first occurrence is the
     * one we want, which is true for the AI APIs we hit (OpenAI choices[].message.content, Claude
     * content[].text, CF Workers AI result.response).
     */
    fun extractFirstStringField(body: String, name: String): String? {
        val pattern = Regex("\"" + Regex.escape(name) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val match = pattern.find(body) ?: return null
        return unescape(match.groupValues[1])
    }

    private fun unescape(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> if (i + 5 < s.length) {
                        val hex = s.substring(i + 2, i + 6)
                        sb.append(hex.toInt(16).toChar())
                        i += 4
                    } else sb.append(next)
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
