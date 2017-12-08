package eu.kanade.tachiyomi

import timber.log.Timber

class TachiyomiDebugTree : Timber.DebugTree() {

    override fun createStackElementTag(element: StackTraceElement): String? {
        return String.format("[%s.%s:%s]",
                super.createStackElementTag(element), element.methodName, element.lineNumber);
    }
}