/*
 * Copyright (C) 2013-2017 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.tools.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.listitem.ListItemLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * @noinspection unused
 */
public abstract class AbstractAboutFragment extends Fragment {

    protected abstract void collectLibraries(List<Library> libraries);

//    public static Drawable getIcon(Context context) {
//        try {
//            PackageManager pm = context.getPackageManager();
//            return Objects.requireNonNull(pm.getPackageInfo(context.getPackageName(), 0).applicationInfo).loadIcon(pm);
//        } catch (PackageManager.NameNotFoundException e) {
//            // Never happens, self package always exists!
//            throw new RuntimeException(e);
//        }
//    }
//
//    public static String getAppName(Context context) {
//        try {
//            PackageManager pm = context.getPackageManager();
//            CharSequence label = Objects.requireNonNull(pm.getPackageInfo(context.getPackageName(), 0).applicationInfo).loadLabel(pm);
//            if (TextUtils.isEmpty(label)) return context.getPackageName();
//            return label.toString().trim();
//        } catch (PackageManager.NameNotFoundException e) {
//            // Never happens, self package always exists!
//            throw new RuntimeException(e);
//        }
//    }
//
//    protected String getAppName() {
//        return getAppName(requireContext());
//    }

    public static String getLibVersion(String packageName) {
        try {
            String versionName = (String) Class.forName(packageName + ".BuildConfig").getField("VERSION_NAME").get(null);
            if (TextUtils.isEmpty(versionName)) return "";
            return versionName.trim();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.colorBackground));
    }

    public static String getAppVersion() {
        return BuildConfig.APP_VERSION_NAME;
    }

    public static String getGmsVersion() {
        return BuildConfig.GMS_VERSION_NAME;
    }

    public static String getAppVersion(Context context) {
        return getAppVersion();
    }

    public static String getGmsVersion(Context context) {
        return getGmsVersion();
    }

    protected String getSummary() {
        return null;
    }

    /** Morphe's official links, the same targets Morphe Manager uses. */
    private static final String URL_WEBSITE = "https://morphe.software";

    /**
     * Fills the version card's status row (up to date / update available). The app module
     * overrides this with its updater; by default the row stays hidden.
     */
    protected void loadUpdateStatus(@NonNull TextView status, @NonNull View divider) {
    }

    /**
     * Colors the tile behind the MicroG RE logo as Morphe Manager sets off its own: the theme's
     * text color, faint. The logo's cut-outs show the tile, so they read light in a light theme
     * and dark in a dark one. Used wherever the logo stands on a tile, the About page and the
     * update sheet.
     */
    public static void applyLogoTile(@NonNull View tile) {
        int ink = MaterialColors.getColor(tile, com.google.android.material.R.attr.colorOnSurface);
        tile.setBackgroundColor(MaterialColors.compositeARGBWithAlpha(ink, MaterialColors.isColorLight(ink) ? 26 : 20));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View aboutRoot = inflater.inflate(R.layout.about_root, container, false);

        // Header: opens the system app info page.
        aboutRoot.findViewById(R.id.app_info).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception ignored) {
            }
        });

        applyLogoTile(aboutRoot.findViewById(R.id.app_icon));

        // Version card.
        String version = getAppVersion();
        ((TextView) aboutRoot.findViewById(R.id.app_version)).setText(version);
        if (version != null && version.toLowerCase(Locale.ROOT).contains("dev")) {
            aboutRoot.findViewById(R.id.about_prerelease_badge).setVisibility(View.VISIBLE);
        }
        loadUpdateStatus(aboutRoot.findViewById(R.id.about_status), aboutRoot.findViewById(R.id.about_status_divider));

        // Official links.
        bindLink(aboutRoot, R.id.morphe_donate, URL_WEBSITE + "/donate");
        bindLink(aboutRoot, R.id.morphe_website, URL_WEBSITE);
        bindLink(aboutRoot, R.id.morphe_github, "https://github.com/MorpheApp");
        bindLink(aboutRoot, R.id.morphe_reddit, "https://reddit.com/r/MorpheApp");
        bindLink(aboutRoot, R.id.morphe_translations, URL_WEBSITE + "/translate");
        aboutRoot.findViewById(R.id.morphe_contributors).setOnClickListener(v -> openContributors());
        aboutRoot.findViewById(R.id.morphe_supporters).setOnClickListener(v -> openSupporters());

        // Close behaves like system back, so it works wherever the page is hosted.
        aboutRoot.findViewById(R.id.about_close).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        // Full screen page: keep content clear of the system bars while scrolling under them.
        View content = aboutRoot.findViewById(R.id.main_container);
        final int padLeft = content.getPaddingLeft();
        final int padTop = content.getPaddingTop();
        final int padRight = content.getPaddingRight();
        final int padBottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(padLeft + bars.left, padTop + bars.top, padRight + bars.right, padBottom + bars.bottom);
            return insets;
        });

        List<Library> libraries = new ArrayList<>();
        collectLibraries(libraries);
        Collections.sort(libraries);

        ViewGroup libraryContainer = aboutRoot.findViewById(R.id.library_container);
        if (libraryContainer != null) {
            for (int i = 0; i < libraries.size(); i++) {
                Library library = libraries.get(i);
                View libraryView = inflater.inflate(R.layout.library_item, libraryContainer, false);

                TextView title = libraryView.findViewById(android.R.id.text1);
                TextView subtitle = libraryView.findViewById(android.R.id.text2);

                title.setText(getString(R.string.about_name_version_str, library.name, getLibVersion(library.packageName)));
                subtitle.setText(library.copyright != null ? library.copyright : getString(R.string.about_default_license));

                ListItemLayout listItemLayout = libraryView.findViewById(R.id.list_item_library);
                if (listItemLayout != null) {
                    listItemLayout.updateAppearance(i, libraries.size());
                }

                libraryContainer.addView(libraryView);
            }
        }

        return aboutRoot;
    }

    /** Shows who contributed. The app module lists them in the app; by default, the web page. */
    protected void openContributors() {
        openUrl("https://github.com/MorpheApp/MicroG-RE/graphs/contributors");
    }

    /** Shows who supports Morphe. The app module lists them in the app; by default, the web page. */
    protected void openSupporters() {
        openUrl("https://opencollective.com/morpheapp");
    }

    private void bindLink(View root, int id, String url) {
        root.findViewById(id).setOnClickListener(v -> openUrl(url));
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private class LibraryAdapter extends ArrayAdapter<Library> {
        public LibraryAdapter(Context context, Library[] libraries) {
            super(context, android.R.layout.simple_list_item_2, android.R.id.text1, libraries);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View v = super.getView(position, convertView, parent);
            ((TextView) v.findViewById(android.R.id.text1)).setText(getString(R.string.about_name_version_str, Objects.requireNonNull(getItem(position)).name, getLibVersion(Objects.requireNonNull(getItem(position)).packageName)));
            ((TextView) v.findViewById(android.R.id.text2)).setText(Objects.requireNonNull(getItem(position)).copyright != null ? Objects.requireNonNull(getItem(position)).copyright : getString(R.string.about_default_license));
            return v;
        }
    }

    protected static class Library implements Comparable<Library> {
        public final String packageName;
        public final String name;
        public final String copyright;

        public Library(String packageName, String name, String copyright) {
            this.packageName = packageName;
            this.name = name;
            this.copyright = copyright;
        }

        @NonNull
        @Override
        public String toString() {
            return name + ", " + copyright;
        }

        @Override
        public int compareTo(Library another) {
            return name.toLowerCase(Locale.US).compareTo(another.name.toLowerCase(Locale.US));
        }
    }
}