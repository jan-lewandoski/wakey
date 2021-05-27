package com.eps.wakey.fragments

import android.os.Bundle
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


class ActionBottomDialogFragment: BottomSheetDialogFragment() {
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
}