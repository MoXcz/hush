# SPDX-License-Identifier: GPL-3.0-or-later
# Room creates the generated database class by its no-arg constructor (AGENTS.md §11).
-keep class * extends androidx.room3.RoomDatabase { <init>(); }
