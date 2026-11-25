package com.jassemsendi.unpacked.agent

// The citation validator (spec story 35): every number in an answer must appear in some Tool output the model saw,
// read as the model wrote it and compared in a canonical form, so that "1,200" matches 1200 and "3.0" matches 3. A
// citation marker such as [3] names a Provenance, not a quantity, so it is never counted as a number.

/** A citation marker as an answer writes it, `[3]`, its number in the first group. */
public val CITATION_MARKER: Regex = Regex("""\[(\d+)]""")
private val NUMBER = Regex("""\d+(?:[.,]\d+)*""")
private val THOUSANDS = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?""")

/** The citation markers an answer carries, each once, in the order they first appear. */
public fun citationMarkersIn(text: String): List<Int> =
    CITATION_MARKER
        .findAll(text)
        .map { it.groupValues[1].toInt() }
        .distinct()
        .toList()

/** The numbers in a text as written, citation markers left out, each once, in the order they first appear. */
public fun numbersIn(text: String): List<String> =
    NUMBER
        .findAll(text.replace(CITATION_MARKER, " "))
        .map { it.value }
        .distinct()
        .toList()

/**
 * The numbers of [answer] that appear in none of [toolOutputs], as the answer wrote them. Empty when the answer is
 * verified. Compared canonically, so a difference of formatting alone does not fail an answer.
 */
public fun unverifiedNumbers(
    answer: String,
    toolOutputs: List<String>,
): List<String> {
    val known = toolOutputs.flatMap(::numbersIn).mapTo(HashSet(), ::canonicalNumber)
    return numbersIn(answer).filter { canonicalNumber(it) !in known }
}

/**
 * One spelling for one quantity: thousands separators dropped, a decimal comma read as a point, trailing zeros of a
 * decimal and leading zeros of an integer dropped. A token with several points, such as a version, is left as it is.
 */
internal fun canonicalNumber(token: String): String {
    val plain =
        when {
            THOUSANDS.matches(token) -> token.replace(",", "")
            token.contains(',') && !token.contains('.') && token.count { it == ',' } == 1 -> token.replace(',', '.')
            else -> token
        }
    if (plain.count { it == '.' } > 1 || plain.contains(',')) return plain
    val (whole, fraction) = plain.split('.').let { it[0] to it.getOrNull(1) }
    val integer = whole.trimStart('0').ifEmpty { "0" }
    val decimals = fraction?.trimEnd('0').orEmpty()
    return if (decimals.isEmpty()) integer else "$integer.$decimals"
}
