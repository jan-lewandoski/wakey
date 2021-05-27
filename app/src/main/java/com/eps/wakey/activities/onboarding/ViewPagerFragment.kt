package com.eps.wakey.activities.onboarding

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.eps.wakey.R
import com.eps.wakey.activities.onboarding.screens.*
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.fragment_view_pager.*
import kotlinx.android.synthetic.main.fragment_view_pager.view.*

class ViewPagerFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_view_pager, container, false)

        val fragmentList = arrayListOf<Fragment>(
            Welcome(),
            About(),
            EyeTracking(),
            Permissions(),
            TimeToDrive()
        )

        val adapter = ViewPagerAdapter(
            fragmentList,
            requireActivity().supportFragmentManager,
            lifecycle
        )

        // TODO: Change options to "About wakey"
        // TODO: Fix hard coded strings in each screen

        view.viewPager.adapter = adapter

        TabLayoutMediator(view.tab_layout, view.viewPager) {
            tab, position ->
        }.attach()

        view.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    0 -> {
                        view.buttonBack.visibility = View.INVISIBLE
                        view.buttonNext.visibility = View.VISIBLE
                        view.buttonDone.visibility = View.GONE
                    }
                    fragmentList.size - 1 -> {
                        view.buttonBack.visibility = View.VISIBLE
                        view.buttonDone.visibility = View.VISIBLE
                        view.buttonNext.visibility = View.GONE
                    }
                    else -> {
                        view.buttonBack.visibility = View.VISIBLE
                        view.buttonNext.visibility = View.VISIBLE
                        view.buttonDone.visibility = View.GONE
                    }
                }
                super.onPageSelected(position)
            }
        })

        view.buttonBack?.setOnClickListener {
            val current = view.viewPager.currentItem
            viewPager.currentItem = current - 1
        }

        view.buttonNext?.setOnClickListener {
            val current = view.viewPager.currentItem
            viewPager.currentItem = current + 1
        }

        view.buttonDone?.setOnClickListener {
            findNavController().navigate(R.id.action_viewPagerFragment_to_homeActivity)
            onboardingFinished()
        }

        return view
    }

    private fun onboardingFinished() {
        val sharedPref = requireActivity().getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putBoolean("Finished", true)
        editor.apply()
    }

}