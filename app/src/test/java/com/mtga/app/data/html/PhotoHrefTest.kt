package com.mtga.app.data.html

import org.junit.Assert.assertEquals
import org.junit.Test

// Test protocol: JUnit 4 (https://github.com/junit-team/junit4). The photos are the anchor and img
// Nitter's renderPhotoAttachment writes, a(href=..., class="still-image", target="_blank") around
// genImg(thumb): https://github.com/zedeus/nitter/blob/master/src/views/tweet.nim#renderPhotoAttachment
class PhotoHrefTest {

    private fun page(attachments: String) = "<div class=\"timeline-item \" data-username=\"alice\">" +
        "<a class=\"tweet-link\" href=\"/alice/status/123#m\"></a><div class=\"tweet-body\">" +
        "<span class=\"tweet-date\"><a href=\"/alice/status/123#m\" title=\"Nov 5, 2026\">1h</a></span>" +
        "<div class=\"tweet-content media-body\" dir=\"auto\">hello</div>" +
        "<div class=\"attachments\"><div class=\"gallery-row\">$attachments</div></div></div></div>"

    private fun hrefFirst(name: String) =
        "<div class=\"attachment image\"><a href=\"/pic/orig/media%2F$name.jpg\" class=\"still-image\" target=\"_blank\">" +
            "<img src=\"/pic/media%2F$name.jpg%3Fname%3Dsmall\" alt=\"\" loading=\"lazy\"></a></div>"

    private fun classFirst(name: String) =
        "<div class=\"attachment image\"><a class=\"still-image\" target=\"_blank\" href=\"/pic/orig/media%2F$name.jpg\">" +
            "<img src=\"/pic/media%2F$name.jpg%3Fname%3Dsmall\" alt=\"\" loading=\"lazy\"></a></div>"

    private fun downloads(attachments: String) = HtmlTimelineParser().parse(page(attachments), "alice", "nitter.example")!!
        .posts.single().media.map { it.downloadUrl }

    private fun original(name: String) = "https://nitter.example/pic/orig/media%2F$name.jpg"

    @Test
    fun `two photos in a row keep their two addresses with the href before the class`() {
        assertEquals(listOf(original("first"), original("second")), downloads(hrefFirst("first") + hrefFirst("second")))
    }

    @Test
    fun `two photos in a row keep their two addresses with the class before the href`() {
        assertEquals(listOf(original("first"), original("second")), downloads(classFirst("first") + classFirst("second")))
    }

    @Test
    fun `the two attribute orders can follow each other`() {
        assertEquals(listOf(original("first"), original("second")), downloads(classFirst("first") + hrefFirst("second")))
        assertEquals(listOf(original("first"), original("second")), downloads(hrefFirst("first") + classFirst("second")))
    }
}
