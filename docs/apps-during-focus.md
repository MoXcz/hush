# Apps during focus

During work phases Hush records which apps were in front, and Stats shows them under "Apps during
focus".

On Android this needs usage access, which you turn on yourself in Settings → Timer → App usage during
focus; without it nothing is recorded. The desktop app does the same on Hyprland once you turn on
Settings → Timer → App usage during focus (off by default): while a work phase runs it notes the
focused window; for a browser it guesses the site from the window
title ("… - YouTube" counts as YouTube), and for a terminal the program ("nvim notes.md" counts as
Neovim, from a short list of known programs). It never reads URLs and keeps only the site's or
program's name.

[engineering.md](engineering.md) §10 ("Apps during focus") has the full rules.
