package com.mtga.app.core.text

/**
 * One decoder for the HTML character references every source puts in a post's text: named
 * (`&amp;`), decimal (`&#38;`) and hexadecimal (`&#x27;`). Ported from jsoup (MIT), whose
 * Entities.unescape hands the string to the HTML tokeniser's character reference rules, so a
 * reference without its `;` or an unknown one stays as it was written.
 *
 * https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/nodes/Entities.java#unescape
 *
 * not ported: EscapeMode xhtml and its xmlPoints (we decode, we never write XML)
 * not ported: extended's 2125 names (the named table is jsoup's base set of 106, plus [extraPoints])
 * not ported: nameForCodepoint (escaping only)
 * not ported: getByName (nothing asks for a name's string)
 * not ported: escape (escaping only)
 * not ported: escapeString (escaping only)
 * not ported: doEscape (escaping only)
 * not ported: appendEscaped (escaping only)
 * not ported: appendEncoded (escaping only)
 * not ported: appendNbsp (escaping only)
 * not ported: appendApos (escaping only)
 * not ported: canEncode (no charset to encode for)
 * not ported: isValidXmlChar (no XML output)
 * not ported: byName (no charset)
 * not ported: encoderFor (no charset)
 */
object Entities {
    private const val empty = -1
    private const val emptyName = ""
    private const val codepointRadix = 36
    private val codeDelims = charArrayOf(',', ';')
    private val multipoints = HashMap<String, String>() // name -> multiple character references

    private const val BaseCount = 106
    private val baseSorted = ArrayList<String>(BaseCount) // names sorted longest first, for prefix matching

    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/parser/Tokeniser.java#L17-L30
    private const val replacementChar = '�' // replaces null character
    private val notCharRefCharsSorted = charArrayOf('\t', '\n', '\r', '\u000C', ' ', '<', '&').also { it.sort() }

    // Some illegal character escapes are parsed by browsers as windows-1252 instead (jsoup issue 1034).
    private const val win1252ExtensionsStart = 0x80
    private val win1252Extensions = intArrayOf(
        0x20AC, 0x0081, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021,
        0x02C6, 0x2030, 0x0160, 0x2039, 0x0152, 0x008D, 0x017D, 0x008F,
        0x0090, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014,
        0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0x009D, 0x017E, 0x0178
    )

    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/nodes/EntitiesData.java#L15
    // Copied as it is: names sorted, each "name=codepoint;index&" with the codepoint in base 36.
    private const val basePoints =
        "AElig=5i;1c&AMP=12;2&Aacute=5d;17&Acirc=5e;18&Agrave=5c;16&Aring=5h;1b&Atilde=5f;19&Auml=5g;1a&COPY=4p;h&Ccedil=5j;1d&" +
            "ETH=5s;1m&Eacute=5l;1f&Ecirc=5m;1g&Egrave=5k;1e&Euml=5n;1h&GT=1q;6&Iacute=5p;1j&Icirc=5q;1k&Igrave=5o;1i&Iuml=5r;1l&" +
            "LT=1o;4&Ntilde=5t;1n&Oacute=5v;1p&Ocirc=5w;1q&Ograve=5u;1o&Oslash=60;1u&Otilde=5x;1r&Ouml=5y;1s&QUOT=y;0&REG=4u;n&" +
            "THORN=66;20&Uacute=62;1w&Ucirc=63;1x&Ugrave=61;1v&Uuml=64;1y&Yacute=65;1z&aacute=69;23&acirc=6a;24&acute=50;u&" +
            "aelig=6e;28&agrave=68;22&amp=12;3&aring=6d;27&atilde=6b;25&auml=6c;26&brvbar=4m;e&ccedil=6f;29&cedil=54;y&cent=4i;a&" +
            "copy=4p;i&curren=4k;c&deg=4w;q&divide=6v;2p&eacute=6h;2b&ecirc=6i;2c&egrave=6g;2a&eth=6o;2i&euml=6j;2d&frac12=59;13&" +
            "frac14=58;12&frac34=5a;14&gt=1q;7&iacute=6l;2f&icirc=6m;2g&iexcl=4h;9&igrave=6k;2e&iquest=5b;15&iuml=6n;2h&laquo=4r;k&" +
            "lt=1o;5&macr=4v;p&micro=51;v&middot=53;x&nbsp=4g;8&not=4s;l&ntilde=6p;2j&oacute=6r;2l&ocirc=6s;2m&ograve=6q;2k&" +
            "ordf=4q;j&ordm=56;10&oslash=6w;2q&otilde=6t;2n&ouml=6u;2o&para=52;w&plusmn=4x;r&pound=4j;b&quot=y;1&raquo=57;11&" +
            "reg=4u;o&sect=4n;f&shy=4t;m&sup1=55;z&sup2=4y;s&sup3=4z;t&szlig=67;21&thorn=72;2w&times=5z;1t&uacute=6y;2s&ucirc=6z;2t&" +
            "ugrave=6x;2r&uml=4o;g&uuml=70;2u&yacute=71;2v&yen=4l;d&yuml=73;2x&"

