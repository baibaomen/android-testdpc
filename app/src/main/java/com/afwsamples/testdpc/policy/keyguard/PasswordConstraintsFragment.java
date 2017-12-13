/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.afwsamples.testdpc.policy.keyguard;

import android.app.Activity;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.widget.Toast;

import com.afwsamples.testdpc.DeviceAdminReceiver;
import com.afwsamples.testdpc.R;
import com.afwsamples.testdpc.common.ProfileOrParentFragment;
import com.afwsamples.testdpc.common.Util;
import com.afwsamples.testdpc.common.preference.CustomConstraint;
import com.afwsamples.testdpc.common.preference.DpcPreference;
import com.afwsamples.testdpc.common.preference.DpcPreferenceBase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_SOMETHING;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
import static com.afwsamples.testdpc.common.preference.DpcPreferenceHelper.NO_CUSTOM_CONSTRIANT;

/**
 * This fragment provides functionalities to set password constraint policies as a profile
 * or device owner. In the former case, it is also possible to set password constraints on
 * the parent profile.
 *
 * <p>These include:
 * <ul>
 * <li>{@link DevicePolicyManager#setPasswordQuality(ComponentName, int)}</li>
 * <li>{@link DevicePolicyManager#setPasswordMinimumLength(ComponentName, int)}</li>
 * <li>{@link DevicePolicyManager#setPasswordMinimumLetters(ComponentName, int)}</li>
 * <li>{@link DevicePolicyManager#setPasswordMinimumNumeric(ComponentName, int)}</li>
 * <li>{@link DevicePolicyManager#setPasswordMinimumLowerCase(ComponentName, int)}</li>
 * <li>{@link DevicePolicyManager#setPasswordMinimumUpperCase(ComponentName, int)}</li>
 * <li>{@link DevicePolicyManager#setPasswordMinimumSymbols(ComponentName, int)}</li>
 * <li>{@link DevicePolicyManager#setPasswordMinimumNonLetter(ComponentName, int)}</li>
 * <li>{@link DevicePolicyManager#setPasswordHistoryLength(ComponentName, int)}</li>
 * <li>{@link DevicePolicyManager#setPasswordBlacklist(ComponentName, String, List<String>)}</li>
 * </ul>
 */
