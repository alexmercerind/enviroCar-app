/**
 * Copyright (C) 2013 - 2021 the enviroCar community
 *
 * This file is part of the enviroCar app.
 *
 * The enviroCar app is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The enviroCar app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with the enviroCar app. If not, see http://www.gnu.org/licenses/.
 */
package org.envirocar.app.views.dashboard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.transition.AutoTransition;
import android.transition.ChangeBounds;
import android.transition.Slide;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.Task;
import com.jakewharton.rxbinding3.appcompat.RxToolbar;
import com.squareup.otto.Subscribe;

import org.envirocar.app.BaseApplicationComponent;
import org.envirocar.app.R;
import org.envirocar.app.databinding.FragmentDashboardViewNewBinding;
import org.envirocar.app.handler.agreement.AgreementManager;
import org.envirocar.app.handler.ApplicationSettings;
import org.envirocar.app.handler.BluetoothHandler;
import org.envirocar.app.handler.preferences.UserPreferenceHandler;
import org.envirocar.app.handler.userstatistics.UserStatisticsUpdateEvent;
import org.envirocar.app.injection.BaseInjectorFragment;
import org.envirocar.app.recording.RecordingService;
import org.envirocar.app.recording.RecordingState;
import org.envirocar.app.recording.RecordingType;
import org.envirocar.app.recording.events.EngineNotRunningEvent;
import org.envirocar.app.recording.events.RecordingStateEvent;
import org.envirocar.app.views.carselection.CarSelectionActivity;
import org.envirocar.app.views.login.SigninActivity;
import org.envirocar.app.views.obdselection.OBDSelectionActivity;
import org.envirocar.app.views.recordingscreen.RecordingScreenActivity;
import org.envirocar.app.views.utils.DialogUtils;
import org.envirocar.app.views.utils.SizeSyncTextView;
import org.envirocar.app.views.others.TermsOfUseActivity;
import org.envirocar.core.ContextInternetAccessProvider;
import org.envirocar.core.entity.User;
import org.envirocar.core.events.NewCarTypeSelectedEvent;
import org.envirocar.core.events.NewUserSettingsEvent;
import org.envirocar.core.events.bluetooth.BluetoothDeviceSelectedEvent;
import org.envirocar.core.events.bluetooth.BluetoothStateChangedEvent;
import org.envirocar.core.events.gps.GpsStateChangedEvent;
import org.envirocar.core.logging.Logger;
import org.envirocar.core.utils.PermissionUtils;
import org.envirocar.core.utils.ServiceUtils;
import org.envirocar.obd.events.TrackRecordingServiceStateChangedEvent;
import org.envirocar.obd.service.BluetoothServiceState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import info.hoang8f.android.segmented.SegmentedGroup;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.schedulers.Schedulers;

import static android.app.Activity.RESULT_OK;

/**
 * @author dewall
 */
public class DashboardFragment extends BaseInjectorFragment {
    private static final Logger LOG = Logger.getLogger(DashboardFragment.class);

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1203;

    private FragmentDashboardViewNewBinding binding;

    // View Injections
    protected Toolbar toolbar;
    protected TextView textView;
    protected View loggedInLayout;
    protected View userTracksLayout;
    protected TextView userTracksTextView;
    protected View userDistanceLayout;
    protected TextView userDistanceTextView;
    protected View userDurationLayout;
    protected TextView userDurationTextView;
    protected ProgressBar userStatProgressBar;

    protected ViewGroup indicatorView;
    protected View bluetoothIndicatorLayout;
    protected ImageView bluetoothIndicator;
    protected View obdIndicatorLayout;
    protected ImageView obdIndicator;
    protected ImageView gpsIndicator;
    protected ImageView carIndicator;

    protected SizeSyncTextView bluetoothIndicatorText;
    protected SizeSyncTextView obdIndicatorText;
    protected SizeSyncTextView gpsIndicatorText;
    protected SizeSyncTextView carIndicatorText;

    protected SegmentedGroup modeSegmentedGroup;
    protected RadioButton obdModeRadioButton;
    protected RadioButton gpsModeRadioButton;

    protected ViewGroup bluetoothSelectionView;
    protected TextView bluetoothSelectionTextPrimary;
    protected TextView bluetoothSelectionTextSecondary;
    protected TextView carSelectionTextPrimary;
    protected TextView carSelectionTextSecondary;

