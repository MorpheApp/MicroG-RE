/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.tools.ui;

import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

/**
 * Shared edge-to-edge handling for the settings screens.
 * <p>
 * The top inset is consumed by the AppBarLayout (android:fitsSystemWindows), so only the
 * scrolling content and the FAB need adjusting here: side insets (landscape / cutouts) and
 * the bottom inset, which is the IME height while the keyboard is open and the navigation
 * bar height otherwise. The scroll view keeps android:clipToPadding="false" so content can
 * scroll behind the transparent navigation bar without being cut off.
 */
public final class WindowInsetsHelper {

    private WindowInsetsHelper() {
    }

    /**
     * Draws behind transparent system bars. Gated to API 29+, where the navigation bar
     * contrast scrim can be turned off; older releases keep the opaque system bars.
     */
    public static void enableEdgeToEdge(@NonNull ComponentActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            EdgeToEdge.enable(activity, SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT));
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    /**
     * Applies system bar, display cutout and IME insets to the settings scroll view and FAB.
     *
     * @param root       the CoordinatorLayout that receives the window insets
     * @param scrollView the scrolling content below the app bar
     * @param fab        optional extended FAB anchored to the bottom end corner
     */
    public static void applySettingsInsets(@NonNull View root, @NonNull NestedScrollView scrollView, @Nullable ExtendedFloatingActionButton fab) {
        final int scrollStart = scrollView.getPaddingLeft();
        final int scrollTop = scrollView.getPaddingTop();
        final int scrollEnd = scrollView.getPaddingRight();
        final int scrollBottom = scrollView.getPaddingBottom();

        final int fabLeft;
        final int fabRight;
        final int fabBottom;
        if (fab != null) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
            fabLeft = params.leftMargin;
            fabRight = params.rightMargin;
            fabBottom = params.bottomMargin;
        } else {
            fabLeft = fabRight = fabBottom = 0;
        }

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(bars.bottom, ime.bottom);

            scrollView.setPadding(scrollStart + bars.left, scrollTop, scrollEnd + bars.right, scrollBottom + bottomInset);

            if (fab != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
                params.leftMargin = fabLeft + bars.left;
                params.rightMargin = fabRight + bars.right;
                params.bottomMargin = fabBottom + bottomInset;
                fab.setLayoutParams(params);
            }

            return windowInsets;
        });
    }

    /**
     * Shrinks the extended FAB while scrolling down and extends it again on the way up.
     */
    public static void shrinkFabOnScroll(@NonNull NestedScrollView scrollView, @NonNull ExtendedFloatingActionButton fab) {
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY > oldScrollY) {
                fab.shrink();
            } else if (scrollY < oldScrollY) {
                fab.extend();
            }
        });
    }
}
