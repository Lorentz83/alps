/**
 *  Copyright 2020 Lorenzo Bossi
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


package com.github.lorentz83.alps.ui.fragments;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.github.lorentz83.alps.MainActivity;
import com.github.lorentz83.alps.R;
import com.github.lorentz83.alps.utils.LogUtility;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class GalleryFragment extends Fragment {
    private final static LogUtility log = new LogUtility(GalleryFragment.class);

    public static final int THUMB_SIZE = 200;

    private final List<Integer> _res;


    public GalleryFragment() {
        _res = new ArrayList<>();

        for (Field f : R.mipmap.class.getFields()) {
            String name = f.getName();
            if (f.getType() == int.class && name.startsWith("pre_")) {
                try {
                    int val = (int) R.mipmap.class.getField(name).get(null);
                    _res.add(val);
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    log.wtf("reflection error ", e);
                }
            }
        }

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.content_gallery, container, false);

        LinearLayout layout = root.findViewById(R.id.gallery_layout);

        final MainActivity ctx = (MainActivity) getContext();
        final Resources resources = getContext().getResources();

        for (int id : _res) {
            ImageView img = new ImageView(getContext());
            layout.addView(img);

            img.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setPadding(10, 10, 10, 10);

            img.setMinimumHeight(THUMB_SIZE);
            img.setMinimumWidth(THUMB_SIZE);

            BitmapDrawable drawable = new BitmapDrawable(getResources(), BitmapFactory.decodeResource(resources, id));
            drawable.getPaint().setFilterBitmap(false);
            img.setImageDrawable(drawable);

            img.setOnClickListener(v -> {
                Uri uri = new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                        .authority(resources.getResourcePackageName(id))
                        .appendPath(resources.getResourceTypeName(id))
                        .appendPath(resources.getResourceEntryName(id))
                        .build();
                try {
                    ctx.openImage(uri);
                } catch (IOException e) {
                    log.wtf("cannot open pre-loaded image", e);
                }
            });
        }

        return root;
    }

}