    protected FrameLayout bannerLayout;
    protected ConstraintLayout mainLayout;

    protected View startTrackButton;
    protected TextView startTrackButtonText;

    // injected variables
    @Inject
    protected UserPreferenceHandler userHandler;
    @Inject
    protected BluetoothHandler bluetoothHandler;

    @Inject
    protected AgreementManager mAgreementManager;

    private CompositeDisposable disposables;
    private boolean statisticsKnown = false;

    // some private variables
    private AlertDialog connectingDialog;
    private CountDownTimer deviceDiscoveryTimer;
    private List<SizeSyncTextView> indicatorSyncGroup;
    private AppUpdateManager appUpdateManager;
    private Task<AppUpdateInfo> appUpdateInfoTask;
    private Boolean welcomeMessageShown = false;

    @Override
    protected void injectDependencies(BaseApplicationComponent baseApplicationComponent) {
        baseApplicationComponent.inject(this);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // for the login/register button
        setHasOptionsMenu(true);

        this.disposables = new CompositeDisposable();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardViewNewBinding.inflate(inflater, container, false);
        final View contentView = binding.getRoot();
        toolbar = binding.fragmentDashboardToolbar;
        textView = binding.fragmentDashboardUsername;
        loggedInLayout = binding.fragmentDashboardLoggedInLayout;
        userTracksLayout = binding.fragmentDashboardUserTracksLayout;
        userTracksTextView = binding.fragmentDashboardUserTracksTextview;
        userDistanceLayout = binding.fragmentDashboardUserDistanceLayout;
        userDistanceTextView = binding.fragmentDashboardUserDistanceTextview;
        userDurationLayout = binding.fragmentDashboardUserDurationLayout;
        userDurationTextView = binding.fragmentDashboardUserDurationTextview;
        userStatProgressBar = binding.fragmentDashboardUserStatisticsProgress;
        indicatorView = binding.fragmentDashboardIndicatorView;
        bluetoothIndicatorLayout = binding.fragmentDashboardIndicatorBluetoothLayout;
        bluetoothIndicator = binding.fragmentDashboardIndicatorBluetooth;
        obdIndicatorLayout = binding.fragmentDashboardIndicatorObdLayout;
        obdIndicator = binding.fragmentDashboardIndicatorObd;
        gpsIndicator = binding.fragmentDashboardIndicatorGps;
        carIndicator = binding.fragmentDashboardIndicatorCar;
        bluetoothIndicatorText = binding.fragmentDashboardIndicatorBluetoothText;
        obdIndicatorText = binding.fragmentDashboardIndicatorObdText;
        gpsIndicatorText = binding.fragmentDashboardIndicatorGpsText;
        carIndicatorText = binding.fragmentDashboardIndicatorCarText;
        modeSegmentedGroup = binding.fragmentDashboardModeSelector;
        obdModeRadioButton = binding.fragmentDashboardObdModeButton;
        gpsModeRadioButton = binding.fragmentDashboardGpsModeButton;
        bluetoothSelectionView = binding.fragmentDashboardObdselectionLayout;
        bluetoothSelectionTextPrimary = binding.fragmentDashboardObdselectionTextPrimary;
        bluetoothSelectionTextSecondary = binding.fragmentDashboardObdselectionTextSecondary;
        carSelectionTextPrimary = binding.fragmentDashboardCarselectionTextPrimary;
        carSelectionTextSecondary = binding.fragmentDashboardCarselectionTextSecondary;
        bannerLayout = binding.fragmentDashboardBanner;
        mainLayout = binding.fragmentDashboardMainLayout;
        startTrackButton = binding.fragmentDashboardStartTrackButton;
        startTrackButtonText = binding.fragmentDashboardStartTrackButtonText;

        obdModeRadioButton.setOnCheckedChangeListener(this::onModeChangedClicked);
        gpsModeRadioButton.setOnCheckedChangeListener(this::onModeChangedClicked);
        binding.fragmentDashboardCarselectionLayout.setOnClickListener(v -> {
            onCarSelectionClicked();
        });
        binding.fragmentDashboardObdselectionLayout.setOnClickListener(v -> {
            onBluetoothSelectionClicked();
        });
        binding.fragmentDashboardStartTrackButton.setOnClickListener(v -> {
            onStartTrackButtonClicked();
        });
        binding.fragmentDashboardIndicatorCar.setOnClickListener(v -> {
            onCarIndicatorClicked();
        });
        binding.fragmentDashboardIndicatorObd.setOnClickListener(v -> {
            onObdIndicatorClicked();
        });
        binding.fragmentDashboardIndicatorBluetooth.setOnClickListener(v -> {
            onBluetoothIndicatorClicked();
        });
        binding.fragmentDashboardIndicatorGps.setOnClickListener(v -> {
            onGPSIndicatorClicked();
        });
        binding.userStatisticsCardView.setOnClickListener(v -> {
            onUserStatsClicked();
        });

        // inflate menus and init toolbar clicks
        toolbar.inflateMenu(R.menu.menu_dashboard_logged_out);
        toolbar.getOverflowIcon().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        RxToolbar.itemClicks(this.toolbar).subscribe(this::onToolbarItemClicked);

        appUpdateManager = AppUpdateManagerFactory.create(getContext());
        appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

        //
        this.updateUserLogin(userHandler.getUser());

        // init the text size synchronization
        initTextSynchronization();

        // set recording state
        ApplicationSettings.getSelectedRecordingTypeObservable(getContext())
                .doOnNext(this::setRecordingMode)
                .doOnError(LOG::error)
                .blockingFirst();

        return contentView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        this.updateStatisticsVisibility(this.statisticsKnown);
        appUpdateManager
                .getAppUpdateInfo()
                .addOnSuccessListener(
                        appUpdateInfo -> {
                            if (appUpdateInfo.updateAvailability()
                                    == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                                try {
                                    appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, getActivity(), 121);
                                } catch (IntentSender.SendIntentException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE: {
                LOG.info("Permission result: " + Arrays.toString(permissions) + "; " + Arrays.toString(grantResults));
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LOG.info("Location permission has been granted");
                    Snackbar.make(getView(), "Location Permission granted.",
                            BaseTransientBottomBar.LENGTH_SHORT).show();
                    onStartTrackButtonClicked();
                } else {
                    LOG.info("Location permission has been denied");
                    Snackbar.make(getView(), "Location Permission denied.",
                            BaseTransientBottomBar.LENGTH_LONG).show();
                }
            }
        }
    }

    private void onToolbarItemClicked(MenuItem menuItem) {
        LOG.info(String.format("Toolbar - Clicked on %s", menuItem.getTitle()));
        if (menuItem.getItemId() == R.id.dashboard_action_login) {
            // starting the login activity
            SigninActivity.startActivity(getContext());
        } else if (menuItem.getItemId() == R.id.dashboard_action_logout) {
            // show a logout dialog
            new MaterialAlertDialogBuilder(getActivity(), R.style.MaterialDialog)
                    .setTitle(R.string.menu_logout_envirocar_title)
                    .setMessage(R.string.menu_logout_envirocar_content)
                    .setIcon(R.drawable.ic_logout_white_24dp)
                    .setPositiveButton(R.string.menu_logout_envirocar_positive,
                            (dialog, which) -> userHandler.logOut().subscribe(onLogoutSubscriber()))
                    .setNegativeButton(R.string.menu_logout_envirocar_negative, null)
                    .show();
        } else if (menuItem.getItemId() == R.id.dashboard_action_delete_account) {
            // open the browser for account deletion
            Intent deleteViaBrowserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://envirocar.org/app/#!/login?afterLogin=/profile"));

            try {
                startActivity(deleteViaBrowserIntent);
            } catch (android.content.ActivityNotFoundException e) {
                LOG.warn(e.getMessage(), e);
                Snackbar.make(getView(), getString(R.string.others_could_not_open), Snackbar.LENGTH_LONG).show();
            }
        }
    }



    private DisposableCompletableObserver onLogoutSubscriber() {
        return new DisposableCompletableObserver() {
            private MaterialDialog dialog = null;
            private User userTemp = null;

            @Override
            public void onStart() {
                this.userTemp = userHandler.getUser();
                // show progress dialog for the deletion
                this.dialog = new MaterialDialog.Builder(getContext())
                        .title(R.string.activity_login_logout_progress_dialog_title)
                        .content(R.string.activity_login_logout_progress_dialog_content)
                        .progress(true, 0)
                        .cancelable(false)
                        .show();
            }

            @Override
            public void onComplete() {
                // Show a snackbar that indicates the finished logout
                Snackbar.make(getActivity().findViewById(R.id.snackbar_placeholder),
                        String.format(getString(R.string.goodbye_message), userTemp.getUsername()),
                        Snackbar.LENGTH_LONG).show();
                dialog.dismiss();
            }

            @Override
            public void onError(Throwable e) {
                LOG.error(e.getMessage(), e);
                dialog.dismiss();
            }

        };
    }

    public void onModeChangedClicked(CompoundButton button, boolean checked) {
        if (!checked)
            return;
        RecordingType selectedRT = button.getId() == R.id.fragment_dashboard_obd_mode_button ?
                RecordingType.OBD_ADAPTER_BASED : RecordingType.ACTIVITY_RECOGNITION_BASED;

        // if the GPS tracking is not enabled then set recording type to OBD.
        if (!ApplicationSettings.isGPSBasedTrackingEnabled(getContext())) {
            selectedRT = RecordingType.OBD_ADAPTER_BASED;
        }

        LOG.info("Mode selected " + button.getText());

        // adjust the ui
        this.setRecordingMode(selectedRT);

        // update the selected recording type
        ApplicationSettings.setSelectedRecordingType(getContext(), selectedRT);
        // update button
        boolean setEnabled = false;
        if (button.getId() == R.id.fragment_dashboard_gps_mode_button) {
            setEnabled = (this.carIndicator.isActivated() && this.gpsIndicator.isActivated());
        } else if (button.getId() == R.id.fragment_dashboard_obd_mode_button) {
            setEnabled = (this.bluetoothIndicator.isActivated()
                    && this.gpsIndicator.isActivated()
                    && this.obdIndicator.isActivated()
                    && this.carIndicator.isActivated());
        }
        this.startTrackButtonText.setText(R.string.dashboard_start_track);
        this.startTrackButton.setEnabled(setEnabled);
    }

    private void setRecordingMode(RecordingType selectedRT) {
        if (!ApplicationSettings.isGPSBasedTrackingEnabled(getContext())) {
            modeSegmentedGroup.setVisibility(View.GONE);
        }

        // check whether OBD is visible or not.
        int visibility = selectedRT == RecordingType.OBD_ADAPTER_BASED ? View.VISIBLE : View.GONE;

        if (visibility == View.GONE) {
            gpsModeRadioButton.setChecked(true);
            obdModeRadioButton.setChecked(false);
        }

        // shared transition set
        TransitionSet transitionSet = new TransitionSet()
                .addTransition(new ChangeBounds())
                .addTransition(new AutoTransition())
                .addTransition(new Slide(Gravity.LEFT));

        // animate transition
        TransitionManager.beginDelayedTransition(this.modeSegmentedGroup);
        TransitionManager.beginDelayedTransition(this.bluetoothSelectionView, transitionSet);
        this.bluetoothSelectionView.setVisibility(visibility);

        // indicator transition
        TransitionManager.beginDelayedTransition(this.indicatorView, transitionSet);
        this.bluetoothIndicatorLayout.setVisibility(visibility);
        this.obdIndicatorLayout.setVisibility(visibility);
    }

    // OnClick Handler
    protected void onCarSelectionClicked() {
        LOG.info("Clicked on Carselection.");
        Intent intent = new Intent(getActivity(), CarSelectionActivity.class);
        getActivity().startActivity(intent);
    }

    protected void onBluetoothSelectionClicked() {
        LOG.info("Clicked on Bluetoothselection.");
        Intent intent = new Intent(getActivity(), OBDSelectionActivity.class);
        getActivity().startActivity(intent);
    }

    @SuppressLint("RestrictedApi")
    protected void onStartTrackButtonClicked() {
        LOG.info("Clicked on Start Track Button");

        User user = userHandler.getUser();

        if (RecordingService.RECORDING_STATE == RecordingState.RECORDING_RUNNING) {
            RecordingScreenActivity.navigate(getContext());
            return;
        } else if (!PermissionUtils.hasLocationPermission(getContext())) {
            String[] perms;
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                perms = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                };
            }
            else {
                perms = new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION
                };
            }
            ActivityCompat.requestPermissions(getActivity(), perms,
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else if (user == null && ApplicationSettings.isTrackchunkUploadEnabled(getContext())) {
                // cannot start, we need a user
                LOG.info("cannot start, login is required for chunk upload feature");
                Snackbar.make(getView(),
                    getString(R.string.dashboard_track_chunks_enabled_login),
                    Snackbar.LENGTH_LONG).show();
        } else if (user != null) {
            // if chunk is enabled, we need to check if the current ToU are accepted
            if (ApplicationSettings.isTrackchunkUploadEnabled(getContext())) {
                LOG.info("chunk upload is enabled, checking TermsOfUse");
                if (! (new ContextInternetAccessProvider(getContext()).isConnected())) {
                    Snackbar.make(getView(),
                        getString(R.string.error_not_connected_to_network),
                        Snackbar.LENGTH_LONG).show();
                    return;
                }
                mAgreementManager.verifyTermsOfUse(null, true)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(termsOfUse -> {
                        if (termsOfUse != null) {
                            startRecording();
                        } else {
                            LOG.warn("No TermsOfUse received from verification");
                        }
                    }, e -> {
                        LOG.warn("Error during TermsOfUse verification", e);
                        // inform the user about ToU acceptance
                        Snackbar sb = Snackbar.make(getView(), String.format(getString(R.string.dashboard_accept_tou), getString(R.string.title_others)), Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                LOG.info("ToU Snackbar closed");
                                Intent intent = new Intent(getActivity(), TermsOfUseActivity.class);
                                getActivity().startActivity(intent);
                            }
                        });
                        Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) sb.getView();
                        layout.setMinimumHeight(100);
                        sb.show();
                    });
            } else {
                // we can check the ToUs later before upload
                LOG.info("A user is logged in, chunk upload is disabled");
                startRecording();
            }
        } else {
            // no user, we can create a local track
            LOG.info("No user available, chunk upload is disabled");
            startRecording();
        }
    }

    private void startRecording() {
        LOG.info("All conditions met, starting track recording");
        if (modeSegmentedGroup.getCheckedRadioButtonId() == R.id.fragment_dashboard_obd_mode_button) {
            if (this.gpsIndicator.isActivated()
                && this.carIndicator.isActivated()
                && this.bluetoothIndicator.isActivated()
                && this.obdIndicator.isActivated()
                && requireActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                BluetoothDevice device = bluetoothHandler.getSelectedBluetoothDevice();

                Intent obdRecordingIntent = new Intent(getActivity(), RecordingService.class);

                this.connectingDialog = DialogUtils.createProgressBarDialogBuilder(getContext(),
                        R.string.dashboard_connecting,
                        R.drawable.ic_bluetooth_white_24dp,
                        String.format(getString(R.string.dashboard_connecting_find_template), device.getName()))
                        .setNegativeButton(R.string.cancel, (dialog, which) -> {
                            ServiceUtils.stopService(getActivity(), obdRecordingIntent);
                        })
                        .show();

                // If the device is not found to start the track, dismiss the Dialog in 30 sec
                deviceDiscoveryTimer = new CountDownTimer(60000, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                    }

                    @Override
                    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    public void onFinish() {
                        LOG.warn("Device discovery timeout. Stop recording.");
                        connectingDialog.dismiss();
                        ServiceUtils.stopService(getActivity(), obdRecordingIntent);
                        Snackbar.make(
                                getView(),
                                String.format(
                                        getString(R.string.dashboard_connecting_not_found_template),
                                        device.getName()
                                ),
                                Snackbar.LENGTH_LONG
                        ).show();
                    }
                }.start();
                ServiceUtils.startService(getActivity(), obdRecordingIntent);
            } else if (modeSegmentedGroup.getCheckedRadioButtonId() == R.id.fragment_dashboard_obd_mode_button) {
                Intent gpsOnlyIntent = new Intent(getActivity(), RecordingService.class);
                ServiceUtils.startService(getActivity(), gpsOnlyIntent);
            }
        }
    }



    protected void onCarIndicatorClicked() {
        LOG.info("Car Indicator clicked");
        Intent intent = new Intent(getActivity(), CarSelectionActivity.class);
        getActivity().startActivity(intent);
    }

    protected void onObdIndicatorClicked() {
        LOG.info("OBD indicator clicked");
        Intent intent = new Intent(getActivity(), OBDSelectionActivity.class);
        getActivity().startActivity(intent);
    }

    protected void onBluetoothIndicatorClicked() {
        LOG.info("Bluetooth indicator clicked");
        Intent intent = new Intent(getActivity(), OBDSelectionActivity.class);
        getActivity().startActivity(intent);
    }

    protected void onGPSIndicatorClicked() {
        LOG.info("GPS indicator clicked");
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        getActivity().startActivity(intent);
    }

    protected void onUserStatsClicked() {
        BottomNavigationView bottomView = getActivity().findViewById(R.id.navigation);
        bottomView.setSelectedItemId(R.id.navigation_my_tracks);
    }

