@file:Suppress("DEPRECATION")

package com.mikimn.apkloader

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.view.Display
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

open class MyContextWrapper(private val base: Context): Context() {
    override fun getAssets(): AssetManager {
         return base.assets
    }

    override fun getResources(): Resources {
        return base.resources
    }

    override fun getPackageManager(): PackageManager {
        return base.packageManager
    }

    override fun getContentResolver(): ContentResolver {
        return base.contentResolver
    }

    override fun getMainLooper(): Looper {
        return base.mainLooper
    }

    override fun getApplicationContext(): Context {
        return base.applicationContext
    }

    override fun setTheme(p0: Int) {
        return base.setTheme(p0)
    }

    override fun getTheme(): Resources.Theme {
        return base.theme
    }

    override fun getClassLoader(): ClassLoader {
        return base.classLoader
    }

    override fun getPackageName(): String {
        return base.packageName
    }

    override fun getApplicationInfo(): ApplicationInfo {
        return base.applicationInfo
    }

    override fun getPackageResourcePath(): String {
        return base.packageResourcePath
    }

    override fun getPackageCodePath(): String {
        return base.packageCodePath
    }

    override fun getSharedPreferences(p0: String?, p1: Int): SharedPreferences {
        return base.getSharedPreferences(p0, p1)
    }

    override fun moveSharedPreferencesFrom(p0: Context?, p1: String?): Boolean {
        return base.moveSharedPreferencesFrom(p0, p1)
    }

    override fun deleteSharedPreferences(p0: String?): Boolean {
        return base.deleteSharedPreferences(p0)
    }

    override fun openFileInput(p0: String?): FileInputStream {
        return base.openFileInput(p0)
    }

    override fun openFileOutput(p0: String?, p1: Int): FileOutputStream {
        return base.openFileOutput(p0, p1)
    }

    override fun deleteFile(p0: String?): Boolean {
        return base.deleteFile(p0)
    }

    override fun getFileStreamPath(p0: String?): File {
        return base.getFileStreamPath(p0)
    }

    override fun getDataDir(): File {
        return base.dataDir
    }

    override fun getFilesDir(): File {
        return base.filesDir
    }

    override fun getNoBackupFilesDir(): File {
        return base.noBackupFilesDir
    }

    override fun getExternalFilesDir(p0: String?): File? {
        return base.getExternalFilesDir(p0)
    }

    override fun getExternalFilesDirs(p0: String?): Array<File> {
        return base.getExternalFilesDirs(p0)
    }

    override fun getObbDir(): File {
        return base.obbDir
    }

    override fun getObbDirs(): Array<File> {
        return base.obbDirs
    }

    override fun getCacheDir(): File {
        return base.cacheDir
    }

    override fun getCodeCacheDir(): File {
        return base.codeCacheDir
    }

    override fun getExternalCacheDir(): File? {
        return base.externalCacheDir
    }

    override fun getExternalCacheDirs(): Array<File> {
        return base.externalCacheDirs
    }

    @Deprecated("Deprecated in Java")
    override fun getExternalMediaDirs(): Array<File> {
        return base.externalMediaDirs
    }

    override fun fileList(): Array<String> {
        return base.fileList()
    }

    override fun getDir(p0: String?, p1: Int): File {
        return base.getDir(p0, p1)
    }

    override fun openOrCreateDatabase(
        p0: String?,
        p1: Int,
        p2: SQLiteDatabase.CursorFactory?
    ): SQLiteDatabase {
        return base.openOrCreateDatabase(p0, p1, p2)
    }

    override fun openOrCreateDatabase(
        p0: String?,
        p1: Int,
        p2: SQLiteDatabase.CursorFactory?,
        p3: DatabaseErrorHandler?
    ): SQLiteDatabase {
        return base.openOrCreateDatabase(p0, p1, p2, p3)
    }

    override fun moveDatabaseFrom(p0: Context?, p1: String?): Boolean {
        return base.moveDatabaseFrom(p0, p1)
    }

    override fun deleteDatabase(p0: String?): Boolean {
        return base.deleteDatabase(p0)
    }

    override fun getDatabasePath(p0: String?): File {
        return base.getDatabasePath(p0)
    }

    override fun databaseList(): Array<String> {
        return base.databaseList()
    }

    @Deprecated("Deprecated in Java")
    override fun getWallpaper(): Drawable {
        return base.wallpaper
    }

    @Deprecated("Deprecated in Java")
    override fun peekWallpaper(): Drawable {
        return base.peekWallpaper()
    }

    @Deprecated("Deprecated in Java")
    override fun getWallpaperDesiredMinimumWidth(): Int {
        return base.wallpaperDesiredMinimumWidth
    }

    @Deprecated("Deprecated in Java")
    override fun getWallpaperDesiredMinimumHeight(): Int {
        return base.wallpaperDesiredMinimumHeight
    }

    @Deprecated("Deprecated in Java")
    override fun setWallpaper(p0: Bitmap?) {
        return base.setWallpaper(p0)
    }

