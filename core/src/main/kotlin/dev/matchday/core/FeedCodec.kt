package dev.matchday.core

/**
 * Dependency-free parser for the public Matchday JSON feed contract.
 * Unknown fields can be added by future feed producers without breaking clients.
 * Dates and team IDs are validated before persistent caching.
 */
object FeedCodec {
    fun decode(json: String): SportsFeed {
        val root = MiniJson(json).value() as? Map<*, *> ?: error("피드 JSON은 객체여야 합니다")
        val version = root.int("schemaVersion", 0)
        require(version == 1) { "지원하지 않는 schemaVersion: $version" }
        fun rows(key: String): List<Map<*, *>> = (root[key] as? List<*>)?.map {
            it as? Map<*, *> ?: error("$key 목록의 원소는 객체여야 합니다")
        } ?: emptyList()
        return SportsFeed(
            updatedAt = root.str("updatedAt"),
            isDemo = root.bool("isDemo"),
            demoDay = root.str("demoDay", "2026-10-30"),
            teams = rows("teams").map { t -> Team(t.str("id"), t.str("sport"), t.str("name"),
                t.str("short"), t.str("mark", t.str("short")), t.str("logoUrl")) },
            games = rows("games").map { g -> Game(g.str("id"), g.str("competition"), g.str("season"),
                g.str("dateTime"), g.str("home"), g.str("away"), g.str("status"), g.optInt("homeScore"),
                g.optInt("awayScore"), g.str("stage"), g.str("venue"), g.str("seriesId")) },
            standings = rows("standings").map { s -> Standing(s.str("competition"), s.str("season"),
                s.int("rank"), s.str("team"), s.int("played"), s.int("won"), s.int("lost"),
                s.int("drawn"), s.int("forScore"), s.int("againstScore"), s.str("streak")) },
            series = rows("series").map { b -> Series(b.str("id"), b.str("competition"), b.str("stage"),
                b.str("group"), b.int("roundIndex"), b.str("left"), b.str("right"),
                b.int("leftWins"), b.int("rightWins"), b.str("status"), b.str("scheduled"),
                b.int("bestOf", 5), b.str("nextId")) }
        ).validate()
    }

    private fun Map<*, *>.str(key: String, fallback: String = ""): String = this[key] as? String ?: fallback
    private fun Map<*, *>.bool(key: String): Boolean = this[key] as? Boolean ?: false
    private fun Map<*, *>.int(key: String, fallback: Int = 0): Int = (this[key] as? Number)?.toInt() ?: fallback
    private fun Map<*, *>.optInt(key: String): Int? = (this[key] as? Number)?.toInt()
}

/** Small strict RFC 8259-style JSON reader, no Android or network dependencies. */
internal class MiniJson(private val src: String) {
    private var pos = 0
    fun value(): Any? {
        skipSpace()
        val result = read()
        skipSpace()
        require(pos == src.length) { "JSON 종료 뒤 데이터가 존재합니다: $pos" }
        return result
    }
    private fun read(): Any? {
        skipSpace()
        require(pos < src.length) { "JSON이 예상보다 일찍 끝났습니다" }
        return when (src[pos]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> number()
        }
    }
    private fun obj(): Map<String, Any?> {
        expect('{')
        val map = linkedMapOf<String, Any?>()
        skipSpace()
        if (accept('}')) return map
        do {
            skipSpace()
            require(src.getOrNull(pos) == '"') { "키는 문자열이어야 합니다" }
            val key = str()
            skipSpace(); expect(':')
            map[key] = read()
            skipSpace()
            if (accept('}')) return map
            expect(',')
        } while (true)
    }
    private fun arr(): List<Any?> {
        expect('[')
        val list = mutableListOf<Any?>()
        skipSpace()
        if (accept(']')) return list
        do {
            list.add(read())
            skipSpace()
            if (accept(']')) return list
            expect(',')
        } while (true)
    }
    private fun str(): String {
        expect('"')
        val result = StringBuilder()
        while (pos < src.length) {
            val c = src[pos++]
            if (c == '"') return result.toString()
            if (c != '\\') {
                require(c.code >= 32) { "문자열 안에 제어 문자가 포함되었습니다" }
                result.append(c)
                continue
            }
            require(pos < src.length) { "이스케이프 오류" }
            when (val esc = src[pos++]) {
                '"', '\\', '/' -> result.append(esc)
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'u' -> {
                    require(pos + 4 <= src.length) { "유니코드 이스케이프 오류" }
                    result.append(src.substring(pos, pos + 4).toInt(16).toChar())
                    pos += 4
                }
                else -> error("잘못된 이스케이프: $esc")
            }
        }
        error("닫는 따옴표가 없습니다")
    }
    private fun number(): Number {
        val start = pos
        if (src.getOrNull(pos) == '-') pos++
        while (src.getOrNull(pos)?.isDigit() == true) pos++
        if (src.getOrNull(pos) == '.') {
            pos++
            while (src.getOrNull(pos)?.isDigit() == true) pos++
        }
        if (src.getOrNull(pos) in listOf('e', 'E')) {
            pos++
            if (src.getOrNull(pos) in listOf('+', '-')) pos++
            while (src.getOrNull(pos)?.isDigit() == true) pos++
        }
        require(pos > start) { "숫자 형식 오류: $pos" }
        val number = src.substring(start, pos)
        return number.toLongOrNull() ?: number.toDoubleOrNull() ?: error("숫자 형식 오류: $number")
    }
    private fun literal(word: String, result: Any?): Any? {
        require(src.startsWith(word, pos)) { "리터럴 오류: $pos" }
        pos += word.length
        return result
    }
    private fun skipSpace() { while (src.getOrNull(pos)?.isWhitespace() == true) pos++ }
    private fun expect(ch: Char) { require(accept(ch)) { "'$ch' 필요: $pos" } }
    private fun accept(ch: Char): Boolean = if (src.getOrNull(pos) == ch) { pos++; true } else false
}