//    @Subscribe
//    public void onReceiveRecordingStateChangedEvent(TrackRecordingServiceStateChangedEvent event) {
//        LOG.info("Recieved Recording State Changed event");
//        Observable.just(event.mState)
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .map(state -> {
//                    if (state == BluetoothServiceState.SERVICE_STARTED) {
//                        RecordingScreenActivity.navigate(getContext());
//                    }
//                    return state;
//                })
//                .subscribe(this::updateStartTrackButton, LOG::error);
//    }
//
//    @Subscribe
//    public void onRecordingStateEvent(RecordingStateEvent event) {
//        LOG.info("Retrieve Recording State Event: " + event.toString());
//        Observable.just(event.recordingState)
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(this::updateByRecordingState, LOG::error);
//    }
//
//    @Subscribe
//    public void onEngineNotRunningEvent(EngineNotRunningEvent event) {
//        LOG.info("Retrieved Engine not running event");
//        if (connectingDialog != null) {
//            connectingDialog.dismiss();
//            deviceDiscoveryTimer.cancel();
//            connectingDialog = null;
//        }
//
//        Observable.just(event)
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribeOn(AndroidSchedulers.mainThread())
//                .subscribe(
//                        engineNotRunningEvent -> new MaterialAlertDialogBuilder(getActivity(), R.style.MaterialDialog)
//                                .setTitle(R.string.dashboard_engine_not_running_dialog_title)
//                                .setMessage(R.string.dashboard_engine_not_running_dialog_content)
//                                .setIcon(R.drawable.ic_error_black_24dp)
//                                .setPositiveButton(R.string.ok, null)
//                                .setCancelable(true)
//                                .show(),
//                        throwable -> LOG.error("Error while showing EngineNotRunningEvent dialog:", throwable));
//    }
//
//    /**
//     * Receiver method for bluetooth activation events.
//     *
//     * @param event
//     */
//    @Subscribe
//    public void receiveBluetoothStateChanged(BluetoothStateChangedEvent event) {
//        // post on decor view to ensure that it gets executed when view has been inflated.
//        runAfterInflation(() -> {
//            this.bluetoothIndicator.setActivated(event.isBluetoothEnabled);
//            this.updateOBDState(event.selectedDevice);
//            this.updateStartTrackButton();
//        });
//    }
//
//    /**
//     * Receiver method for new Car selected events.
//     */
//    @Subscribe
//    public void onReceiveNewCarTypeSelectedEvent(final NewCarTypeSelectedEvent event) {
//        LOG.info("Received NewCarTypeSelected event. Updating views.");
//        // post on decor view to ensure that it gets executed when view has been inflated.
//        runAfterInflation(() -> {
//            if (event.mCar != null) {
//                this.carSelectionTextPrimary.setText(String.format("%s %s",
//                        event.mCar.getManufacturer(), event.mCar.getModel()));
//                String secText = String.format("%s, %s cm³, %s",
//                        "" + event.mCar.getConstructionYear(),
//                        "" + event.mCar.getEngineDisplacement(),
//                        "" + getString(event.mCar.getFuelType().getStringResource()));
//                if (event.mCar.getVehicleType() != null) {
//                    secText += ", " + getString(event.mCar.getVehicleType().getStringResource());
//                }
//                this.carSelectionTextSecondary.setText(secText);
//
//                // set indicator color accordingly
//                this.carIndicator.setActivated(true);
//            } else {
//
//                this.carSelectionTextPrimary.setText(String.format("%s", getResources().getString(R.string.dashboard_carselection_no_car_selected)));
//                this.carSelectionTextSecondary.setText(String.format("%s", getResources().getString(R.string.dashboard_carselection_no_car_selected_advise)));
//
//                // set warning indicator color to red
//                this.carIndicator.setActivated(false);
//            }
//
//            this.updateStartTrackButton();
//        });
//    }
//
//    /**
//     * Receiver method for bluetooth device selected events.
//     *
//     * @param event
//     */
//    @Subscribe
//    public void onOBDAdapterSelectedEvent(BluetoothDeviceSelectedEvent event) {
//        // post on decor view to ensure that it gets executed when view has been inflated.
//        runAfterInflation(() -> {
//            updateOBDState(event.mDevice);
//        });
//    }
//
//    /**
//     * Receiver method for GPS activation events.
//     *
//     * @param event
//     */
//    @Subscribe
//    public void onGpsStateChangedEvent(final GpsStateChangedEvent event) {
//        // post on decor view to ensure that it gets executed when view has been inflated.
//        runAfterInflation(() -> {
//            this.gpsIndicator.setActivated(event.mIsGPSEnabled);
//            this.updateStartTrackButton();
//        });
//    }
//
//    @Subscribe
//    public void onNewUserSettingsEvent(final NewUserSettingsEvent event) {
//        runAfterInflation(() -> {
//            this.statisticsKnown = false;
//            this.updateUserLogin(event.mUser);
//        });
//    }
//
//    @Subscribe
//    public void onUserStatisticsUpdateEvent(final UserStatisticsUpdateEvent event) {
//        runAfterInflation(() -> {
//            this.statisticsKnown = true;
//            updateStatisticsVisibility(true);
//            userTracksTextView.setText(String.format("%s", event.numTracks));
//            userDistanceTextView.setText(String.format("%s km", Math.round(event.totalDistance)));
//            userDurationTextView.setText(formatTimeForDashboard(event.totalDuration));
//        });
//    }

    private void updateUserLogin(User user) {
        if (user != null) {
            // show progress bar
            updateStatisticsVisibility(this.statisticsKnown);

            this.loggedInLayout.setVisibility(View.VISIBLE);
            this.toolbar.getMenu().clear();
            this.toolbar.inflateMenu(R.menu.menu_dashboard_logged_in);
            this.textView.setText(user.getUsername());

            // Welcome message as user logged in successfully
            if (!welcomeMessageShown) {
                Snackbar.make(getActivity().findViewById(R.id.snackbar_placeholder),
                        String.format(getString(R.string.welcome_message), user.getUsername()),
                        Snackbar.LENGTH_LONG).show();
                welcomeMessageShown = true;
            }

            ConstraintSet set = new ConstraintSet();
            set.constrainPercentHeight(bannerLayout.getId(), 0.25f);
            set.connect(bannerLayout.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0);
            set.connect(bannerLayout.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0);
            set.connect(bannerLayout.getId(), ConstraintSet.TOP, toolbar.getId(), ConstraintSet.BOTTOM, 0);
            set.applyTo(this.mainLayout);
        } else {
            // show progress bar
            updateStatisticsVisibility(this.statisticsKnown);

            this.loggedInLayout.setVisibility(View.GONE);
            this.toolbar.getMenu().clear();
            this.toolbar.inflateMenu(R.menu.menu_dashboard_logged_out);

            ConstraintSet set = new ConstraintSet();
            set.constrainPercentHeight(bannerLayout.getId(), 0.115f);
            set.connect(bannerLayout.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0);
            set.connect(bannerLayout.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0);
            set.connect(bannerLayout.getId(), ConstraintSet.TOP, toolbar.getId(), ConstraintSet.BOTTOM, 0);
            set.applyTo(this.mainLayout);
        }
    }

    private void updateStatisticsVisibility(boolean statisticsKnown) {
        // update progress bar visibility
        int progressBarVisibility = statisticsKnown ? View.GONE : View.VISIBLE;
        userStatProgressBar.setVisibility(progressBarVisibility);

        // update statistics visibility
        int statisticsVisibility = statisticsKnown ? View.VISIBLE : View.INVISIBLE;
        userTracksLayout.setVisibility(statisticsVisibility);
        userDistanceLayout.setVisibility(statisticsVisibility);
        userDurationLayout.setVisibility(statisticsVisibility);
    }

    private String formatTimeForDashboard(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        String formatString = hours > 99 ? "%03d:%02d h" : "%02d:%02d h";
        return String.format(formatString, hours, TimeUnit.MILLISECONDS.toMinutes(millis)
                - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)));
    }

