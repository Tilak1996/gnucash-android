/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.colorpicker

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import androidx.fragment.app.DialogFragment
import org.gnucash.android.R
import org.gnucash.android.ui.colorpicker.ColorPickerSwatch.OnColorSelectedListener

/**
 * A dialog which takes in as input an array of colors and creates a palette allowing the user to
 * select a specific color swatch, which invokes a listener.
 */
class ColorPickerDialog : DialogFragment(), OnColorSelectedListener {
    protected var mAlertDialog: AlertDialog? = null

    protected var mTitleResId: Int = R.string.color_picker_default_title
    protected var mColors: IntArray? = null
    protected var mSelectedColor: Int = 0
    protected var mColumns: Int = 0
    protected var mSize: Int = 0

    private var mPalette: ColorPickerPalette? = null
    private var mProgress: ProgressBar? = null

    protected var mListener: OnColorSelectedListener? = null

    fun initialize(
        titleResId: Int,
        colors: IntArray?,
        selectedColor: Int,
        columns: Int,
        size: Int
    ) {
        setArguments(titleResId, columns, size)
        setColors(colors, selectedColor)
    }

    fun setArguments(titleResId: Int, columns: Int, size: Int) {
        val bundle = Bundle()
        bundle.putInt(KEY_TITLE_ID, titleResId)
        bundle.putInt(KEY_COLUMNS, columns)
        bundle.putInt(KEY_SIZE, size)
        setArguments(bundle)
    }

    fun setOnColorSelectedListener(listener: OnColorSelectedListener?) {
        mListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (getArguments() != null) {
            mTitleResId = requireArguments().getInt(KEY_TITLE_ID)
            mColumns = requireArguments().getInt(KEY_COLUMNS)
            mSize = requireArguments().getInt(KEY_SIZE)
        }

        if (savedInstanceState != null) {
            mColors = savedInstanceState.getIntArray(KEY_COLORS)
            mSelectedColor =
                (savedInstanceState.getSerializable(org.gnucash.android.ui.colorpicker.ColorPickerDialog.Companion.KEY_SELECTED_COLOR) as Int?)!!
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity: Activity? = getActivity()

        val view = LayoutInflater.from(getActivity()).inflate(R.layout.color_picker_dialog, null)
        mProgress = view.findViewById<View?>(android.R.id.progress) as ProgressBar?
        mPalette = view.findViewById<View?>(R.id.color_picker) as ColorPickerPalette?
        mPalette!!.init(mSize, mColumns, this)

        if (mColors != null) {
            showPaletteView()
        }

        mAlertDialog = AlertDialog.Builder(activity)
            .setTitle(mTitleResId)
            .setView(view)
            .create()

        return mAlertDialog!!
    }

    override fun onColorSelected(color: Int) {
        if (mListener != null) {
            mListener!!.onColorSelected(color)
        }

        if (getTargetFragment() is OnColorSelectedListener) {
            val listener =
                getTargetFragment() as OnColorSelectedListener?
            listener!!.onColorSelected(color)
        }

        if (color != mSelectedColor) {
            mSelectedColor = color
            // Redraw palette to show checkmark on newly selected color before dismissing.
            mPalette!!.drawPalette(mColors, mSelectedColor)
        }

        dismiss()
    }

    fun showPaletteView() {
        if (mProgress != null && mPalette != null) {
            mProgress!!.setVisibility(View.GONE)
            refreshPalette()
            mPalette!!.setVisibility(View.VISIBLE)
        }
    }

    fun showProgressBarView() {
        if (mProgress != null && mPalette != null) {
            mProgress!!.setVisibility(View.VISIBLE)
            mPalette!!.setVisibility(View.GONE)
        }
    }

    fun setColors(colors: IntArray?, selectedColor: Int) {
        if (mColors != colors || mSelectedColor != selectedColor) {
            mColors = colors
            mSelectedColor = selectedColor
            refreshPalette()
        }
    }

    private fun refreshPalette() {
        if (mPalette != null && mColors != null) {
            mPalette!!.drawPalette(mColors, mSelectedColor)
        }
    }

    var colors: IntArray?
        get() = mColors
        set(colors) {
            if (mColors != colors) {
                mColors = colors
                refreshPalette()
            }
        }

    var selectedColor: Int
        get() = mSelectedColor
        set(color) {
            if (mSelectedColor != color) {
                mSelectedColor = color
                refreshPalette()
            }
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(KEY_COLORS, mColors)
        outState.putSerializable(KEY_SELECTED_COLOR, mSelectedColor)
    }

    companion object {
        const val SIZE_LARGE: Int = 1
        const val SIZE_SMALL: Int = 2

        protected const val KEY_TITLE_ID: String = "title_id"
        protected const val KEY_COLORS: String = "colors"
        protected const val KEY_SELECTED_COLOR: String = "selected_color"
        protected const val KEY_COLUMNS: String = "columns"
        protected const val KEY_SIZE: String = "size"

        fun newInstance(
            titleResId: Int, colors: IntArray?, selectedColor: Int,
            columns: Int, size: Int
        ): ColorPickerDialog {
            val ret = ColorPickerDialog()
            ret.initialize(titleResId, colors, selectedColor, columns, size)
            return ret
        }
    }
}