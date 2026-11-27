package org.microg.gms.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.transition.TransitionManager;

import com.google.android.gms.BuildConfig;
import com.google.android.gms.R;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import org.microg.gms.common.Constants;
import org.microg.gms.ui.settings.SettingsProvider;
import org.microg.gms.ui.updater.AppUpdater;
import org.microg.gms.ui.updater.UpdateCheckScheduler;
import org.microg.tools.ui.WindowInsetsHelper;

import static org.microg.gms.ui.UtilsKt.buildAlertDialog;
import static org.microg.gms.ui.settings.SettingsProviderKt.getAllSettingsProviders;

public class MainSettingsActivity extends AppCompatActivity {
    private AppBarConfiguration appBarConfiguration;

    private static final String FIRST_RUN_MASTER = "org.microg.gms_firstRun";
    private static final String FIRST_RUN_PREF = "as_run";

    private NavController getNavController() {
        return ((NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.navhost)).getNavController();
    }

    private void showDialogIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(FIRST_RUN_MASTER, MODE_PRIVATE);
        if (BuildConfig.APPLICATION_ID == Constants.USER_MICROG_PACKAGE_NAME &&
                prefs.getBoolean(FIRST_RUN_PREF, true)) {
            buildAlertDialog(this)
                    .setMessage(R.string.limited_services_dialog_information)
                    .setTitle(R.string.limited_services_app_name)
                    .setPositiveButton(R.string.limited_services_dialog_information_ack, (dialog, id) -> {
                        prefs.edit().putBoolean(FIRST_RUN_PREF, false).apply();
                    })
                    .create()
                    .show();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        WindowInsetsHelper.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        for (SettingsProvider settingsProvider : getAllSettingsProviders(this)) {
            settingsProvider.preProcessSettingsIntent(intent);
        }

        setContentView(R.layout.settings_root_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        CollapsingToolbarLayout toolbarLayout = findViewById(R.id.collapsing_toolbar);
        setSupportActionBar(toolbar);

        // Edge-to-edge: the app bar takes the status bar inset, the scrolling content and
        // the FAB take the navigation bar / IME inset.
        WindowInsetsHelper.applySettingsInsets(findViewById(R.id.root_layout),
                findViewById(R.id.nested_scroll_view), findViewById(R.id.preference_fab));

        for (SettingsProvider settingsProvider : getAllSettingsProviders(this)) {
            settingsProvider.extendNavigation(getNavController());
        }

        appBarConfiguration = new AppBarConfiguration.Builder(getNavController().getGraph()).build();
        NavigationUI.setupWithNavController(toolbarLayout, toolbar, getNavController(), appBarConfiguration);
        // On the way back to the main page NavigationUI fades the up arrow out through a delayed
        // transition on the toolbar, which runs for almost a second and holds the toolbar's layout
        // meanwhile: a page opened within it got its arrow drifting below the title and its menu
        // late. The arrow goes at once instead, as the title does. Added after NavigationUI's
        // listener, this one runs after it, before the transition starts.
        // The new title is only redrawn, not laid out: one that takes two lines was drawn at the
        // old height for a frame or two before the app bar grew for it. Lay it out right away.
        getNavController().addOnDestinationChangedListener((controller, destination, arguments) -> {
            TransitionManager.endTransitions(toolbar);
            toolbarLayout.requestLayout();
        });

        // Each page opens at the top with its large title expanded and gets its scroll position
        // back when returned to, without the page being left jumping during the transition.
        getNavController().addOnDestinationChangedListener(new PageScrollKeeper(
                (NestedScrollView) findViewById(R.id.nested_scroll_view),
                (AppBarLayout) findViewById(R.id.app_bar),
                getSupportFragmentManager().findFragmentById(R.id.navhost)));

        showDialogIfNeeded();
        LauncherEntries.sync(this);

        UpdateCheckScheduler.schedule(this);
        AppUpdater.checkOnLaunch(this);
        // The daily update notification needs POST_NOTIFICATIONS on Android 13+; a
        // background receiver can never prompt, so ask while the settings screen is open.
        // Only on a fresh start: not again when the activity is recreated (language change,
        // rotation), where "Not now" was just answered.
        if (savedInstanceState == null) {
            AppUpdater.ensureUpdateNotificationPermission(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Tapping the update notification can deliver the check extra to an already-running activity.
        setIntent(intent);
        AppUpdater.checkOnLaunch(this);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(getNavController(), appBarConfiguration) || super.onSupportNavigateUp();
    }
}