//    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//    private void updateOBDState(BluetoothDevice device) {
//        if (device != null) {
//            bluetoothSelectionTextPrimary.setText(device.getName());
//            bluetoothSelectionTextSecondary.setText(device.getAddress());
//
//            // set indicator color
//            this.obdIndicator.setActivated(true);
//        } else {
//
//            bluetoothSelectionTextPrimary.setText(getResources().getText(R.string.dashboard_obd_not_selected));
//            bluetoothSelectionTextSecondary.setText(getResources().getText(R.string.dashboard_obd_not_selected_advise));
//
//            this.obdIndicator.setActivated(false);
//        }
//        this.updateStartTrackButton();
//    }

    private void updateStartTrackButton() {
        boolean setEnabled = false;
        switch (RecordingService.RECORDING_STATE) {
            case RECORDING_RUNNING:
                this.startTrackButtonText.setText(R.string.dashboard_goto_track);
                this.startTrackButton.setEnabled(true);
                break;
            case RECORDING_INIT:
                this.startTrackButtonText.setText(R.string.dashboard_track_is_starting);
                this.startTrackButton.setEnabled(true);
                break;
            case RECORDING_STOPPED:
                if (this.modeSegmentedGroup.getCheckedRadioButtonId() == R.id.fragment_dashboard_gps_mode_button) {
                    setEnabled = (this.carIndicator.isActivated()
                            && this.gpsIndicator.isActivated());
                } else if (this.modeSegmentedGroup.getCheckedRadioButtonId() == R.id.fragment_dashboard_obd_mode_button) {
                    setEnabled = (this.bluetoothIndicator.isActivated()
                            && this.gpsIndicator.isActivated()
                            && this.obdIndicator.isActivated()
                            && this.carIndicator.isActivated());
                }
                this.startTrackButtonText.setText(R.string.dashboard_start_track);
                this.startTrackButton.setEnabled(setEnabled);
                break;
        }
    }

