package com.jassemsendi.unpacked.eval

import com.jassemsendi.unpacked.agent.Answer
import com.jassemsendi.unpacked.agent.AskError
import com.jassemsendi.unpacked.agent.AskOutcome
import com.jassemsendi.unpacked.agent.unverifiedNumbers
import com.jassemsendi.unpacked.model.Result

/** How one question went: the answer stated the expected value, it did not, or it was flagged by the validator. */
enum class EvalStatus {
    Pass,
    Fail,

    /** The answer carried a number no Tool output holds; it counts as a failure whatever else it said. */
    Flagged,
}

data class EvalResult(
    val question: EvalQuestion,
    val status: EvalStatus,
    val detail: String,
)

/**
 * Scores one outcome against what the fixture says the answer must state. The check is deliberately mechanical and
 * lenient about everything but the value: an answer passes when the expected number appears among its numbers (days
 * and clock times set aside), or the expected words appear anywhere in it, so a right answer in any wording passes and
 * only a wrong or flagged one fails.
 */
fun judge(
    question: EvalQuestion,
    outcome: Result<AskOutcome, AskError>,
): EvalResult =
    when (outcome) {
        is Result.Error -> {
            EvalResult(question, EvalStatus.Fail, "no answer: ${outcome.error}")
        }

        is Result.Success -> {
            when (val ended = outcome.data) {
                is AskOutcome.StepsExhausted -> {
                    EvalResult(
                        question,
                        EvalStatus.Fail,
                        "stopped at the step cap after ${ended.trace.size} Tool calls",
                    )
                }

                is AskOutcome.Answered -> {
                    judgeAnswer(question, ended.answer)
                }
            }
        }
    }

private fun judgeAnswer(
    question: EvalQuestion,
    answer: Answer,
): EvalResult {
    val text = answer.text.oneLine()
    return when {
        !answer.isVerified -> {
            EvalResult(question, EvalStatus.Flagged, "unverified ${answer.unverifiedNumbers.joinToString()}: $text")
        }

        question.expected.isStatedIn(answer.text) -> {
            EvalResult(question, EvalStatus.Pass, text)
        }

        else -> {
            EvalResult(question, EvalStatus.Fail, "expected ${question.expected.describe()}; answer: $text")
        }
    }
}

/** A `YYYY-MM-DD` day or a clock time, whose digits are not quantities. */
private val DAY_OR_TIME = Regex("""\d{4}-\d{2}-\d{2}|\d{1,2}:\d{2}""")

/** An ordinal's suffix, so "22nd September" reads as "22 September". */
private val ORDINAL_SUFFIX = Regex("""(?<=\d)(st|nd|rd|th)\b""")

private fun Expected.isStatedIn(text: String): Boolean =
    when (this) {
        // The validator's own canonical comparison, turned around: the expected number must be "verified" by the answer.
        is Expected.Count -> unverifiedNumbers(value.toString(), listOf(text.replace(DAY_OR_TIME, " "))).isEmpty()

        is Expected.Words -> alternatives.any { it.normalised() in text.normalised() }

        is Expected.AllOf -> parts.all { it.isStatedIn(text) }

        is Expected.Day -> Expected.Words(spellings()).isStatedIn(text.replace(ORDINAL_SUFFIX, ""))
    }

private fun Expected.describe(): String =
    when (this) {
        is Expected.Count -> value.toString()
        is Expected.Words -> alternatives.joinToString(" or ")
        is Expected.AllOf -> parts.joinToString(" and ") { it.describe() }
        is Expected.Day -> date.toString()
    }

/** ISO, "22 September 2026", "September 22, 2026" and the same with the month abbreviated to three letters. */
private fun Expected.Day.spellings(): List<String> {
    val month = date.month.spelled()
    val months = listOf(month, month.take(MONTH_ABBREVIATION))
    return listOf(date.toString()) +
        months.flatMap { listOf("${date.day} $it ${date.year}", "$it ${date.day} ${date.year}") }
}

private const val MONTH_ABBREVIATION = 3

private fun String.normalised(): String = lowercase().filter { it.isLetterOrDigit() }

private fun String.oneLine(): String = trim().replace(Regex("""\s+"""), " ")

/** The results in question order, their score and the table the harness prints. */
class EvalReport(
    val results: List<EvalResult>,
) {
    val passed: Int = results.count { it.status == EvalStatus.Pass }

    /** Passes over questions, in whole percent; a flagged answer is not a pass. */
    val percent: Int = if (results.isEmpty()) 0 else passed * PERCENT / results.size

    fun render(): String {
        val width = results.maxOfOrNull { it.question.question.length } ?: 0
        val rows =
            results.mapIndexed { index, result ->
                listOf(
                    (index + 1).toString().padStart(2),
                    result.status.name.padEnd(STATUS_WIDTH),
                    result.question.topic.name
                        .padEnd(TOPIC_WIDTH),
                    result.question.question.padEnd(width),
                    result.detail.take(DETAIL_WIDTH),
                ).joinToString("  ")
            }
        val header =
            listOf(" #", "Status".padEnd(STATUS_WIDTH), "Topic".padEnd(TOPIC_WIDTH), "Question".padEnd(width), "Detail")
                .joinToString("  ")
        val score =
            "Score: $passed/${results.size} passed = $percent% (target $TARGET_PERCENT%); " +
                "flagged answers count as failures."
        return (listOf(header) + rows + score).joinToString("\n")
    }

    private companion object {
        const val PERCENT = 100
        const val STATUS_WIDTH = 7
        const val TOPIC_WIDTH = 11
        const val DETAIL_WIDTH = 160
    }
}
