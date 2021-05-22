package com.eps.wakey.activities.onboarding.screens

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager2.widget.ViewPager2
import com.eps.wakey.R
import kotlinx.android.synthetic.main.fragment_view_pager.view.*
import kotlinx.android.synthetic.main.fragment_welcome.view.*

class Welcome : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_welcome, container, false)

        val viewPager = activity?.findViewById<ViewPager2>(R.id.viewPager)

        view.buttonGetStarted.setOnClickListener {
            viewPager?.currentItem = 1
        }

        return view
    }
}