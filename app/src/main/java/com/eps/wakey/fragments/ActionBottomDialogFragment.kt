package com.eps.wakey.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import com.eps.wakey.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import kotlinx.android.synthetic.main.bottom_sheet.*
import kotlinx.android.synthetic.main.bottom_sheet.view.*


class ActionBottomDialogFragment: BottomSheetDialogFragment(),  View.OnClickListener {

    private var mListener: ItemClickListener? = null

    override fun onClick(v: View?) {
            Log.d("SETTINGS", v.toString())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet, container, false)

        val sharedPref = activity?.getSharedPreferences("SETTINGS", AppCompatActivity.MODE_PRIVATE)

        view.switchPreview.isChecked = sharedPref?.getBoolean("WITH_PREVIEW", false)!!

        view.sensitivitySlider.value = sharedPref?.getFloat("EYE_TRACKING_SENSITIVITY", 0.3f)!!

        view.switchPreview?.setOnCheckedChangeListener { compoundButton: CompoundButton, b: Boolean ->
            val editor = sharedPref?.edit()
            editor.putBoolean("WITH_PREVIEW", switchPreview.isChecked)
            editor.apply()
        }

        view.sensitivitySlider?.addOnChangeListener(Slider.OnChangeListener { slider, value, fromUser ->
            val editor = sharedPref?.edit()
            editor.putFloat("EYE_TRACKING_SENSITIVITY", sensitivitySlider.value)
            editor.apply()
        })

        return view
    }

    // TODO: Add "restore default settings" button
    // TODO: Cleanup

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        switchPreview.setOnClickListener(this)
        sensitivitySlider.setOnClickListener(this)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mListener = if (context is ItemClickListener) {
            context
        } else {
            throw RuntimeException(context.toString() + " must implement ItemClickListener")
        }
    }

    override fun onDetach() {
        Log.d("SETTINGS", "Closing the sheet")
        super.onDetach()
        mListener = null
    }
}


interface ItemClickListener {
    fun onItemClick(value: Any?)
}