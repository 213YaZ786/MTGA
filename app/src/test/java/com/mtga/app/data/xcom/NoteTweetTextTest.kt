package com.mtga.app.data.xcom

import com.mtga.app.core.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures copied from a real logged out x.com profile page, September 2026.
 * The record shape is the point: rest_id sits between __typename and text,
 * which is exactly what the previous regex could not cross.
 */
class NoteTweetTextTest {

    @Test
    fun `reads a note text written after rest_id`() {
        val texts = NoteTweetText.texts(PAGE)

        assertEquals(2, texts.size)
        assertTrue(texts[0].startsWith("BEAUVAIS : UN HOMME"))
        assertTrue(texts[0].endsWith("est décédé peu après."))
        assertTrue(texts[0].contains("\n\n"))
    }

    @Test
    fun `completes a cut post from the page`() {
        val cut = post("BEAUVAIS : UN HOMME ARMÉ ABATTU PAR LA POLICE\n\nLors de son interpellation, il aurait ouvert")

        val completed = NoteTweetText.complete(listOf(cut), PAGE).single()

        assertTrue(completed.text.endsWith("est décédé peu après."))
    }

    @Test
    fun `leaves a post the page knows nothing about alone`() {
        val other = post("PARIS : UN BUTIN ESTIMÉ À 1 MILLION D’EUROS, aucun rapport avec la page")

        assertEquals(other, NoteTweetText.complete(listOf(other), PAGE).single())
    }

    @Test
    fun `leaves a post alone when the page carries no note at all`() {
        val cut = post("BEAUVAIS : UN HOMME ARMÉ ABATTU PAR LA POLICE, texte coupé ici")

        assertEquals(cut, NoteTweetText.complete(listOf(cut), "<html>rien du tout</html>").single())
    }

    @Test
    fun `reads the quoted key spelling of the initial payload`() {
        val json = """{"__typename":"NoteTweet","rest_id":"1","text":"assez long pour ne pas être un hasard, vraiment"}"""

        assertEquals(
            listOf("assez long pour ne pas être un hasard, vraiment"),
            NoteTweetText.texts(json)
        )
    }

    @Test
    fun `ignores the wrapper records that carry no text`() {
        val wrappers =
            """__typename:"NoteTweetResults",id:"x",__typename:"NoteTweetData",is_expandable:!0"""

        assertEquals(emptyList<String>(), NoteTweetText.texts(wrappers))
    }

    @Test
    fun `does not mistake full_text for text`() {
        val record =
            "__typename:\"NoteTweet\",rest_id:\"1\",full_text:\"coupé\",text:\"entier et bien plus long\""

        assertEquals(listOf("entier et bien plus long"), NoteTweetText.texts(record))
    }

    /**
     * Shape of the logged out profile payload in late September 2026: the
     * timeline arrives inline as a streamed UserOriginalsTimeline response,
     * and entity_set, with the links of the post, sits before text. 2.9.2
     * stopped at its brace and left every such post cut.
     */
    @Test
    fun `reads a note text written after entity_set`() {
        val record = """
            note_tweet:${'$'}R[189]={is_expandable:!0,note_tweet_results:${'$'}R[190]={id:"Tm90ZQ==",
            result:${'$'}R[191]={__typename:"NoteTweet",entity_set:${'$'}R[192]={hashtags:${'$'}R[193]=[],
            smarttags:${'$'}R[194]=[],symbols:${'$'}R[195]=[],urls:${'$'}R[196]=[${'$'}R[197]={
            display_url:"x.com/someone/stat…",expanded_url:"https://x.com/someone/status/1/video/1",
            indices:${'$'}R[198]=[382,405],url:"https://t.co/abc"}],user_mentions:${'$'}R[199]=[]},
            id:"Tm90ZVR3ZWV0",rest_id:"2104470073733763072",
            text:"A long post that goes on past the cut, with a brace } and a quote \" inside.\nhttps://t.co/abc"}}}
        """.trimIndent()

        assertEquals(
            listOf("A long post that goes on past the cut, with a brace } and a quote \" inside.\nhttps://t.co/abc"),
            NoteTweetText.texts(record)
        )
    }

    @Test
    fun `completes a cut post whose note carries links`() {
        val cut = post("A long post that goes on past the cut, with a brace")
        val record = """
            result:{__typename:"NoteTweet",entity_set:{urls:[{url:"https://t.co/abc",indices:[1,2]}]},
            rest_id:"1",text:"A long post that goes on past the cut, with a brace and the rest of it."}
        """.trimIndent()

        assertEquals(
            "A long post that goes on past the cut, with a brace and the rest of it.",
            NoteTweetText.complete(listOf(cut), record).single().text
        )
    }

    @Test
    fun `gives up on a record that closes without text`() {
        val record = """__typename:"NoteTweet",entity_set:{urls:[]},rest_id:"1"},text:"not this one""""

        assertEquals(emptyList<String>(), NoteTweetText.texts(record))
    }

    @Test
    fun `unescapes what javascript escaped`() {
        assertEquals("a\nb\tc\"d\\e", NoteTweetText.unescape("""a\nb\tc\"d\\e"""))
        assertEquals("é", NoteTweetText.unescape("""\u00e9"""))
    }

    private fun post(text: String) = Post(
        id = "2101308476563554618",
        authorHandle = "cpasdeslol_X",
        authorName = "Cpasdeslol",
        text = text,
        publishedAtMillis = 1_789_825_955_000,
        permalink = "https://x.com/cpasdeslol_X/status/2101308476563554618"
    )

    private companion object {
        /**
         * Two note records, the first surrounded by the neighbouring fields of
         * the real payload so the brace guard and the window are exercised.
         */
        private val PAGE = """
            <script>(${'$'}R=>${'$'}R[58].next(${'$'}R[60]={relayRecords:{
            "Tm90ZVR3ZWV0OjIxMDEzMDg0NzY0ODgxMzA1NjE=":${'$'}R[197]={
            __id:"Tm90ZVR3ZWV0OjIxMDEzMDg0NzY0ODgxMzA1NjE=",__typename:"NoteTweetResults",
            result:{__ref:"Tm90ZVR3ZWV0OjIxMDEzMDg0NzY0ODgxMzA1NjE="}},
            "Tm90ZVR3ZWV0OjIx":${'$'}R[198]={__id:"Tm90ZVR3ZWV0OjIx",__typename:"NoteTweet",
            rest_id:"2101308476488130561",text:"BEAUVAIS : UN HOMME ARMÉ ABATTU PAR LA POLICE\n\nLors de son interpellation, il aurait ouvert le feu à plusieurs reprises, blessant un policier au bras. Touché par balles, l’homme est décédé peu après.",
            entity_set:{__ref:"client:Tm90ZVR3ZWV0OjIx:entity_set"},id:"Tm90ZVR3ZWV0OjIx"},
            "Tm90ZVR3ZWV0OjIy":${'$'}R[269]={__id:"Tm90ZVR3ZWV0OjIy",__typename:"NoteTweet",
            rest_id:"2101300648721477633",text:"NIORT : UN REFUS D’OBTEMPÉRER SE TERMINE CONTRE UN POTEAU. Les deux passagers ont été blessés.",
            entity_set:{__ref:"client:Tm90ZVR3ZWV0OjIy:entity_set"}}}}))</script>
        """.trimIndent()
    }
}
