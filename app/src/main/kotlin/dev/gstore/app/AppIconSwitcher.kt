package dev.gstore.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.gstore.datastore.AppIconStyle

object AppIconSwitcher {
    fun set(context: Context, style: AppIconStyle) {
        val packageManager = context.packageManager
        val aliases = listOf(
            AppIconStyle.CLASSIC to ".IconClassic",
            AppIconStyle.RED_LIGHT to ".IconRedLight",
            AppIconStyle.RED_DARK to ".IconRedDark",
        )
        val settings = aliases.map { (icon, alias) ->
            Triple(
                ComponentName(context.packageName, context.packageName + alias),
                if (icon == style) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.setComponentEnabledSettings(
                settings.map { (component, enabledState, flags) ->
                    PackageManager.ComponentEnabledSetting(component, enabledState, flags)
                },
            )
        } else {
            settings.sortedByDescending { it.second == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
                .forEach { (component, enabledState, flags) ->
                    packageManager.setComponentEnabledSetting(
                        component,
                        enabledState,
                        flags,
                    )
                }
        }
    }
}
