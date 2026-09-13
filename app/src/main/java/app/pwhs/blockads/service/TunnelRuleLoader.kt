package app.pwhs.blockads.service

import android.content.Context
import app.pwhs.blockads.ui.browser.rules.BrowserRuleStorage
import timber.log.Timber

/**
 * Loads cosmetic rules, ad path patterns, and scriptlets for the Go tunnel engine
 * directly from browser_rules.json (via BrowserRuleStorage).
 */
object TunnelRuleLoader {

    /**
     * Loads cosmetic CSS from the active browser rule package (adblock_cosmetic.css / remote updates).
     */
    fun loadCosmeticCss(context: Context): String {
        return try {
            val storage = BrowserRuleStorage(context)
            val pkg = storage.getActivePackage()
            pkg.cosmeticCss?.takeIf { it.isNotBlank() } ?: ""
        } catch (e: Exception) {
            Timber.w(e, "Failed to read browser cosmetic CSS from storage")
            ""
        }
    }

    /**
     * Loads ad path patterns from the active browser rule package.
     */
    fun loadAdPathPatterns(context: Context): String {
        return try {
            val storage = BrowserRuleStorage(context)
            val pkg = storage.getActivePackage()
            pkg.adPathPatterns.joinToString("\n")
        } catch (e: Exception) {
            Timber.w(e, "Failed to load ad path patterns from storage")
            ""
        }
    }

    /**
     * Loads scriptlets JS from the active browser rule package (adguard_scriptlets.js / remote updates).
     */
    fun loadScriptletsJs(context: Context): String {
        return try {
            val storage = BrowserRuleStorage(context)
            val pkg = storage.getActivePackage()
            pkg.scriptletsJs?.takeIf { it.isNotBlank() } ?: ""
        } catch (e: Exception) {
            Timber.w(e, "Failed to load scriptlets JS from storage")
            ""
        }
    }
}