//    private void updateByRecordingState(RecordingState state) {
//        switch (state) {
//            case RECORDING_INIT:
//                break;
//            case RECORDING_RUNNING:
//                if (modeSegmentedGroup.getCheckedRadioButtonId() == R.id.fragment_dashboard_gps_mode_button) {
//                    RecordingScreenActivity.navigate(getContext());
//                } else if (modeSegmentedGroup.getCheckedRadioButtonId() == R.id.fragment_dashboard_obd_mode_button) {
//                    if (this.connectingDialog != null) {
//                        this.connectingDialog.dismiss();
//                        deviceDiscoveryTimer.cancel();
//                        this.connectingDialog = null;
//                        RecordingScreenActivity.navigate(getContext());
//                    }
//                }
//                break;
//            case RECORDING_STOPPED:
//                if (this.connectingDialog != null) {
//                    this.connectingDialog.dismiss();
//                    deviceDiscoveryTimer.cancel();
//                    this.connectingDialog = null;
//                }
//                break;
//        }
//        updateStartTrackButton();
//    }
//
//    private void updateStartTrackButton(BluetoothServiceState state) {
//        switch (state) {
//            case SERVICE_STOPPED:
//                this.startTrackButton.setEnabled(true);
//                break;
//            case SERVICE_STARTED:
//                break;
//            case SERVICE_STARTING:
//                break;
//            case SERVICE_STOPPING:
//                break;
//            default:
//                break;
//        }
//    }

    private void initTextSynchronization() {
        // text size synchonization grp for indicators
        this.indicatorSyncGroup = new ArrayList<>();
        this.indicatorSyncGroup.add(bluetoothIndicatorText);
        this.indicatorSyncGroup.add(obdIndicatorText);
        this.indicatorSyncGroup.add(gpsIndicatorText);
        this.indicatorSyncGroup.add(carIndicatorText);

        SizeSyncTextView.OnTextSizeChangedListener listener =
                new SizeSyncTextView.OnTextSizeChangedListener() {
                    @SuppressLint("RestrictedApi")
                    @Override
                    public void onTextSizeChanged(SizeSyncTextView view, float size) {
                        for (SizeSyncTextView textView : indicatorSyncGroup) {
                            if (!textView.equals(view) && textView.getText() != view.getText()) {
                                textView.setAutoSizeTextTypeUniformWithPresetSizes(
                                        new int[]{(int) size}, TypedValue.COMPLEX_UNIT_PX);
                            }
                        }
                    }
                };

        for (SizeSyncTextView textView : indicatorSyncGroup) {
            textView.setOnTextSizeChangedListener(listener);
        }
    }

    private void appUpdateCheck() {
        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                try {
                    appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, getActivity(), 121);
                } catch (IntentSender.SendIntentException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 121 && resultCode != RESULT_OK) {
            appUpdateCheck();
        }
    }
}
