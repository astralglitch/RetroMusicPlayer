/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package code.name.monkey.retromusic.podcast

import android.text.Spanned
import android.text.SpannableStringBuilder

/**
 * Trims leading/trailing whitespace without discarding spans -- `CharSequence.trim()` only
 * exists for `String`, and calling `.toString()` first (as this app used to, on a
 * `parseAsHtml()` result) throws away every link/formatting span the HTML parse just produced.
 */
fun Spanned.trimSpanned(): Spanned {
    var start = 0
    var end = length
    while (start < end && this[start].isWhitespace()) start++
    while (end > start && this[end - 1].isWhitespace()) end--
    return SpannableStringBuilder(this, start, end)
}
