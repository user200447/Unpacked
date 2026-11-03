package com.jassemsendi.unpacked.model

/**
 * One of the nine things GDPR Article 15 says a controller must provide on request (glossary: Article
 * item), in the article's own order. [article] is how the Access request names it.
 */
public enum class ArticleItem(
    public val article: String,
) {
    /** 15(1)(a): the purposes of the processing. */
    Purposes("15(1)(a)"),

    /** 15(1)(b): the categories of personal data concerned. */
    Categories("15(1)(b)"),

    /** 15(1)(c): the recipients the data has been or will be disclosed to. */
    Recipients("15(1)(c)"),

    /** 15(1)(d): how long the data will be stored, or how that period is decided. */
    RetentionPeriod("15(1)(d)"),

    /** 15(1)(e): the rights to rectification, erasure, restriction and objection. */
    RightsToRectifyAndErase("15(1)(e)"),

    /** 15(1)(f): the right to lodge a complaint with a supervisory authority. */
    RightToComplain("15(1)(f)"),

    /** 15(1)(g): where the data came from, when not from the user. */
    SourceOfData("15(1)(g)"),

    /** 15(1)(h): automated decision-making and profiling, with the logic involved. */
    AutomatedDecisions("15(1)(h)"),

    /** 15(3): a copy of the personal data undergoing processing. */
    CopyOfData("15(3)"),
}

/** How far an Import goes towards one Article item: what a Verdict says of it. Strongest first. */
public enum class Coverage {
    Covered,
    Partial,
    Missing,
}

/**
 * A deterministic statement, declared by a Source, of what in an Export counts toward one Article item
 * (glossary: Evidence rule): the presence of files matching a pattern, or of Events or Claims of a Kind.
 * [establishes] is how far that evidence goes when it is found: all the way to [Coverage.Covered], or only
 * to [Coverage.Partial] when the Source shows something of the item but not what the article asks for.
 * The item's Verdict is the strongest its satisfied rules establish, and Missing when none is.
 */
public sealed interface EvidenceRule {
    public val item: ArticleItem
    public val establishes: Coverage

    /**
     * Satisfied by any file of the Import whose path matches [pattern], where a star stands for any run of
     * characters and case does not matter: `*.json` is every JSON file; a star, a slash and a file name is that
     * file wherever it sits.
     */
    public data class FileNamed(
        override val item: ArticleItem,
        val pattern: String,
        override val establishes: Coverage = Coverage.Covered,
    ) : EvidenceRule {
        init {
            require(establishes != Coverage.Missing) { "found evidence cannot establish Missing" }
        }
    }

    /** Satisfied by at least one Event of [kind] in the Import. */
    public data class EventsOf(
        override val item: ArticleItem,
        val kind: EventKind,
        override val establishes: Coverage = Coverage.Partial,
    ) : EvidenceRule {
        init {
            require(establishes != Coverage.Missing) { "found evidence cannot establish Missing" }
        }
    }

    /** Satisfied by at least one Claim of [kind] in the Import. */
    public data class ClaimsOf(
        override val item: ArticleItem,
        val kind: ClaimKind,
        override val establishes: Coverage = Coverage.Partial,
    ) : EvidenceRule {
        init {
            require(establishes != Coverage.Missing) { "found evidence cannot establish Missing" }
        }
    }
}