    @Deprecated("Deprecated in Java")
    override fun setWallpaper(p0: InputStream?) {
        return base.setWallpaper(p0)
    }

    @Deprecated("Deprecated in Java")
    override fun clearWallpaper() {
        return base.clearWallpaper()
    }

    override fun startActivity(p0: Intent?) {
        return base.startActivity(p0)
    }

    override fun startActivity(p0: Intent?, p1: Bundle?) {
        return base.startActivity(p0, p1)
    }

    override fun startActivities(p0: Array<out Intent>?) {
        return base.startActivities(p0)
    }

    override fun startActivities(p0: Array<out Intent>?, p1: Bundle?) {
        return base.startActivities(p0, p1)
    }

    override fun startIntentSender(p0: IntentSender?, p1: Intent?, p2: Int, p3: Int, p4: Int) {
        return base.startIntentSender(p0, p1, p2, p3, p4)
    }

    override fun startIntentSender(
        p0: IntentSender?,
        p1: Intent?,
        p2: Int,
        p3: Int,
        p4: Int,
        p5: Bundle?
    ) {
        return base.startIntentSender(p0, p1, p2, p3, p4, p5)
    }

    override fun sendBroadcast(p0: Intent?) {
        return base.sendBroadcast(p0)
    }

    override fun sendBroadcast(p0: Intent?, p1: String?) {
        return base.sendBroadcast(p0, p1)
    }

    override fun sendOrderedBroadcast(p0: Intent?, p1: String?) {
        return base.sendOrderedBroadcast(p0, p1)
    }

    override fun sendOrderedBroadcast(
        p0: Intent,
        p1: String?,
        p2: BroadcastReceiver?,
        p3: Handler?,
        p4: Int,
        p5: String?,
        p6: Bundle?
    ) {
        return base.sendOrderedBroadcast(p0, p1, p2, p3, p4, p5, p6)
    }

    @SuppressLint("MissingPermission")
    override fun sendBroadcastAsUser(p0: Intent?, p1: UserHandle?) {
        return base.sendBroadcastAsUser(p0, p1)
    }

    @SuppressLint("MissingPermission")
    override fun sendBroadcastAsUser(p0: Intent?, p1: UserHandle?, p2: String?) {
        return base.sendBroadcastAsUser(p0, p1, p2)
    }

