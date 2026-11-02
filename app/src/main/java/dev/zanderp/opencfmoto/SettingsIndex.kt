// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

/** Static search index + category list for [SettingsHubActivity]. Generated from the settings layout; no pref access. */
internal object SettingsIndex {
    const val DASH = 1
    const val CRASH = 2
    const val LANGUAGE = 3

    class Cat(val section: String, val title: Int, val sub: Int, val icon: Int, val special: Int = 0)
    class Entry(val section: String, val label: Int, val keywords: Int, val focusId: Int, val special: Int)

    val categories = listOf(
        Cat("connect", R.string.sr_cat_connect, R.string.sr_cat_connect_sub, R.drawable.ic_set_connect, 0),
        Cat("screen", R.string.sr_cat_screen, R.string.sr_cat_screen_sub, R.drawable.ic_set_screen, 0),
        Cat("map", R.string.sr_cat_map, R.string.sr_cat_map_sub, R.drawable.ic_set_map, 0),
        Cat("controls", R.string.sr_cat_controls, R.string.sr_cat_controls_sub, R.drawable.ic_set_controls, 0),
        Cat("safety", R.string.sr_cat_safety, R.string.sr_cat_safety_sub, R.drawable.ic_set_safety, CRASH),
        Cat("trips", R.string.sr_cat_trips, R.string.sr_cat_trips_sub, R.drawable.ic_set_trips, 0),
        Cat("sound", R.string.sr_cat_sound, R.string.sr_cat_sound_sub, R.drawable.ic_set_sound, 0),
        Cat("advanced", R.string.sr_cat_advanced, R.string.sr_cat_advanced_sub, R.drawable.ic_set_advanced, 0),
        Cat("language", R.string.sr_cat_language, R.string.sr_cat_language_sub, R.drawable.ic_set_language, LANGUAGE),
    )

    val entries = listOf(
        Entry("connect", R.string.sr_i_permissions, R.string.sr_k_permissions, R.id.step2_title, 0),
        Entry("connect", R.string.sr_i_aa_install, R.string.sr_k_aa_install, R.id.step1_title, 0),
        Entry("connect", R.string.sr_i_autoconnect, R.string.sr_k_autoconnect, R.id.autoconnect_desc, 0),
        Entry("connect", R.string.sr_i_bt_trigger, R.string.sr_k_bt_trigger, R.id.bt_trigger_desc, 0),
        Entry("connect", R.string.sr_i_wait_bike, R.string.sr_k_wait_bike, R.id.switch_wait_for_bike, 0),
        Entry("connect", R.string.sr_i_recovery, R.string.sr_k_recovery, R.id.recovery_desc, 0),
        Entry("connect", R.string.sr_i_resume, R.string.sr_k_resume, R.id.resume_perm_btn, 0),
        Entry("connect", R.string.sr_i_keepwifi, R.string.sr_k_keepwifi, R.id.keepwifi_desc, 0),
        Entry("screen", R.string.sr_i_dash_design, R.string.sr_k_dash_design, 0, DASH),
        Entry("screen", R.string.sr_i_fit_dash, R.string.sr_k_fit_dash, R.id.btn_fit_to_dash, 0),
        Entry("screen", R.string.sr_i_screen_fit, R.string.sr_k_screen_fit, R.id.fit_desc, 0),
        Entry("screen", R.string.sr_i_mirror_orient, R.string.sr_k_mirror_orient, R.id.mirror_orient_desc, 0),
        Entry("screen", R.string.sr_i_margins, R.string.sr_k_margins, R.id.btn_screen_margins, 0),
        Entry("screen", R.string.sr_i_resolution, R.string.sr_k_resolution, R.id.res_desc, 0),
        Entry("screen", R.string.sr_i_dpi, R.string.sr_k_dpi, R.id.dpi_desc, 0),
        Entry("map", R.string.sr_i_map_theme, R.string.sr_k_map_theme, R.id.theme_desc, 0),
        Entry("controls", R.string.sr_i_dbltap, R.string.sr_k_dbltap, R.id.dbltap_desc, 0),
        Entry("controls", R.string.sr_i_holds, R.string.sr_k_holds, R.id.holds_desc, 0),
        Entry("controls", R.string.sr_i_hold_delay, R.string.sr_k_hold_delay, R.id.hold_desc, 0),
        Entry("controls", R.string.sr_i_bluetooth, R.string.sr_k_bluetooth, R.id.bt_status, 0),
        Entry("safety", R.string.sr_i_crash, R.string.sr_k_crash, 0, CRASH),
        Entry("trips", R.string.sr_i_logtrips, R.string.sr_k_logtrips, R.id.logtrips_on, 0),
        Entry("trips", R.string.sr_i_parking, R.string.sr_k_parking, R.id.extras_park_on, 0),
        Entry("trips", R.string.sr_i_rain, R.string.sr_k_rain, R.id.extras_rain_on, 0),
        Entry("trips", R.string.sr_i_turns, R.string.sr_k_turns, R.id.extras_turn_on, 0),
        Entry("sound", R.string.sr_i_conn_sound, R.string.sr_k_conn_sound, R.id.switch_connect_sound, 0),
        Entry("sound", R.string.sr_i_clock_lab, R.string.sr_k_clock_lab, 0, 0),
        Entry("sound", R.string.sr_i_bt_clock, R.string.sr_k_bt_clock, R.id.btclock_desc, 0),
        Entry("sound", R.string.sr_i_clock_resync, R.string.sr_k_clock_resync, R.id.clockresync_smart, 0),
        Entry("advanced", R.string.sr_i_quality, R.string.sr_k_quality, R.id.quality_desc, 0),
        Entry("advanced", R.string.sr_i_nontouch, R.string.sr_k_nontouch, R.id.nontouch_desc, 0),
        Entry("advanced", R.string.sr_i_forcetouch, R.string.sr_k_forcetouch, R.id.forcetouch_desc, 0),
        Entry("advanced", R.string.sr_i_battery, R.string.sr_k_battery, R.id.power_desc, 0),
        Entry("advanced", R.string.sr_i_profile, R.string.sr_k_profile, R.id.profile_desc, 0),
        Entry("advanced", R.string.sr_i_transport, R.string.sr_k_transport, R.id.transport_desc, 0),
        Entry("advanced", R.string.sr_i_telemetry, R.string.sr_k_telemetry, R.id.telemetry_desc, 0),
        Entry("advanced", R.string.sr_i_secrets, R.string.sr_k_secrets, R.id.secrets_desc, 0),
        Entry("advanced", R.string.sr_i_share_settings, R.string.sr_k_share_settings, R.id.settings_share, 0),
        Entry("bikes", R.string.sr_i_bikes, R.string.sr_k_bikes, 0, 0),
        Entry("language", R.string.sr_cat_language, R.string.sr_lang_keywords, 0, LANGUAGE),
    )
}
