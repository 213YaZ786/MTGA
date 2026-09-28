package com.mtga.app.data.accounts

import com.mtga.app.core.model.FollowedAccount
import com.mtga.app.core.model.FollowedAccount.Companion.MAIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionCodecTest {

    @Test
    fun `folders survive an export and an import`() {
        val accounts = listOf(
            FollowedAccount("nasa", folder = "Science"),
            FollowedAccount("esa", folder = "Science"),
            FollowedAccount("nytimes", folder = "News"),
            FollowedAccount("someone")
        )
        val file = SubscriptionCodec.export(accounts, listOf(MAIN, "News", "Science", "Empty"), nowMillis = 0)

        val read = SubscriptionCodec.import(file)

        assertEquals(
            listOf(
                SubscriptionCodec.Entry("nasa", "Science"),
                SubscriptionCodec.Entry("esa", "Science"),
                SubscriptionCodec.Entry("nytimes", "News"),
                SubscriptionCodec.Entry("someone", null)
            ),
            read
        )
        // Main is never a group, an empty folder still is.
        assertTrue("\"name\":\"Empty\"" in file)
        assertTrue("\"name\":\"Main\"" !in file)
    }

    /** Fritter keys accounts by numeric user id, and members refer to those. */
    @Test
    fun `reads groups from a Fritter file keyed by numeric ids`() {
        val file = """
            {"subscriptions":[
              {"id":"11348282","screen_name":"NASA","name":"NASA"},
              {"id":"807095","screen_name":"nytimes","name":"The New York Times"}],
             "subscriptionGroups":[
              {"id":"g1","name":"Space","icon":"rocket","color":null,"created_at":"2024-01-01T00:00:00.000"},
              {"id":"g2","name":"Other","icon":"","color":null,"created_at":"2024-01-01T00:00:00.000"}],
             "subscriptionGroupMembers":[
              {"group_id":"g1","profile_id":"11348282"},
              {"group_id":"g2","profile_id":"11348282"}]}
        """.trimIndent()

        assertEquals(
            listOf(SubscriptionCodec.Entry("NASA", "Space"), SubscriptionCodec.Entry("nytimes", null)),
            SubscriptionCodec.import(file)
        )
    }

    @Test
    fun `a post link in plain text gives its author, never the post id`() {
        assertEquals(
            listOf(SubscriptionCodec.Entry("nasa"), SubscriptionCodec.Entry("esa")),
            SubscriptionCodec.import("https://x.com/nasa/status/2104470073905713351\n@esa")
        )
    }
}
