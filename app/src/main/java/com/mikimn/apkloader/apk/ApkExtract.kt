package com.mikimn.apkloader.apk
//
//import android.R.attr.version
//import android.content.pm.ApplicationInfo
//import android.content.pm.PackageManager
//import android.os.Build
//import android.view.View
//import java.io.File
//
//
//class ApkExtract(val packageManager: PackageManager) {
//    class NewThread : Thread() {
//        override fun run() {
//            list.clear()
//            super.run()
//            var i = 0
//            val manager: PackageManager = getPackageManager()
//            if (packagelist.size() > 0) {
//                for (applicationInfo in packagelist) {
//                    i++
//                    if ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
//                        flag = 1
//                    } else {
//                        flag = 0
//                    }
//                    val stringBuilder = StringBuilder()
//                    packagename = applicationInfo.packageName
//                    try {
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//                            version =
//                                manager.getPackageInfo(packagename, 0).getLongVersionCode().toInt()
//                                    .toString()
//                        } else {
//                            version = manager.getPackageInfo(packagename, 0).versionCode.toString()
//                        }
//                        vername = manager.getPackageInfo(packagename, 0).versionName
//                        val reqper: Array<String> = manager.getPackageInfo(
//                            packagename,
//                            PackageManager.GET_PERMISSIONS
//                        ).requestedPermissions
//                        if (reqper != null) {
//                            for (per in reqper) {
//                                stringBuilder.append("\n").append(per)
//                            }
//                        } else {
//                            permissions = null
//                        }
//                    } catch (e: PackageManager.NameNotFoundException) {
//                        e.printStackTrace()
//                    }
//                    permissions = stringBuilder.toString()
//                    icon = applicationInfo.loadIcon(getPackageManager())
//                    name = applicationInfo.loadLabel(getPackageManager()).toString()
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                        minsdk = applicationInfo.minSdkVersion.toString()
//                    }
//                    targetsdk = applicationInfo.targetSdkVersion.toString()
//                    uid = applicationInfo.uid.toString()
//                    file = File(applicationInfo.publicSourceDir)
//                    longsize = file.length()
//                    if (longsize > 1024 && longsize <= 1024 * 1024) {
//                        size = ((longsize / 1024).toString() + " KB")
//                    } else if (longsize > 1024 * 1024 && longsize <= 1024 * 1024 * 1024) {
//                        size = ((longsize / (1024 * 1024)).toString() + " MB")
//                    } else {
//                        size = ((longsize / (1024 * 1024 * 1024)).toString() + " GB")
//                    }
//                    list.add(
//                        AppListModel(
//                            icon,
//                            name,
//                            packagename,
//                            file,
//                            size,
//                            flag,
//                            version,
//                            targetsdk,
//                            minsdk,
//                            uid,
//                            permissions,
//                            vername
//                        )
//                    )
//                    if (i == packagelist.size() - 1) {
//                        runOnUiThread(Runnable {
//                            val layoutManager: LinearLayoutManager =
//                                LinearLayoutManager(this@MainActivity)
//                            appListAdapter = AppListAdapter(
//                                this@MainActivity,
//                                list,
//                                Color.parseColor(preferences.getCircleColor()),
//                                recycler_apps,
//                                this@MainActivity
//                            )
//                            appListAdapter.notifyDataSetChanged()
//                            recycler_apps.setAdapter(appListAdapter)
//                            recycler_apps.setLayoutManager(layoutManager)
//                            recycler_apps.setHasFixedSize(true)
//                            recycler_apps.setVisibility(View.VISIBLE)
//                            txt_loading.setVisibility(View.GONE)
//                            progressBar.setVisibility(View.GONE)
//                            txt_copyright.setVisibility(View.GONE)
//                        })
//                    }
//                }
//            }
//        }
//    }
//}