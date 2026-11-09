package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * In-process instrumentation seam for an isolated encrypted Skills library.
 * Never accepts intents or shell inputs. Release APKs always use the real store.
 * A process restart automatically drops any test override.
 */
internal object SkillLabIsolatedTestStore {
    @Volatile private var isolated: AndroidConversationPreparationStore? = null

    fun forContext(context: Context): AndroidConversationPreparationStore =
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            isolated ?: AndroidConversationPreparationStore(context)
        else AndroidConversationPreparationStore(context)

    fun setForDebugTest(context: Context, store: AndroidConversationPreparationStore?) {
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "isolated_test_store_requires_debuggable_app"
        }
        isolated = store
    }
}
