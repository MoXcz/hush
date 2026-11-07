// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiteTitlesTest {
    private fun zen(title: String) = siteFromTitle("zen", title)

    @Test
    fun `a YouTube video is YouTube`() {
        assertEquals("YouTube", zen("How to learn Kanji - YouTube — Zen Browser"))
    }

    @Test
    fun `a GitHub repository is GitHub`() {
        assertEquals("GitHub", zen("owner/repo: A short description · GitHub — Zen Browser"))
    }

    @Test
    fun `a GitHub pull request with several separators is GitHub`() {
        assertEquals("GitHub", zen("Fix the bug - by someone · Pull Request #12 · owner/repo · GitHub — Zen Browser"))
    }

    @Test
    fun `the last of several separators wins`() {
        assertEquals("Gmail", zen("Inbox (12) - me@example.com - Gmail — Zen Browser"))
        assertEquals("Stack Overflow", zen("kotlin - How to split a string | once - Stack Overflow — Zen Browser"))
    }

    @Test
    fun `a pipe and the en dash are separators`() {
        assertEquals("Boot.dev", zen("Learn AI Coding: What Is Context? | Boot.dev — Zen Browser"))
        assertEquals("Wikipedia", zen("Kanji – Wikipedia — Zen Browser"))
    }

    @Test
    fun `Helium and Chromium suffixes are stripped`() {
        assertEquals("Wikipedia", siteFromTitle("helium", "Kanji - Wikipedia - Helium"))
        assertEquals("YouTube", siteFromTitle("chromium", "Video - YouTube - Chromium"))
        assertEquals("YouTube", siteFromTitle("google-chrome", "Video - YouTube - Google Chrome"))
        assertEquals("YouTube", siteFromTitle("brave-browser", "Video - YouTube - Brave"))
    }

    @Test
    fun `Firefox suffixes are stripped`() {
        assertEquals("YouTube", siteFromTitle("firefox", "Video - YouTube — Mozilla Firefox"))
    }

    @Test
    fun `a private window gives no site`() {
        assertNull(siteFromTitle("firefox", "Video - YouTube — Mozilla Firefox Private Browsing"))
        assertNull(zen("Video - YouTube — Zen Browser Private Browsing"))
        assertNull(siteFromTitle("chromium", "Video - YouTube - Chromium (Incognito)"))
        assertNull(siteFromTitle("google-chrome", "Video - YouTube - Google Chrome (Incognito)"))
        assertNull(siteFromTitle("brave-browser", "Video - YouTube - Brave (Private)"))
        assertNull(siteFromTitle("microsoft-edge", "Video - YouTube - [InPrivate] - Microsoft Edge"))
    }

    @Test
    fun `domains and well-known multi-word names are sites`() {
        assertEquals("github.com", zen("Repo - github.com — Zen Browser"))
        assertEquals("docs.python.org", zen("os — Miscellaneous - docs.python.org — Zen Browser"))
        assertEquals("Hacker News", zen("Some story | Hacker News — Zen Browser"))
        assertEquals("Reddit", zen("Kotlin coroutines question - Reddit — Zen Browser"))
        assertEquals("Boot.dev Blog", zen("A post - Boot.dev Blog — Zen Browser"))
    }

    @Test
    fun `an email address is no site`() {
        assertNull(zen("Inbox - me@mail.com — Zen Browser"))
        assertNull(zen("Inbox (12) - me@example.com — Zen Browser"))
    }

    @Test
    fun `a person's name is no site`() {
        assertNull(zen("Sign in - John Smith — Zen Browser"))
        assertNull(zen("Profile | Ana María López — Zen Browser"))
        assertNull(zen("Chat - john — Zen Browser"))
    }

    @Test
    fun `a single word not on the site list is no site`() {
        assertNull(zen("Chat - Maria — Zen Browser"))
        assertNull(zen("Messenger - John — Zen Browser"))
        assertNull(zen("Inbox - Alice — Zen Browser"))
    }

    @Test
    fun `known one-word sites match ignoring case and keep the title's casing`() {
        assertEquals("youtube", zen("Video - youtube — Zen Browser"))
        assertEquals("IMDb", zen("Dune (2021) - IMDb — Zen Browser"))
        assertEquals("4chan", zen("Board - 4chan — Zen Browser"))
    }

    @Test
    fun `file names are no domain`() {
        assertNull(zen("Viewer - Report.pdf — Zen Browser"))
        assertNull(zen("File - notes.txt — Zen Browser"))
        assertNull(zen("Repo - README.md — Zen Browser"))
        assertNull(zen("Open - Report.pdf draft — Zen Browser"))
    }

    @Test
    fun `numbers and digit-heavy text are no site`() {
        assertNull(zen("Order - 12345 — Zen Browser"))
        assertNull(zen("Ticket - A1B2C3D4 — Zen Browser"))
        assertNull(zen("Call - +49 151 2345678 — Zen Browser"))
    }

    @Test
    fun `sentences and text with punctuation are no site`() {
        assertNull(zen("Draft - Meeting notes for Monday — Zen Browser"))
        assertNull(zen("Docs - Chapter: Intro — Zen Browser"))
        assertNull(zen("Path - home/user/notes — Zen Browser"))
        assertNull(zen("Tag - #focus — Zen Browser"))
    }

    @Test
    fun `a title without a separator stays with the browser`() {
        assertNull(siteFromTitle("helium", "75 Jahre Grundgesetz - Helium"))
        assertNull(zen("YouTube — Zen Browser"))
        assertNull(zen("New Tab — Zen Browser"))
    }

    @Test
    fun `the browser's own name or an empty title is no site`() {
        assertNull(zen("Zen Browser"))
        assertNull(zen(""))
        assertNull(zen("   "))
        assertNull(siteFromTitle("helium", "Helium"))
        assertNull(zen("Something - Mozilla Firefox — Zen Browser"))
    }

    @Test
    fun `a long last segment is part of the title, not a site`() {
        assertNull(zen("Intro - This is a very long sentence that is clearly a page title — Zen Browser"))
        val longest = "a".repeat(SITE_NAME_MAX_LENGTH - 4) + ".com"
        assertEquals(longest, zen("Intro - $longest — Zen Browser"))
    }

    @Test
    fun `a segment without letters is no site`() {
        assertNull(zen("Chapter 1 - 2 — Zen Browser"))
    }

    @Test
    fun `a separator at the start or end leaves nothing usable`() {
        assertNull(zen(" - — Zen Browser"))
        assertNull(zen("Title - — Zen Browser"))
    }

    @Test
    fun `a hyphen without spaces isn't a separator`() {
        assertNull(zen("Spider-Man — Zen Browser"))
    }

    @Test
    fun `titles without a browser suffix still give a site`() {
        assertEquals("YouTube", zen("Video - YouTube"))
    }

    @Test
    fun `only browser windows give sites`() {
        assertNull(siteFromTitle("md.obsidian.Obsidian", "AI tooling - notes - Obsidian 1.14.4"))
        assertNull(siteFromTitle("kitty", "vim - notes"))
        assertTrue(isBrowser("Zen"))
        assertFalse(isBrowser("kitty"))
    }
}