    // The few names outside the base set that posts carry (quotes, dashes, the ellipsis, arrows, the
    // euro and trade signs, apos), cut from jsoup's fullPoints, with the rules of its extended set:
    // they are decoded only when their `;` is there.
    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/nodes/EntitiesData.java#L16
    private const val extraPoints =
        "apos=13;a&bdquo=6ce;e4&bull=6ci;e9&euro=6gc;f2&hellip=6cm;eb&larr=6mo;hx&ldquo=6cc;dz&lsquo=6c8;ds&" +
            "mdash=6c4;dn&ndash=6c3;dm&rarr=6mq;i6&rdquo=6cd;e2&rsquo=6c9;dv&sbquo=6ca;dx&trade=6jm;gg&"

    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/nodes/Entities.java#EscapeMode
    // not ported: nameForCodepoint (escaping only)
    // not ported: the xhtml and extended constants as such (two tables here, base and extended)
    private class EscapeMode(pointsData: String, size: Int) {
        // table of named references to their codepoints. sorted so we can binary search.
        val nameKeys = Array(size) { emptyName }
        val codeVals = IntArray(size)

        init {
            load(this, pointsData, size)
        }

        fun codepointForName(name: String): Int {
            val index = nameKeys.binarySearch(name)
            return if (index >= 0) codeVals[index] else empty
        }
    }

    private val base = EscapeMode(basePoints, BaseCount)
    private val extended = EscapeMode(extraPoints, 15)

