package com.mtga.app.feature.settings

import com.mtga.app.data.settings.Settings

/**
 * The sources as the reader sees them, in Settings and in the guide: one
 * switch each, all on by default. How they follow each other is in
 * data/repository/Sources.
 */
data class SourceOption(
    val title: String,
    val summary: String,
    val isOn: (Settings) -> Boolean,
    val set: (Settings, Boolean) -> Settings
)

val SOURCE_OPTIONS = listOf(
    SourceOption(
        title = "Newest posts from X",
        summary = "The latest posts of an account, fastest. X sees your IP address.",
        isOn = { it.useXcomDirect },
        set = { s, on -> s.copy(useXcomDirect = on) }
    ),
    SourceOption(
        title = "FxTwitter",
        summary = "Whole timelines page after page, and conversations. FxTwitter sees which accounts you read.",
        isOn = { it.useFxTwitter },
        set = { s, on -> s.copy(useFxTwitter = on) }
    ),
    SourceOption(
        title = "Nitter servers",
        summary = "Volunteer servers, often behind a bot check. They see which accounts you read.",
        isOn = { it.useNitter },
        set = { s, on -> s.copy(useNitter = on) }
    ),
    SourceOption(
        title = "Internet Archive",
        summary = "Older posts the archive saved, each read from X.",
        isOn = { it.olderFromArchives },
        set = { s, on -> s.copy(olderFromArchives = on) }
    ),
    SourceOption(
        title = "DuckDuckGo",
        summary = "Recent posts found by search, each read from X.",
        isOn = { it.useDuckDuckGo },
        set = { s, on -> s.copy(useDuckDuckGo = on) }
    )
)
