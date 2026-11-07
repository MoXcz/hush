// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

// Window classes of browsers whose titles end with the page's title (Hyprland reports them lowercase
// for these, but compare ignoring case).
private val BROWSER_CLASSES =
    setOf(
        "zen",
        "zen-browser",
        "zen-alpha",
        "zen-beta",
        "zen-twilight",
        "firefox",
        "firefox-esr",
        "firefoxdeveloperedition",
        "firefox-nightly",
        "librewolf",
        "waterfox",
        "floorp",
        "helium",
        "chromium",
        "google-chrome",
        "google-chrome-beta",
        "google-chrome-unstable",
        "brave-browser",
        "vivaldi-stable",
        "microsoft-edge",
        "thorium-browser",
    )

// What browsers append to the page title. Longest first, so a private window's suffix wins.
private val BROWSER_SUFFIXES =
    listOf(
        "Zen Browser Private Browsing",
        "Mozilla Firefox Private Browsing",
        "Zen Browser",
        "Mozilla Firefox",
        "Firefox Developer Edition",
        "Firefox Nightly",
        "LibreWolf",
        "Waterfox",
        "Floorp",
        "Helium",
        "Chromium",
        "Google Chrome",
        "Brave",
        "Vivaldi",
        "Microsoft Edge",
        "Thorium",
    ).sortedByDescending { it.length }

// Marks of a private window in Firefox-, Chromium- and Edge-style titles.
private val PRIVATE_MARKS = listOf("Private Browsing", "(Incognito)", "(Private)", "InPrivate")

private val SEPARATORS = listOf(" - ", " | ", " — ", " – ", " · ", " • ")

// Names of more than one word pass only from this list (or with a domain in them), so a person's name
// in a title ("Sign in - John Smith") isn't stored as a site.
private val MULTI_WORD_SITES =
    setOf(
        "stack overflow",
        "stack exchange",
        "super user",
        "server fault",
        "ask ubuntu",
        "hacker news",
        "product hunt",
        "khan academy",
        "arch linux",
        "arch wiki",
        "google docs",
        "google drive",
        "google sheets",
        "google slides",
        "google calendar",
        "google maps",
        "google search",
        "google translate",
        "proton mail",
        "mdn web docs",
        "kotlin documentation",
        "android developers",
        "visual studio code",
        "prime video",
    )

// One word passes only from this list (or as a domain), so a person's name ("Chat - Maria") isn't
// stored as a site.
private val ONE_WORD_SITES =
    setOf(
        "youtube",
        "reddit",
        "gmail",
        "github",
        "gitlab",
        "wikipedia",
        "twitter",
        "x",
        "facebook",
        "instagram",
        "linkedin",
        "netflix",
        "twitch",
        "discord",
        "slack",
        "whatsapp",
        "telegram",
        "outlook",
        "notion",
        "figma",
        "spotify",
        "amazon",
        "google",
        "duckduckgo",
        "bing",
        "medium",
        "substack",
        "duolingo",
        "anki",
        "coursera",
        "udemy",
        "chatgpt",
        "claude",
        "perplexity",
        "gemini",
        "kagi",
        "feedly",
        "pocket",
        "trello",
        "jira",
        "confluence",
        "todoist",
        "obsidian",
        "canva",
        "dropbox",
        "pinterest",
        "tumblr",
        "mastodon",
        "bluesky",
        "threads",
        "quora",
        "imdb",
        "letterboxd",
        "goodreads",
        "steam",
        "4chan",
    )

// File names look like domains ("Report.pdf"); these endings are never taken as a TLD, even where
// one is also a country code (.sh, .md, .rs, .py, .ts, .go).
private val FILE_EXTENSIONS =
    setOf(
        "pdf",
        "txt",
        "md",
        "doc",
        "docx",
        "xls",
        "xlsx",
        "ppt",
        "pptx",
        "csv",
        "json",
        "xml",
        "html",
        "htm",
        "png",
        "jpg",
        "jpeg",
        "gif",
        "svg",
        "webp",
        "mp3",
        "mp4",
        "mkv",
        "zip",
        "tar",
        "gz",
        "kt",
        "java",
        "py",
        "js",
        "ts",
        "rs",
        "go",
        "c",
        "h",
        "cpp",
        "sh",
        "log",
        "odt",
        "ods",
        "epub",
    )

// The top-level domain must be lowercase, as sites write it ("Boot.dev", "GitHub.com").
private val DOMAIN = Regex("""^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*\.[a-z]{2,24}$""")

// Characters a site's name may have; anything else (`@`, `:`, `/`, brackets, `#`...) is page text.
private val NAME_CHARACTERS = Regex("""^[\p{L}\p{N} .&'+!-]+$""")

private const val SITE_NAME_MAX_WORDS = 3

// Longer than this, the last segment is more likely part of the page title than a site's name.
const val SITE_NAME_MAX_LENGTH = 40

fun isBrowser(windowClass: String): Boolean = windowClass.lowercase() in BROWSER_CLASSES

// Most sites end their titles with their name ("How to learn Kanji - YouTube"); null keeps the time
// with the browser: not a browser, a private window, or a last segment that doesn't look like a site.
fun siteFromTitle(
    windowClass: String,
    title: String,
): String? {
    if (!isBrowser(windowClass)) return null
    val trimmed = title.trim()
    if (PRIVATE_MARKS.any { trimmed.contains(it, ignoreCase = true) }) return null
    val page = stripBrowserSuffix(trimmed)
    val (cut, separator) =
        SEPARATORS
            .map { page.lastIndexOf(it) to it }
            .filter { it.first > 0 }
            .maxByOrNull { it.first } ?: return null
    val site = page.substring(cut + separator.length).trim()
    return site.takeIf { isSiteName(it) }
}

private fun stripBrowserSuffix(title: String): String {
    for (suffix in BROWSER_SUFFIXES) {
        for (separator in SEPARATORS) {
            if (title.endsWith(separator + suffix)) return title.dropLast(separator.length + suffix.length).trim()
        }
        if (title == suffix) return ""
    }
    return title
}

// A domain ("boot.dev"), a known one-word name ("YouTube"), or a known multi-word name; never mostly
// digits. When in doubt, no site.
internal fun isSiteName(text: String): Boolean {
    if (text.isEmpty() || text.length > SITE_NAME_MAX_LENGTH) return false
    if (!NAME_CHARACTERS.matches(text) || BROWSER_SUFFIXES.any { it.equals(text, ignoreCase = true) }) return false
    val letters = text.count { it.isLetter() }
    val digits = text.count { it.isDigit() }
    if (letters == 0 || digits * 2 > letters) return false
    val words = text.split(' ').filter { it.isNotEmpty() }
    return when {
        words.size == 1 -> text.lowercase() in ONE_WORD_SITES || isDomain(text)
        words.size > SITE_NAME_MAX_WORDS -> false
        else -> text.lowercase() in MULTI_WORD_SITES || words.any { isDomain(it) }
    }
}

private fun isDomain(word: String): Boolean = DOMAIN.matches(word) && word.substringAfterLast('.') !in FILE_EXTENSIONS