    init {
        // sort the base names by length, for prefix matching
        baseSorted.addAll(base.nameKeys)
        baseSorted.sortWith { a, b -> b.length - a.length }
    }

    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/nodes/Entities.java#isNamedEntity
    /** True for a name of the base set or of [extraPoints], jsoup's isNamedEntity over the names we keep. */
    fun isNamedEntity(name: String): Boolean =
        base.codepointForName(name) != empty || extended.codepointForName(name) != empty

    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/nodes/Entities.java#isBaseNamedEntity
    fun isBaseNamedEntity(name: String): Boolean = base.codepointForName(name) != empty

    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/nodes/Entities.java#codepointsForName
    private fun codepointsForName(name: String, codepoints: IntArray): Int {
        val value = multipoints[name]
        if (value != null) {
            codepoints[0] = value.codePointAt(0)
            codepoints[1] = value.codePointAt(1)
            return 2
        }
        val codepoint = base.codepointForName(name).takeIf { it != empty } ?: extended.codepointForName(name)
        if (codepoint != empty) {
            codepoints[0] = codepoint
            return 1
        }
        return 0
    }

    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/nodes/Entities.java#findPrefix
    /** Finds the longest base named entity that is a prefix of the input. That is, input "notit" would return "not". */
    fun findPrefix(input: String): String {
        for (name in baseSorted) {
            if (input.startsWith(name)) return name
        }
        return emptyName
    }

    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/nodes/Entities.java#unescape
    /** Un-escape an HTML escaped string. That is, `&lt;` is returned as `<`. */
    fun unescape(string: String): String = unescape(string, false)

    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/nodes/Entities.java#unescape
    // Parser.unescapeEntities: nothing to do without an ampersand, else the tokeniser's rules.
    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/parser/Parser.java#L356-L360
    // not ported: Parser.unescape (the instance method builds a tree builder and a tokeniser for the
    // same loop that unescapeEntities below runs on the string)
    /** [strict] is jsoup's inAttribute: a semicolonless name followed by a letter, digit or `=` is left alone. */
    fun unescape(string: String, strict: Boolean): String {
        if (string.indexOf('&') < 0) return string // nothing to unescape
        return unescapeEntities(CharacterReader(string), strict)
    }

    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/nodes/Entities.java#load
    // not ported: codeKeys and nameVals (the codepoint to name tables, for escaping only)
    private fun load(e: EscapeMode, pointsData: String, size: Int) {
        var i = 0
        val reader = CharacterReader(pointsData)
        while (!reader.isEmpty()) {
            // NotNestedLessLess=10913,824;1887&
            val name = reader.consumeTo('=')
            reader.advance()
            val cp1 = reader.consumeToAny(codeDelims).toInt(codepointRadix)
            val codeDelim = reader.current()
            reader.advance()
            val cp2 = if (codeDelim == ',') {
                val second = reader.consumeTo(';').toInt(codepointRadix)
                reader.advance()
                second
            } else {
                empty
            }
            reader.consumeTo('&') // the index in the codepoint to name tables, not kept
            reader.advance()

            e.nameKeys[i] = name
            e.codeVals[i] = cp1

            if (cp2 != empty) {
                multipoints[name] = String(intArrayOf(cp1, cp2), 0, 2)
            }
            i++
        }
        check(i == size) { "Unexpected count of entities loaded" }
    }

    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/parser/Tokeniser.java#L435-L453
    private fun unescapeEntities(reader: CharacterReader, inAttribute: Boolean): String {
        val builder = StringBuilder()
        while (!reader.isEmpty()) {
            builder.append(reader.consumeTo('&'))
            if (reader.matches('&')) {
                reader.consume()
                val c = consumeCharacterReference(reader, null, inAttribute)
                if (c == null || c.isEmpty()) {
                    builder.append('&')
                } else {
                    builder.appendCodePoint(c[0])
                    if (c.size == 2) builder.appendCodePoint(c[1])
                }
            }
        }
        return builder.toString()
    }

    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/parser/Tokeniser.java#L201-L270
    // Parses a character reference after its leading `&` has been consumed: one or two decoded code
    // points, or null if no reference matched.
    // not ported: peekCharacterReference (the tokeniser's lookahead)
    // not ported: isWhitespaceReference (the tokeniser's lookahead)
    // not ported: rewindReference (its peek branch, the plain rewind is inline)
    // not ported: characterReferenceError (parse error tracking)
    private fun consumeCharacterReference(reader: CharacterReader, additionalAllowedCharacter: Char?, inAttribute: Boolean): IntArray? {
        if (reader.isEmpty()) return null
        if (additionalAllowedCharacter != null && additionalAllowedCharacter == reader.current()) return null
        if (reader.matchesAnySorted(notCharRefCharsSorted)) return null

        val codeRef = IntArray(1)
        val multipointHolder = IntArray(2)
        reader.mark()
        if (reader.matchConsume("#")) { // numbered
            val isHexMode = reader.matchConsumeIgnoreCase("X")
            val numRef = if (isHexMode) reader.consumeHexSequence() else reader.consumeDigitSequence()
            if (numRef.isEmpty()) { // didn't match anything
                reader.rewindToMark()
                return null
            }

            reader.unmark()
            reader.matchConsume(";") // a missing semicolon is only a parse error
            val radix = if (isHexMode) 16 else 10
            var charval = numRef.toIntOrNull(radix) ?: -1
            // The syntax is always html here, so a zero is the replacement character too.
            if (charval == -1 || charval > 0x10FFFF || charval == 0) {
                codeRef[0] = replacementChar.code
            } else {
                // fix illegal unicode characters to match browser behavior
                if (charval >= win1252ExtensionsStart && charval < win1252ExtensionsStart + win1252Extensions.size) {
                    charval = win1252Extensions[charval - win1252ExtensionsStart]
                }
                codeRef[0] = charval
            }
            return codeRef
        } else { // named
            // get as many letters as possible, and look for matching entities.
            var nameRef = reader.consumeLetterThenDigitSequence()
            val looksLegit = reader.matches(';')
            // found if a base named entity without a ;, or an extended entity with the ;.
            val found = isBaseNamedEntity(nameRef) || (isNamedEntity(nameRef) && looksLegit)

            if (!found) {
                reader.rewindToMark()
                if (inAttribute) return null
                // check if there's a base prefix match; consume and use that if so
                val prefix = findPrefix(nameRef)
                if (prefix.isEmpty()) return null
                reader.matchConsume(prefix)
                nameRef = prefix
            }
            if (inAttribute && (reader.matchesAsciiAlpha() || reader.matchesDigit() || reader.matches('='))) {
                // in attributes, don't consume semicolonless references followed by ASCII alphanumeric or equals
                reader.rewindToMark()
                return null
            }

            reader.unmark()
            reader.matchConsume(";") // a missing semicolon is only a parse error
            val numChars = codepointsForName(nameRef, multipointHolder)
            return when (numChars) {
                1 -> {
                    codeRef[0] = multipointHolder[0]
                    codeRef
                }
                2 -> multipointHolder
                else -> error("Unexpected characters returned for $nameRef")
            }
        }
    }

