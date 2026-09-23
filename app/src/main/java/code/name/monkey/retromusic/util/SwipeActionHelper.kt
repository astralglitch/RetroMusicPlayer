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
package code.name.monkey.retromusic.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A reusable left/right swipe-action callback for episode-list rows (see
 * docs/PODCAST_DESIGN.md's "Per-tab swipe actions" section). Each direction is independently
 * optional -- pass null to leave that direction inert. The row always snaps back after firing
 * ([onSwiped] never removes it itself); if the action ends up removing the underlying item (e.g.
 * delete-download, remove-from-queue), the resulting DiffUtil update on the next submitList()
 * handles that naturally instead.
 */
class SwipeActionHelper(
    private val leftIconRes: Int?,
    private val rightIconRes: Int?,
    private val onSwipeLeft: ((position: Int) -> Unit)?,
    private val onSwipeRight: ((position: Int) -> Unit)?
) : ItemTouchHelper.SimpleCallback(
    0,
    (if (onSwipeLeft != null) ItemTouchHelper.LEFT else 0) or
        (if (onSwipeRight != null) ItemTouchHelper.RIGHT else 0)
) {

    private var leftIcon: Drawable? = null
    private var rightIcon: Drawable? = null

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        when (direction) {
            ItemTouchHelper.LEFT -> onSwipeLeft?.invoke(position)
            ItemTouchHelper.RIGHT -> onSwipeRight?.invoke(position)
        }
        viewHolder.bindingAdapter?.notifyItemChanged(position)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val itemView = viewHolder.itemView
            val iconRes = if (dX > 0) rightIconRes else leftIconRes
            if (iconRes != null && dX != 0f) {
                val background = if (dX > 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
                c.drawRect(
                    itemView.left.toFloat(),
                    itemView.top.toFloat(),
                    itemView.left + dX,
                    itemView.bottom.toFloat(),
                    android.graphics.Paint().apply { color = background }
                )
                val icon = loadIcon(recyclerView, iconRes)
                if (icon != null) {
                    val iconSize = (itemView.height * 0.4f).roundToInt()
                    val iconMargin = (itemView.height - iconSize) / 2
                    val iconTop = itemView.top + iconMargin
                    val iconBottom = iconTop + iconSize
                    val iconLeft: Int
                    val iconRight: Int
                    if (dX > 0) {
                        iconLeft = itemView.left + iconMargin
                        iconRight = iconLeft + iconSize
                    } else {
                        iconRight = itemView.right - iconMargin
                        iconLeft = iconRight - iconSize
                    }
                    if (abs(dX) > iconMargin) {
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        icon.draw(c)
                    }
                }
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun loadIcon(recyclerView: RecyclerView, iconRes: Int): Drawable? {
        val cached = if (iconRes == leftIconRes) leftIcon else rightIcon
        if (cached != null) return cached
        val drawable = ContextCompat.getDrawable(recyclerView.context, iconRes)?.let {
            DrawableCompat.wrap(it).mutate().apply { DrawableCompat.setTint(this, Color.WHITE) }
        }
        if (iconRes == leftIconRes) leftIcon = drawable else rightIcon = drawable
        return drawable
    }
}
