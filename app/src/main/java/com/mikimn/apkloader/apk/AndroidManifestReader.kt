package com.mikimn.apkloader.apk

import android.content.ComponentName
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.content.res.Resources.NotFoundException
import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.content.res.loader.ResourcesLoader
import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import androidx.core.text.isDigitsOnly
import fr.xgouchet.axml.CompressedXmlParser
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.io.InputStream


class AndroidManifestReader(private val baseDir: File, private val inputStream: InputStream, private val resources: Resources) {
    private val document = CompressedXmlParser().parseDOM(inputStream)
    private var applicationInfo: ApplicationInfo? = null
    private var services: List<ServiceInfo>? = null
    private var providers: List<ProviderInfo>? = null
    private var activities: List<Pair<ActivityInfo, List<IntentFilter>>>? = null

    private companion object {
        val LAUNCH_MODES = mapOf(
            "standard" to ActivityInfo.LAUNCH_MULTIPLE,
            "singleTop" to ActivityInfo.LAUNCH_SINGLE_TOP,
            "singleTask" to ActivityInfo.LAUNCH_SINGLE_TASK,
            "singleInstance" to ActivityInfo.LAUNCH_SINGLE_INSTANCE,
        )

        // android:screenOrientation, as ActivityInfo.SCREEN_ORIENTATION_*.
        val SCREEN_ORIENTATIONS = mapOf(
            "unspecified" to ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            "landscape" to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            "portrait" to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            "user" to ActivityInfo.SCREEN_ORIENTATION_USER,
            "behind" to ActivityInfo.SCREEN_ORIENTATION_BEHIND,
            "sensor" to ActivityInfo.SCREEN_ORIENTATION_SENSOR,
            "nosensor" to ActivityInfo.SCREEN_ORIENTATION_NOSENSOR,
            "sensorLandscape" to ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            "sensorPortrait" to ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            "reverseLandscape" to ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            "reversePortrait" to ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            "fullSensor" to ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            "userLandscape" to ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
            "userPortrait" to ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
            "fullUser" to ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
            "locked" to ActivityInfo.SCREEN_ORIENTATION_LOCKED,
        )

        // android:windowSoftInputMode: state* (low nibble) | adjust* (second nibble), as the
        // WindowManager.LayoutParams.SOFT_INPUT_* values.
        val SOFT_INPUT_MODES = mapOf(
            "stateUnspecified" to 0, "stateUnchanged" to 1, "stateHidden" to 2, "stateAlwaysHidden" to 3,
            "stateVisible" to 4, "stateAlwaysVisible" to 5,
            "adjustUnspecified" to 0x00, "adjustResize" to 0x10, "adjustPan" to 0x20, "adjustNothing" to 0x30,
        )

        // android:configChanges, as ActivityInfo.CONFIG_* (the same bits Configuration.diff reports).
        val CONFIG_CHANGES = mapOf(
            "mcc" to 0x0001, "mnc" to 0x0002, "locale" to 0x0004, "touchscreen" to 0x0008,
            "keyboard" to 0x0010, "keyboardHidden" to 0x0020, "navigation" to 0x0040,
            "orientation" to 0x0080, "screenLayout" to 0x0100, "uiMode" to 0x0200,
            "screenSize" to 0x0400, "smallestScreenSize" to 0x0800, "density" to 0x1000,
            "layoutDirection" to 0x2000, "colorMode" to 0x4000, "grammaticalGender" to 0x8000,
            "fontScale" to 0x40000000, "fontWeightAdjustment" to 0x10000000,
        )
    }

