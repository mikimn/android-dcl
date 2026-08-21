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

        // Cache
        applicationInfo = aInfo
        return aInfo
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