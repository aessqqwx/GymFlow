package com.aess.gymflow

import android.app.Application
import android.content.res.Resources
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import java.util.Locale

class GymFlowApplication : Application() {
    companion object {
        lateinit var instance: GymFlowApplication
            private set
    }

    private val localizedResources = mutableMapOf<String, Resources>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    fun resourcesFor(language: String): Resources = synchronized(localizedResources) {
        localizedResources.getOrPut(if (language == "EN") "EN" else "RU") {
            val configuration = resources.configuration.apply { }
            val copy = android.content.res.Configuration(configuration)
            copy.setLocale(if (language == "EN") Locale.ENGLISH else Locale.forLanguageTag("ru"))
            createConfigurationContext(copy).resources
        }
    }
}

fun gs(language: String, @StringRes id: Int, vararg args: Any): String {
    val resources = GymFlowApplication.instance.resourcesFor(language)
    return if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
}

fun gsa(language: String, @ArrayRes id: Int): Array<String> = GymFlowApplication.instance.resourcesFor(language).getStringArray(id)