public final class PasswordConstraintsFragment extends ProfileOrParentFragment implements
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    private static final int READ_BLACKLIST_FILE_CODE = 42;

    private DpcPreferenceBase mMinLength;
    private DpcPreferenceBase mMinLetters;
    private DpcPreferenceBase mMinNumeric;
    private DpcPreferenceBase mMinLower;
    private DpcPreferenceBase mMinUpper;
    private DpcPreferenceBase mMinSymbols;
    private DpcPreferenceBase mMinNonLetter;
    private DpcPreference mClearBlacklist;

    public static class Container extends ProfileOrParentFragment.Container {
        @Override
        public Class<? extends ProfileOrParentFragment> getContentClass() {
            return PasswordConstraintsFragment.class;
        }
    }

    abstract static class Keys {
        final static String EXPIRATION_TIME = "password_expiration_time";
        final static String EXPIRATION_BY_ALL = "password_expiration_aggregate";
        final static String HISTORY_LENGTH = "password_history_length";

        final static String QUALITY = "minimum_password_quality";

        final static String MIN_LENGTH = "password_min_length";
        final static String MIN_LETTERS = "password_min_letters";
        final static String MIN_NUMERIC = "password_min_numeric";
        final static String MIN_LOWERCASE = "password_min_lowercase";
        final static String MIN_UPPERCASE = "password_min_uppercase";
        final static String MIN_SYMBOLS = "password_min_symbols";
        final static String MIN_NONLETTER = "password_min_nonletter";

        final static String SET_PASSWORD_BLACKLIST_KEY = "set_password_blacklist";
        final static String LOAD_PASSWORD_BLACKLIST_KEY = "load_password_blacklist";
        final static String CLEAR_PASSWORD_BLACKLIST_KEY = "clear_password_blacklist";
    }

    private static final TreeMap<Integer, Integer> PASSWORD_QUALITIES = new TreeMap<>();
    static {
        // IDs of settings for {@link DevicePolicyManager#setPasswordQuality(ComponentName, int)}.
        final int[] policyIds = new int[] {
            PASSWORD_QUALITY_UNSPECIFIED,
            PASSWORD_QUALITY_SOMETHING,
            PASSWORD_QUALITY_NUMERIC,
            PASSWORD_QUALITY_NUMERIC_COMPLEX,
            PASSWORD_QUALITY_ALPHABETIC,
            PASSWORD_QUALITY_ALPHANUMERIC,
            PASSWORD_QUALITY_COMPLEX
        };
        // Strings to show for each password quality setting.
        final int[] policyNames = new int[] {
            R.string.password_quality_unspecified,
            R.string.password_quality_something,
            R.string.password_quality_numeric,
            R.string.password_quality_numeric_complex,
            R.string.password_quality_alphabetic,
            R.string.password_quality_alphanumeric,
            R.string.password_quality_complex
        };
        if (policyIds.length != policyNames.length) {
            throw new AssertionError("Number of items in policyIds and policyNames do not match");
        }
        for (int i = 0; i < policyIds.length; i++) {
            PASSWORD_QUALITIES.put(policyIds[i], policyNames[i]);
        }
    }

    @Override
    public boolean isAvailable(Context context) {
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        getActivity().getActionBar().setTitle(R.string.password_constraints);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.password_constraint_preferences);

        mMinLength = (DpcPreferenceBase) findPreference(Keys.MIN_LENGTH);
        mMinLetters = (DpcPreferenceBase) findPreference(Keys.MIN_LETTERS);
        mMinNumeric = (DpcPreferenceBase) findPreference(Keys.MIN_NUMERIC);
        mMinLower = (DpcPreferenceBase) findPreference(Keys.MIN_LOWERCASE);
        mMinUpper = (DpcPreferenceBase) findPreference(Keys.MIN_UPPERCASE);
        mMinSymbols = (DpcPreferenceBase) findPreference(Keys.MIN_SYMBOLS);
        mMinNonLetter = (DpcPreferenceBase) findPreference(Keys.MIN_NONLETTER);
        mClearBlacklist = (DpcPreference) findPreference(Keys.CLEAR_PASSWORD_BLACKLIST_KEY);

        // Populate password quality settings - messy because the only API for this requires two
        // separate String[]s.
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : PASSWORD_QUALITIES.entrySet()) {
            values.add(Integer.toString(entry.getKey()));
            entries.add(getString(entry.getValue()));
        }
        ListPreference quality = (ListPreference) findPreference(Keys.QUALITY);
        quality.setEntries(entries.toArray(new CharSequence[0]));
        quality.setEntryValues(values.toArray(new CharSequence[0]));

        // Expiration times.
        setup(Keys.EXPIRATION_TIME, null);
        setup(Keys.HISTORY_LENGTH, getDpm().getPasswordHistoryLength(getAdmin()));

        // Minimum quality requirement.
        setup(Keys.QUALITY, PASSWORD_QUALITIES.floorKey(getDpm().getPasswordQuality(getAdmin())));

        // Minimum length requirements.
        setup(Keys.MIN_LENGTH, getDpm().getPasswordMinimumLength(getAdmin()));
        setup(Keys.MIN_LETTERS, getDpm().getPasswordMinimumLetters(getAdmin()));
        setup(Keys.MIN_NUMERIC, getDpm().getPasswordMinimumNumeric(getAdmin()));
        setup(Keys.MIN_LOWERCASE, getDpm().getPasswordMinimumLowerCase(getAdmin()));
        setup(Keys.MIN_UPPERCASE, getDpm().getPasswordMinimumUpperCase(getAdmin()));
        setup(Keys.MIN_SYMBOLS, getDpm().getPasswordMinimumSymbols(getAdmin()));
        setup(Keys.MIN_NONLETTER, getDpm().getPasswordMinimumNonLetter(getAdmin()));

        findPreference(Keys.SET_PASSWORD_BLACKLIST_KEY).setOnPreferenceClickListener(this);
        findPreference(Keys.LOAD_PASSWORD_BLACKLIST_KEY).setOnPreferenceClickListener(this);
        mClearBlacklist.setOnPreferenceClickListener(this);

        setPreferencesConstraint();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Settings that may have been changed by other users need updating.
        updateExpirationTimes();
        refreshBlacklistPreferences();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final int value;
        if (newValue instanceof String && ((String) newValue).length() != 0) {
            try {
                value = Integer.parseInt((String) newValue);
            } catch (NumberFormatException e) {
                Toast.makeText(getActivity(), R.string.not_valid_input, Toast.LENGTH_SHORT).show();
                return false;
            }
        } else {
            value = 0;
        }

        // By default, show the new value as a summary.
        CharSequence summary = newValue.toString();

        switch (preference.getKey()) {
            case Keys.EXPIRATION_TIME: {
                getDpm().setPasswordExpirationTimeout(getAdmin(), TimeUnit.SECONDS.toMillis(value));
                updateExpirationTimes();
                return true;
            }
            case Keys.HISTORY_LENGTH:
                getDpm().setPasswordHistoryLength(getAdmin(), value);
                break;
            case Keys.QUALITY: {
                final ListPreference list = (ListPreference) preference;
                // Store newValue now so getEntry() can return the new setting
                list.setValue((String) newValue);
                summary = list.getEntry();
                getDpm().setPasswordQuality(getAdmin(), value);
                refreshPreferences();
                break;
            }
            case Keys.MIN_LENGTH:
                getDpm().setPasswordMinimumLength(getAdmin(), value);
                break;
            case Keys.MIN_LETTERS:
                getDpm().setPasswordMinimumLetters(getAdmin(), value);
                break;
            case Keys.MIN_NUMERIC:
                getDpm().setPasswordMinimumNumeric(getAdmin(), value);
                break;
            case Keys.MIN_LOWERCASE:
                getDpm().setPasswordMinimumLowerCase(getAdmin(), value);
                break;
            case Keys.MIN_UPPERCASE:
                getDpm().setPasswordMinimumUpperCase(getAdmin(), value);
                break;
            case Keys.MIN_SYMBOLS:
                getDpm().setPasswordMinimumSymbols(getAdmin(), value);
                break;
            case Keys.MIN_NONLETTER:
                getDpm().setPasswordMinimumNonLetter(getAdmin(), value);
                break;
            default:
                return false;
        }

        preference.setSummary(summary);
        DeviceAdminReceiver.sendPasswordRequirementsChanged(getActivity());
        return true;
    }

    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        switch (key) {
            case Keys.SET_PASSWORD_BLACKLIST_KEY:
                showPasswordBlacklistFragment();
                return true;
            case Keys.LOAD_PASSWORD_BLACKLIST_KEY:
                Util.showFilePicker(this, "text/plain", READ_BLACKLIST_FILE_CODE);
                return true;
            case Keys.CLEAR_PASSWORD_BLACKLIST_KEY:
                PasswordBlacklistFragment.setBlacklist(getDpm(), getAdmin(), null, null);
                refreshBlacklistPreferences();
                return true;
            default:
                break;
        }

        return false;
    }

    private void showPasswordBlacklistFragment() {
        final PasswordBlacklistFragment fragment = new PasswordBlacklistFragment();

        fragment.passwordConstraintsFragment = this;
        fragment.setDpm(getDpm());

        Fragment containerFragment = getParentFragment();
        if (containerFragment == null) {
            containerFragment = this;
        }
        containerFragment.getFragmentManager().beginTransaction()
                .addToBackStack(PasswordBlacklistFragment.class.getName())
                .hide(containerFragment)
                .add(R.id.container, fragment)
                .commit();
    }

    /**
     * Enable and disable password constraint preferences based on the current password quality.
     */
    private void setPreferencesConstraint() {
        // Minimum length can be set for most qualities
        mMinLength.setCustomConstraint(
                () -> getDpm().getPasswordQuality(getAdmin()) >= PASSWORD_QUALITY_NUMERIC
                        ? NO_CUSTOM_CONSTRIANT
                        : R.string.not_for_password_quality);

        // Other minimums are only active for the highest quality
        CustomConstraint constraint =
                () -> getDpm().getPasswordQuality(getAdmin()) == PASSWORD_QUALITY_COMPLEX
                        ? NO_CUSTOM_CONSTRIANT
                        : R.string.not_for_password_quality;
        mMinLetters.setCustomConstraint(constraint);
        mMinNumeric.setCustomConstraint(constraint);
        mMinLower.setCustomConstraint(constraint);
        mMinUpper.setCustomConstraint(constraint);
        mMinSymbols.setCustomConstraint(constraint);
        mMinNonLetter.setCustomConstraint(constraint);

        mClearBlacklist.setCustomConstraint(
                () -> PasswordBlacklistFragment.getBlacklistName(getDpm(), getAdmin()) != null
                        ? NO_CUSTOM_CONSTRIANT : R.string.password_blacklist_no_blacklist);
    }

    private void refreshPreferences() {
        mMinLength.refreshEnabledState();
        mMinLetters.refreshEnabledState();
        mMinNumeric.refreshEnabledState();
        mMinLower.refreshEnabledState();
        mMinUpper.refreshEnabledState();
        mMinSymbols.refreshEnabledState();
        mMinNonLetter.refreshEnabledState();
    }

    void refreshBlacklistPreferences() {
        mClearBlacklist.refreshEnabledState();
        mClearBlacklist.setSummary(PasswordBlacklistFragment.getBlacklistName(getDpm(), getAdmin()));
    }

    /**
     * Set an initial value. Updates the summary to match.
     */
    private void setup(String key, Object adminSetting) {
        Preference field = findPreference(key);
        field.setOnPreferenceChangeListener(this);

        if (adminSetting == null) {
            return;
        }

        final String stringSetting = adminSetting.toString();
        CharSequence summary = stringSetting;

        if (field instanceof EditTextPreference) {
            EditTextPreference p = (EditTextPreference) field;
            p.setText(stringSetting);
        } else if (field instanceof ListPreference) {
            ListPreference p = (ListPreference) field;
            p.setValue(stringSetting);
            summary = p.getEntry();
        }
        field.setSummary(summary);
    }

    /**
     * Refresh summaries for settings related to the next password expiration.
     */
    private void updateExpirationTimes() {
        final Preference byAdmin = findPreference(Keys.EXPIRATION_TIME);
        final Preference byAll = findPreference(Keys.EXPIRATION_BY_ALL);

        byAdmin.setSummary(Util.formatTimestamp(getDpm().getPasswordExpiration(getAdmin())));
        byAll.setSummary(Util.formatTimestamp(getDpm().getPasswordExpiration(null)));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_BLACKLIST_FILE_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                final Uri uri = resultData.getData();
                final List<String> blacklist;
                try {
                    blacklist = readTextFileLines(uri);
                } catch (IOException e) {
                    Toast.makeText(getActivity(), R.string.password_blacklist_load_failed,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                final String name = DateFormat.getDateTimeInstance().format(new Date());
                if (!PasswordBlacklistFragment.setBlacklist(getDpm(), getAdmin(), name,
                        blacklist)) {
                    Toast.makeText(getActivity(), R.string.password_blacklist_save_failed,
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private List<String> readTextFileLines(Uri uri) throws IOException {
        final List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getActivity().getApplicationContext().getContentResolver().openInputStream(uri)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
