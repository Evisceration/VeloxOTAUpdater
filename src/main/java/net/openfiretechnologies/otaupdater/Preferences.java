package net.openfiretechnologies.otaupdater;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

/**
 * Created by alex on 24.09.13.
 */
public class Preferences extends PreferenceActivity implements Preference.OnPreferenceChangeListener {

    CheckBoxPreference mDebugLog, mBetaUpdate;
    private SharedPreferences mPrefs;

    private static final String PREFS_DEBUG_LOG = "prefs_debug_log";
    private static final String PREFS_UPDATE_BETA = "prefs_update_beta";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        PreferenceScreen prefSet = getPreferenceScreen();

        mDebugLog = (CheckBoxPreference) prefSet.findPreference(PREFS_DEBUG_LOG);
        mBetaUpdate = (CheckBoxPreference) prefSet.findPreference(PREFS_UPDATE_BETA);

    }


    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {

        if (preference == mDebugLog) {
            mPrefs.edit().putBoolean(PREFS_DEBUG_LOG, mDebugLog.isChecked()).commit();
        } else if (preference == mBetaUpdate) {
            mPrefs.edit().putBoolean(PREFS_UPDATE_BETA, mBetaUpdate.isChecked()).commit();
        }

        return false;
    }
}