    fun parseActivities(): List<Pair<ActivityInfo, List<IntentFilter>>> {
        if (activities != null) {
            return activities!!
        }

        val result = mutableListOf<Pair<ActivityInfo, List<IntentFilter>>>()

        val appInfo = getApplicationInfo()

        val activities = document.getElementsByTagName("activity")

        for (i in 0 until activities.length) {
            val info = ActivityInfo()
            info.applicationInfo = appInfo

            val node = activities.item(i)
            for (j in 0 until node.attributes.length) {
                val attr = node.attributes.item(j)
                if (attr.localName == "name") {
                    info.name = attr.nodeValue
                } else if (attr.localName == "theme") {
                    info.theme = attr.nodeValue.replace("@id/0x", "").toInt(16)
                } else {
                    applyActivityAttribute(info, attr.localName, attr.nodeValue)
                }
            }

            val intentFilters = mutableListOf<IntentFilter>()
            val intentFilterTags = getChildrenByTagName(node, "intent-filter")

            for (intentFilterNode in intentFilterTags) {
                val intentFilter = IntentFilter()

                for (actionNode in getChildrenByTagName(intentFilterNode, "action")) {
                    intentFilter.addAction(actionNode.attributes.getNamedItem("android:name").nodeValue)
                }

                for (actionNode in getChildrenByTagName(intentFilterNode, "action")) {
                    intentFilter.addAction(actionNode.attributes.getNamedItem("android:name").nodeValue)
                }

                for (catNode in getChildrenByTagName(intentFilterNode, "category")) {
                    intentFilter.addCategory(catNode.attributes.getNamedItem("android:name").nodeValue)
                }

                // TODO Data

                intentFilters.add(intentFilter)
            }

            result.add(info to intentFilters.toList())
        }

        this.activities = result.toList()
        return result.toList()
    }

    fun getActivityInfo(componentName: ComponentName, flags: Int): ActivityInfo {
        val activityInfos = parseActivities()
        return activityInfos.find { it.first.name == componentName.className }?.first
            ?: throw IllegalArgumentException(componentName.flattenToString())
    }

