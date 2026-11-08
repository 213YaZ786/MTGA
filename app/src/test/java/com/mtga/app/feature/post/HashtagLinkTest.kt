package com.mtga.app.feature.post

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). A hashtag and a cashtag are the
// slices Nitter's extractHashtags collects from the text, #Artemis and $TSLA:
// https://github.com/zedeus/nitter/blob/master/src/parserutils.nim#extractHashtags
class HashtagLinkTest {

    private val link = Color(0xFF3366FF)

    private fun tags(text: String): List<String> {
        val found = mutableListOf<IntRange>()
        extractHashtags(found, text)
        return found.map { text.substring(it.first, it.last + 1) }
    }

    @Test
    fun `hashtags and cashtags are found, a glued sign, a number and an address are not`() {
        assertEquals(
            listOf("#Artemis", "\$TSLA", "#été"),
            tags("Follow #Artemis and \$TSLA, then #été.")
        )
        assertEquals(emptyList<String>(), tags("Room #1 costs \$5, see example.com/page#top and a&#38;b, US\$100, mail@x.com#tag"))
    }

    @Test
    fun `linkify opens Search with the tag as written, and a mention still opens the profile`() {
        val opened = mutableListOf<String>()
        val mentioned = mutableListOf<String>()
        val text = "Hello @nasa, #Artemis and \$TSLA"
        val annotated = linkify(text, emptyList(), link, { mentioned += it }, { opened += it })

        for (range in annotated.getLinkAnnotations(0, text.length)) {
            val clickable = range.item as LinkAnnotation.Clickable
            clickable.linkInteractionListener?.onClick(clickable)
        }

        assertEquals(listOf("#Artemis", "\$TSLA"), opened)
        assertEquals(listOf("nasa"), mentioned)
        assertEquals(text, annotated.text)
    }

    @Test
    fun `the post card colours the tags and leaves the rest of the text`() {
        val text = "Follow #Artemis today"
        val coloured = colourHashtags(text, link)

        assertEquals(text, coloured.text)
        val spans = coloured.spanStyles
        assertEquals(1, spans.size)
        assertEquals("#Artemis", text.substring(spans[0].start, spans[0].end))
        assertEquals(link, spans[0].item.color)
    }
}
