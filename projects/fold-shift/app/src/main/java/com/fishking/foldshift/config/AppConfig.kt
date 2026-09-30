package com.fishking.foldshift.config

/**
 * Static configuration constants. Kept in one place so Phase 2 doesn't have
 * to rediscover package names.
 */
object AppConfig {
    /** Package name we advertise for our own defaults. */
    const val PACKAGE_NAME: String = "com.fishking.foldshift"

    /** Outer-screen / cover-screen launcher default. */
    const val DEFAULT_OUTER_PACKAGE: String = "bitpit.launcher"

    /** Inner-screen / main-screen launcher default. */
    const val DEFAULT_INNER_PACKAGE: String = "com.sec.android.app.launcher"
}