    @SuppressLint("MissingPermission")
    override fun sendOrderedBroadcastAsUser(
        p0: Intent?,
        p1: UserHandle?,
        p2: String?,
        p3: BroadcastReceiver?,
        p4: Handler?,
        p5: Int,
        p6: String?,
        p7: Bundle?
    ) {
        return base.sendOrderedBroadcastAsUser(p0, p1, p2, p3, p4, p5, p6, p7)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun sendStickyBroadcast(p0: Intent?) {
        return base.sendStickyBroadcast(p0)
    }

    @SuppressLint("MissingPermission")
    @Deprecated("Deprecated in Java")
    override fun sendStickyOrderedBroadcast(
        p0: Intent?,
        p1: BroadcastReceiver?,
        p2: Handler?,
        p3: Int,
        p4: String?,
        p5: Bundle?
    ) {
        return base.sendStickyOrderedBroadcast(p0, p1, p2, p3, p4, p5)
    }

    @SuppressLint("MissingPermission")
    @Deprecated("Deprecated in Java")
    override fun removeStickyBroadcast(p0: Intent?) {
        return base.removeStickyBroadcast(p0)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun sendStickyBroadcastAsUser(p0: Intent?, p1: UserHandle?) {
        return base.sendStickyBroadcastAsUser(p0, p1)
    }

    @SuppressLint("MissingPermission")
    @Deprecated("Deprecated in Java")
    override fun sendStickyOrderedBroadcastAsUser(
        p0: Intent?,
        p1: UserHandle?,
        p2: BroadcastReceiver?,
        p3: Handler?,
        p4: Int,
        p5: String?,
        p6: Bundle?
    ) {
        return base.sendStickyOrderedBroadcastAsUser(p0, p1, p2, p3, p4, p5, p6)
    }

    @SuppressLint("MissingPermission")
    @Deprecated("Deprecated in Java")
    override fun removeStickyBroadcastAsUser(p0: Intent?, p1: UserHandle?) {
        return base.removeStickyBroadcastAsUser(p0, p1)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun registerReceiver(p0: BroadcastReceiver?, p1: IntentFilter?): Intent? {
        return base.registerReceiver(p0, p1)
    }

    override fun registerReceiver(p0: BroadcastReceiver?, p1: IntentFilter?, p2: Int): Intent? {
        return base.registerReceiver(p0, p1, p2)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun registerReceiver(
        p0: BroadcastReceiver?,
        p1: IntentFilter?,
        p2: String?,
        p3: Handler?
    ): Intent? {
        return base.registerReceiver(p0, p1, p2, p3)
    }

    override fun registerReceiver(
        p0: BroadcastReceiver?,
        p1: IntentFilter?,
        p2: String?,
        p3: Handler?,
        p4: Int
    ): Intent? {
        return base.registerReceiver(p0, p1, p2, p3, p4)
    }

    override fun unregisterReceiver(p0: BroadcastReceiver?) {
        return base.unregisterReceiver(p0)
    }

    override fun startService(p0: Intent?): ComponentName? {
        return base.startService(p0)
    }

    override fun startForegroundService(p0: Intent?): ComponentName? {
        return base.startForegroundService(p0)
    }

    override fun stopService(p0: Intent?): Boolean {
        return base.stopService(p0)
    }

    override fun bindService(p0: Intent, p1: ServiceConnection, p2: Int): Boolean {
        return base.bindService(p0, p1, p2)
    }

    override fun unbindService(p0: ServiceConnection) {
        return base.unbindService(p0)
    }

    override fun startInstrumentation(p0: ComponentName, p1: String?, p2: Bundle?): Boolean {
        return base.startInstrumentation(p0, p1, p2)
    }

    override fun getSystemService(p0: String): Any {
        return base.getSystemService(p0)
    }

    override fun getSystemServiceName(p0: Class<*>): String? {
        return base.getSystemServiceName(p0)
    }

    override fun checkPermission(p0: String, p1: Int, p2: Int): Int {
        return base.checkPermission(p0, p1, p2)
    }

    override fun checkCallingPermission(p0: String): Int {
        return base.checkCallingPermission(p0)
    }

    override fun checkCallingOrSelfPermission(p0: String): Int {
        return base.checkCallingOrSelfPermission(p0)
    }

    override fun checkSelfPermission(p0: String): Int {
        return base.checkSelfPermission(p0)
    }

    override fun enforcePermission(p0: String, p1: Int, p2: Int, p3: String?) {
        return base.enforcePermission(p0, p1, p2, p3)
    }

    override fun enforceCallingPermission(p0: String, p1: String?) {
        return base.enforceCallingPermission(p0, p1)
    }

    override fun enforceCallingOrSelfPermission(p0: String, p1: String?) {
        return base.enforceCallingOrSelfPermission(p0, p1)
    }

    override fun grantUriPermission(p0: String?, p1: Uri?, p2: Int) {
        return base.grantUriPermission(p0, p1, p2)
    }

    override fun revokeUriPermission(p0: Uri?, p1: Int) {
        return base.revokeUriPermission(p0, p1)
    }

    override fun revokeUriPermission(p0: String?, p1: Uri?, p2: Int) {
        return base.revokeUriPermission(p0, p1, p2)
    }

    override fun checkUriPermission(p0: Uri?, p1: Int, p2: Int, p3: Int): Int {
        return base.checkUriPermission(p0, p1, p2, p3)
    }

    override fun checkUriPermission(
        p0: Uri?,
        p1: String?,
        p2: String?,
        p3: Int,
        p4: Int,
        p5: Int
    ): Int {
        return base.checkUriPermission(p0, p1, p2, p3, p4, p5)
    }

    override fun checkCallingUriPermission(p0: Uri?, p1: Int): Int {
        return base.checkCallingUriPermission(p0, p1)
    }

    override fun checkCallingOrSelfUriPermission(p0: Uri?, p1: Int): Int {
        return base.checkCallingOrSelfUriPermission(p0, p1)
    }

    override fun enforceUriPermission(p0: Uri?, p1: Int, p2: Int, p3: Int, p4: String?) {
        return base.enforceUriPermission(p0, p1, p2, p3, p4)
    }

    override fun enforceUriPermission(
        p0: Uri?,
        p1: String?,
        p2: String?,
        p3: Int,
        p4: Int,
        p5: Int,
        p6: String?
    ) {
        return base.enforceUriPermission(p0, p1, p2, p3, p4, p5, p6)
    }

    override fun enforceCallingUriPermission(p0: Uri?, p1: Int, p2: String?) {
        return base.enforceCallingUriPermission(p0, p1, p2)
    }

    override fun enforceCallingOrSelfUriPermission(p0: Uri?, p1: Int, p2: String?) {
        return base.enforceCallingOrSelfUriPermission(p0, p1, p2)
    }

    override fun createPackageContext(p0: String?, p1: Int): Context {
        return base.createPackageContext(p0, p1)
    }

    override fun createContextForSplit(p0: String?): Context {
        return base.createContextForSplit(p0)
    }

    override fun createConfigurationContext(p0: Configuration): Context {
        return base.createConfigurationContext(p0)
    }

    override fun createDisplayContext(p0: Display): Context {
        return base.createDisplayContext(p0)
    }

    override fun createDeviceProtectedStorageContext(): Context {
        return base.createDeviceProtectedStorageContext()
    }

    override fun isDeviceProtectedStorage(): Boolean {
        return base.isDeviceProtectedStorage
    }
}