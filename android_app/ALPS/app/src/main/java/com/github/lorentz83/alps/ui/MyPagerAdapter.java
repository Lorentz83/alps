/**
 *  Copyright 2020-2021 Lorenzo Bossi
 *
 *  This file is part of ALPS (Another Light Painting Stick).
 *
 *  ALPS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ALPS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with ALPS.  If not, see <https://www.gnu.org/licenses/>.
 */


package com.github.lorentz83.alps.ui;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.github.lorentz83.alps.R;
import com.github.lorentz83.alps.ui.fragments.GalleryFragment;
import com.github.lorentz83.alps.ui.fragments.PreviewFragment;
import com.github.lorentz83.alps.ui.fragments.SettingsFragment;
import com.github.lorentz83.alps.utils.LogUtility;

/**
 * Instantiates and handles the switch between the fragments in the tab view.
 */
public class MyPagerAdapter extends FragmentPagerAdapter {
    private final static LogUtility log = new LogUtility(MyPagerAdapter.class);

    private final TitledFragment[] _fragments;

    private final SettingsFragment _settingsFragment;
    private final PreviewFragment _previewFragment;
    private final GalleryFragment _galleryFragment;

    private ViewPager _viewPager;

    /**
     * Instantiates all the fragments required by the main activity in the app.
     *
     * @param context
     * @param fm
     */
    public MyPagerAdapter(@NonNull Context context, @NonNull FragmentManager fm) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);

        _previewFragment = new PreviewFragment();
        _settingsFragment = new SettingsFragment();
        _galleryFragment = new GalleryFragment();

        _fragments = new TitledFragment[]{
                new TitledFragment(context.getResources().getString(R.string.preview_tab), _previewFragment),
                new TitledFragment(context.getResources().getString(R.string.settings_tab), _settingsFragment),
                new TitledFragment(context.getResources().getString(R.string.gallery_tab), _galleryFragment),
        };
    }

    /**
     * Returns the preview fragment.
     *
     * @return the preview fragment.
     */
    public @NonNull
    PreviewFragment getPreviewFragment() {
        return _previewFragment;
    }

    /**
     * Switches the tab to the preview fragment.
     */
    public void switchToPreviewFragment() {
        int id = 0;
        for (int i = 0; i < _fragments.length; i++) {
            if (_fragments[i].fragment == _previewFragment) {
                id = i;
                break;
            }
        }
        _viewPager.setCurrentItem(id, true);
    }

    @Override
    public Fragment getItem(int position) {
        return _fragments[position].fragment;
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return _fragments[position].title;
    }

    @Override
    public int getCount() {
        return _fragments.length;
    }

    /**
     * Links this instance to the ViewPager that handles the tab to expose the tab switch functionality.
     *
     * @param viewPager the view pager.
     */
    public void linkTo(@NonNull ViewPager viewPager) {
        viewPager.setAdapter(this);
        _viewPager = viewPager;

        _viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                if (_fragments[position].fragment == _previewFragment) {
                    // TODO: it would be nice to decouple better this.
                    _previewFragment.updatePreview();
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
    }
}


class TitledFragment {
    public final String title;
    public final Fragment fragment;

    public TitledFragment(@NonNull String title, @NonNull Fragment fragment) {
        this.fragment = fragment;
        this.title = title;
    }
}