    private fun getChildrenByTagName(node: Node, tagName: String): List<Node> {
        val result = mutableListOf<Node>()
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeName == tagName) {
                result.add(child)
            }
        }

        return result.toList()
    }

    fun getLauncherActivity(): ActivityInfo? {
        val appInfo = getApplicationInfo()

        val activities = document.getElementsByTagName("activity")

        for (i in 0 until activities.length) {
            val info = ActivityInfo()
            info.applicationInfo = appInfo

            val node = activities.item(i)
            val filters = getChildrenByTagName(node, "intent-filter")

            for (filter in filters) {
                val actions = getChildrenByTagName(filter, "action").filter {
                    it.attributes.getNamedItem("android:name").nodeValue == "android.intent.action.MAIN"
                }

                if (actions.isNotEmpty()) {
                    for (j in 0 until node.attributes.length) {
                        val attr = node.attributes.item(j)
                        if (attr.localName == "name") {
                            info.name = attr.nodeValue
                        } else if (attr.localName == "theme") {
                            info.theme = attr.nodeValue.replace("@id/0x", "").toInt(16)
                        }
                    }

                    return info
                }
            }
        }

        // Some apps (e.g. Duolingo, for seasonal/streak icon variants) launch through an
        // <activity-alias> rather than a plain <activity> - the alias itself has no backing
        // class, just a targetActivity pointing at a real, separately-declared <activity>.
        // Only one alias is normally android:enabled="true" at a time.
        val aliases = document.getElementsByTagName("activity-alias")

        for (i in 0 until aliases.length) {
            val node = aliases.item(i)
            val enabled = node.attributes.getNamedItem("android:enabled")?.nodeValue != "false"
            if (!enabled) continue

            val aliasFilters = getChildrenByTagName(node, "intent-filter")
            val hasMainAction = aliasFilters.any { filter ->
                getChildrenByTagName(filter, "action").any {
                    it.attributes.getNamedItem("android:name").nodeValue == "android.intent.action.MAIN"
                }
            }
            if (!hasMainAction) continue

            val targetActivity = node.attributes.getNamedItem("android:targetActivity")?.nodeValue
                ?: continue
            return parseActivities().find { it.first.name == targetActivity }?.first
        }

        return null
    }

    fun getApplicationInfo(): ApplicationInfo {
        if (applicationInfo != null) {
            return applicationInfo!!
        }

        val aInfo = ApplicationInfo()
        aInfo.nativeLibraryDir =
            baseDir.list { file, path -> file.isDirectory && file.name == "libs" }?.firstOrNull()

        val manifestNode = document.getElementsByTagName("manifest").item(0)
        for (i in 0 until manifestNode.attributes.length) {
            val attr = manifestNode.attributes.item(i)
            if (attr.nodeName == "package") {
                aInfo.packageName = attr.nodeValue
            }
        }

        val node = document.getElementsByTagName("application")
            .item(0)

        for (j in 0 until node.attributes.length) {
            val attr = node.attributes.item(j)
            if (attr.localName == "name") {
                aInfo.name = attr.nodeValue
            } else if (attr.localName == "theme") {
                aInfo.theme = attr.nodeValue.replace("@id/0x", "").toInt(16)
            }
        }

        aInfo.metaData = parseMetaData(node)

        // <uses-sdk>: targetSdkVersion decides platform behaviors the proxy host has to mimic (see
        // DCLActivityProxyPool.appHandledConfigChanges). Absent means "minSdk", i.e. a legacy app.
        document.getElementsByTagName("uses-sdk").item(0)?.let { sdk ->
            fun sdkAttr(name: String) = sdk.attributes.getNamedItem("android:$name")?.nodeValue?.let { parseNumber(it) }
            aInfo.minSdkVersion = sdkAttr("minSdkVersion") ?: aInfo.minSdkVersion
            aInfo.targetSdkVersion = sdkAttr("targetSdkVersion") ?: aInfo.minSdkVersion
        }

        // Cache
        applicationInfo = aInfo
        return aInfo
    }

    /**
     * The window/task attributes a host proxy activity has to imitate (see DCLActivityProxyPool
     * and DCLActivity.applyActivityAttributes). The binary-XML parser renders enum/flag
     * attributes as a number (decimal or `0x` hex) and sometimes as the symbolic name(s).
     */
    private fun applyActivityAttribute(info: ActivityInfo, name: String, value: String) {
        when (name) {
            "launchMode" -> info.launchMode = parseEnum(value, LAUNCH_MODES) ?: info.launchMode
            "configChanges" -> info.configChanges = parseFlags(value, CONFIG_CHANGES) ?: info.configChanges
            "screenOrientation" -> info.screenOrientation = parseEnum(value, SCREEN_ORIENTATIONS) ?: info.screenOrientation
            "windowSoftInputMode" -> info.softInputMode = parseFlags(value, SOFT_INPUT_MODES) ?: info.softInputMode
            "excludeFromRecents" ->
                if (parseBool(value)) info.flags = info.flags or ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS
            "taskAffinity" -> info.taskAffinity = value
            "exported" -> info.exported = parseBool(value)
            "enabled" -> info.enabled = value != "false"
        }
    }

    /** `true`, but also the integer forms a binary manifest can carry (`0xffffffff`, `-1`, `1`). */
    private fun parseBool(value: String): Boolean =
        value.toBoolean() || (parseNumber(value)?.let { it != 0 } ?: false)

    private fun parseNumber(value: String): Int? =
        if (value.startsWith("0x")) value.substring(2).toLongOrNull(16)?.toInt() else value.toIntOrNull()

    private fun parseEnum(value: String, names: Map<String, Int>): Int? = parseNumber(value) ?: names[value]

    /** A `|`-separated flag set, as either one number or symbolic names. */
    private fun parseFlags(value: String, names: Map<String, Int>): Int? {
        parseNumber(value)?.let { return it }
        var result = 0
        for (part in value.split('|').map { it.trim() }.filter { it.isNotEmpty() }) {
            result = result or (parseNumber(part) ?: names[part] ?: return null)
        }
        return result
    }

    /** Parses a component node's <meta-data> children into a Bundle, resolving @id/ references. */
    private fun parseMetaData(node: Node): Bundle {
        val metaData = bundleOf()
        val metaDataNodes = getChildrenByTagName(node, "meta-data")
        for (mdNode in metaDataNodes) {
            // TODO This is probably a bad way to do things
            val nodeName = mdNode.attributes.getNamedItem("android:name").nodeValue
            val nodeValue = (mdNode.attributes.getNamedItem("android:value") ?: mdNode.attributes.getNamedItem("android:resource")).nodeValue
            if (nodeValue.startsWith("@id/")) {
                // android:resource
                try {
                    val resId = nodeValue.replace("@id/0x", "").toInt(16)
                    val resolved: Any = when (val typeName = resources.getResourceTypeName(resId)) {
                        "string" -> resources.getString(resId)
                        "style" -> resId // TODO Make sure
                        "color" -> resources.getColor(resId)
                        "integer" -> resources.getInteger(resId)
                        "xml" -> resources.getXml(resId)
                        "bool" -> resources.getBoolean(resId)
                        "drawable" -> resId // TODO Make sure
                        "interpolator" -> resId // TODO Make sure
                        "array" -> resId // TODO Make sure
                        "raw" -> resId // TODO Make sure
                        else -> throw IllegalStateException("Unknown typename $typeName")
                    }

                    when (resolved) {
                        is String -> metaData.putString(nodeName, resolved)
                        is Int -> metaData.putInt(nodeName, resolved)
                        is Boolean -> metaData.putBoolean(nodeName, resolved)
                        is XmlResourceParser -> metaData.putString(nodeName, resolved.text)
                    }
                } catch (ex: NotFoundException) {
                    // Left blank intentionally
                    throw ex
                }
            } else if (nodeValue.isDigitsOnly() && nodeValue != "") {
                val valueInt = nodeValue.toIntOrNull() ?: nodeValue.toLong()
                if (valueInt.toLong() > Int.MAX_VALUE) {
                    metaData.putLong(nodeName, valueInt.toLong())
                } else {
                    metaData.putInt(nodeName, valueInt.toInt())
                }
            } else if (nodeValue.toFloatOrNull() != null) {
                metaData.putFloat(nodeName, nodeValue.toFloat())
            } else if (nodeValue == "true" || nodeValue == "false") {
                metaData.putBoolean(nodeName, nodeValue.toBooleanStrict())
            } else {
                metaData.putString(nodeName, nodeValue)
            }
        }
        return metaData
    }

    fun getProviders(): List<ProviderInfo> {
        if (providers != null) {
            return providers!!
        }

        val result = mutableListOf<ProviderInfo>()
        val appInfo = getApplicationInfo()

        val appNode = document.getElementsByTagName("application")
            .item(0)

        val providers = getChildrenByTagName(appNode, "provider")

        for (node in providers) {
            val info = ProviderInfo()
            info.applicationInfo = appInfo

            for (j in 0 until node.attributes.length) {
                val attr = node.attributes.item(j)
                if (attr.localName == "name") {
                    info.name = attr.nodeValue
                } else if (attr.localName == "grantUriPermissions") {
                    info.grantUriPermissions = attr.nodeValue.toBoolean()
                } else if (attr.localName == "authorities") {
                    info.authority = attr.nodeValue
                }
            }

            result.add(info)
        }

        this.providers = result.toList()
        return result.toList()
    }

    fun getServices(): List<ServiceInfo> {
        if (services != null) {
            return services!!
        }

        val result = mutableListOf<ServiceInfo>()
        val appInfo = getApplicationInfo()

        val appNode = document.getElementsByTagName("application")
            .item(0)

        val providers = getChildrenByTagName(appNode, "service")

        for (node in providers) {
            val info = ServiceInfo()
            info.applicationInfo = appInfo

            for (j in 0 until node.attributes.length) {
                val attr = node.attributes.item(j)
                if (attr.localName == "name") {
                    info.name = attr.nodeValue
                } else if (attr.localName == "exported") {
                    info.exported = attr.nodeValue.toBoolean()
                }
            }

            info.metaData = parseMetaData(node)

            result.add(info)
        }

        this.services = result.toList()
        return result.toList()
    }
}