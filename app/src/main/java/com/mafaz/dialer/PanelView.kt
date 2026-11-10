package com.mafaz.dialer

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class PanelView(
    ctx: Context,
    private val leftSide: Boolean,
    private val cc: String,
    private val onClose: () -> Unit
) : FrameLayout(ctx) {

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private val ACTION_BG = 0xFF37474F.toInt()
    private val ACTION_HOT = 0xFF00897B.toInt()

    private val ui = Handler(Looper.getMainLooper())
    private var recents: List<Person> = emptyList()
    private var all: List<Person> = emptyList()
    private var shown: List<Person> = emptyList()
    private var tab = 0
    private var query = ""

    private val list = ListView(ctx)
    private val empty = TextView(ctx)
    private val tabs = arrayOf(TextView(ctx), TextView(ctx))
    private val actions = ArrayList<Pair<TextView, (Person) -> Unit>>()

    private var dragPerson: Person? = null
    private var chip: TextView? = null
    private var hover: TextView? = null
    private var lastX = 0f
    private var lastY = 0f

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()