    // The reader over a string, on a string in place of the buffered stream. Each call is jsoup's:
    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/parser/CharacterReader.java#L130-L150
    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/parser/CharacterReader.java#L258-L310
    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/parser/CharacterReader.java#L349-L358
    // https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/parser/CharacterReader.java#L555-L700
    // not ported: bufferUp (no stream buffer)
    // not ported: isEmptyNoBufferUp (no stream buffer)
    // not ported: unconsume (nothing steps back)
    // not ported: nextIndexOf (consumeTo uses indexOf)
    // not ported: consumeRange (no buffer range)
    // not ported: rangeMatchesIgnoreCase (regionMatches does it)
    // not ported: containsIgnoreCase (nothing scans ahead)
    // not ported: matchesAny (matchesAnySorted is the one asked)
    // not ported: consumeToEnd (consumeTo ends the string)
    private class CharacterReader(private val charBuf: String) {
        private var bufPos = 0
        private var bufMark = -1

        fun mark() {
            bufMark = bufPos
        }

        fun unmark() {
            bufMark = -1
        }

        fun rewindToMark() {
            check(bufMark != -1) { "Mark invalid" }
            bufPos = bufMark
            unmark()
        }

        fun isEmpty(): Boolean = bufPos >= charBuf.length

        fun current(): Char = if (isEmpty()) EOF else charBuf[bufPos]

        fun consume(): Char {
            val value = if (isEmpty()) EOF else charBuf[bufPos]
            bufPos++
            return value
        }

        fun advance() {
            bufPos++
        }

        fun consumeTo(c: Char): String {
            val offset = charBuf.indexOf(c, bufPos)
            if (offset != -1) {
                val consumed = charBuf.substring(bufPos, offset)
                bufPos = offset
                return consumed
            }
            val data = charBuf.substring(bufPos)
            bufPos = charBuf.length
            return data
        }

        fun consumeToAny(chars: CharArray): String {
            val start = bufPos
            while (bufPos < charBuf.length && charBuf[bufPos] !in chars) bufPos++
            return charBuf.substring(start, bufPos)
        }

        private fun consumeMatching(test: (Char) -> Boolean): String {
            val start = bufPos
            while (bufPos < charBuf.length && test(charBuf[bufPos])) bufPos++
            return charBuf.substring(start, bufPos)
        }

        fun consumeLetterThenDigitSequence(): String {
            val start = bufPos
            while (bufPos < charBuf.length && isAsciiLetter(charBuf[bufPos])) bufPos++
            while (bufPos < charBuf.length && isDigit(charBuf[bufPos])) bufPos++
            return charBuf.substring(start, bufPos)
        }

        fun consumeHexSequence(): String = consumeMatching { isDigit(it) || it in 'a'..'f' || it in 'A'..'F' }

        fun consumeDigitSequence(): String = consumeMatching { it in '0'..'9' }

        fun matches(c: Char): Boolean = !isEmpty() && charBuf[bufPos] == c

        fun matches(seq: String): Boolean = charBuf.startsWith(seq, bufPos)

        fun matchesIgnoreCase(seq: String): Boolean = charBuf.regionMatches(bufPos, seq, 0, seq.length, ignoreCase = true)

        fun matchesAnySorted(seq: CharArray): Boolean = !isEmpty() && seq.binarySearch(charBuf[bufPos]) >= 0

        fun matchesAsciiAlpha(): Boolean = !isEmpty() && isAsciiLetter(charBuf[bufPos])

        fun matchesDigit(): Boolean = !isEmpty() && isDigit(charBuf[bufPos])

        fun matchConsume(seq: String): Boolean {
            if (matches(seq)) {
                bufPos += seq.length
                return true
            }
            return false
        }

        fun matchConsumeIgnoreCase(seq: String): Boolean {
            if (matchesIgnoreCase(seq)) {
                bufPos += seq.length
                return true
            }
            return false
        }

        private fun isAsciiLetter(c: Char) = c in 'A'..'Z' || c in 'a'..'z'
        private fun isDigit(c: Char) = c in '0'..'9'

        companion object {
            const val EOF = (-1).toChar()
        }
    }
}
