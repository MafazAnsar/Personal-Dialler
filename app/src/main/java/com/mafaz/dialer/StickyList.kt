package com.mafaz.dialer

import android.content.Context
import android.widget.ListView

// A list that keeps its bottom row in place when its height changes (selection bar, keypad,
// keyboard). Without this a bottom-up list keeps its top fixed and pushes the bottom rows out of view.
class BottomStickyList(ctx: Context) : ListView(ctx) {
    var sticky = false
    private var keep = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (sticky && oldh > 0 && h != oldh && count > 0 && childCount > 0) {
            val last = getChildAt(childCount - 1)
            keep = lastVisiblePosition == count - 1 && last.bottom >= oldh - 2
        }
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun layoutChildren() {
        super.layoutChildren()
        if (keep) {
            keep = false
            setSelection(count - 1)
        }
    }
}
