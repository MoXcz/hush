// SPDX-License-Identifier: GPL-3.0-or-later
package app.hush.desktop.usage

// Window classes of terminals, compared ignoring case.
private val TERMINAL_CLASSES =
    setOf(
        "kitty",
        "foot",
        "footclient",
        "alacritty",
        "org.wezfurlong.wezterm",
        "com.mitchellh.ghostty",
        "org.gnome.console",
        "org.gnome.terminal",
        "gnome-terminal-server",
        "konsole",
        "org.kde.konsole",
        "xterm",
        "st",
        "st-256color",
        "urxvt",
        "tilix",
        "com.gexperts.tilix",
    )

data class TerminalProgram(
    val name: String,
    val label: String,
)

// Only these are recorded, so a shell prompt, a path or another command never becomes a row.
private val PROGRAMS: Map<String, TerminalProgram> =
    listOf(
        listOf("nvim") to "Neovim",
        listOf("vim", "vi") to "Vim",
        listOf("hx", "helix") to "Helix",
        listOf("emacs") to "Emacs",
        listOf("nano") to "nano",
        listOf("micro") to "micro",
        listOf("kak") to "Kakoune",
        listOf("htop") to "htop",
        listOf("btop") to "btop",
        listOf("top") to "top",
        listOf("lazygit") to "lazygit",
        listOf("tig") to "tig",
        listOf("yazi") to "yazi",
        listOf("ranger") to "ranger",
        listOf("lf") to "lf",
        listOf("nnn") to "nnn",
        listOf("mc") to "Midnight Commander",
        listOf("less") to "less",
        listOf("man") to "man",
        listOf("ssh") to "ssh",
        listOf("tmux") to "tmux",
        listOf("zellij") to "zellij",
        listOf("gdb") to "gdb",
        listOf("python", "python3") to "Python",
        listOf("node") to "Node.js",
        listOf("ghci") to "GHCi",
        listOf("irb") to "IRB",
        listOf("psql") to "psql",
        listOf("sqlite3") to "SQLite",
        listOf("weechat") to "WeeChat",
        listOf("neomutt") to "NeoMutt",
        listOf("aerc") to "aerc",
        listOf("newsboat") to "Newsboat",
        listOf("cmus") to "cmus",
        listOf("ncspot") to "ncspot",
        listOf("w3m") to "w3m",
    ).flatMap { (names, label) -> names.map { it to TerminalProgram(names.first(), label) } }
        .toMap()

// Neovim's and Vim's own `set title` format: "notes.md (~/x) - NVIM".
private val VIM_TITLE_SUFFIXES = mapOf(" - NVIM" to "nvim", " - VIM" to "vim")

fun isTerminal(windowClass: String): Boolean = windowClass.lowercase() in TERMINAL_CLASSES

// Shells that set the title (e.g. kitty's shell integration) show the running command line, or the
// directory at the prompt. Only the program's name is taken, and only if it's listed; arguments and
// paths never are. Null keeps the time with the terminal.
fun programFromTitle(
    windowClass: String,
    title: String,
): TerminalProgram? {
    if (!isTerminal(windowClass)) return null
    val trimmed = title.trim()
    VIM_TITLE_SUFFIXES.forEach { (suffix, name) -> if (trimmed.endsWith(suffix)) return PROGRAMS[name] }
    val words = trimmed.split(Regex("""\s+"""))
    val command = (if (words.first() == "sudo") words.getOrNull(1) else words.first()) ?: return null
    return PROGRAMS[programName(command)]
}

// "/usr/bin/htop" is htop, but "~/src/nvim" is a directory (a prompt's title), not the program.
private fun programName(command: String): String? {
    if ('/' !in command) return command
    val directory = command.substringBeforeLast('/')
    return command.substringAfterLast('/').takeIf { directory.endsWith("/bin") || directory == "bin" }
}
