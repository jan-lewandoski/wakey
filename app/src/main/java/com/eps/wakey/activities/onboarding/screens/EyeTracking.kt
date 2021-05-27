package com.eps.wakey.activities.onboarding.screens

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.eps.wakey.R
import kotlinx.android.synthetic.main.fragment_eye_tracking.view.*

class EyeTracking : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.fragment_eye_tracking, container, false)
    }

}