# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep the Glance widget implementation intact in release builds. The widget is
# rendered from app-widget/broadcast entrypoints and Glance-generated RemoteViews,
# so several generated composable/coroutine classes do not look reachable to R8
# from this app's normal call graph. If those are renamed or removed, release
# widgets can stay stuck on the static initialLayout preview.
-keep class dev.jsjh.timebox.widget.** { *; }

# Glance widgets invoke ActionCallback subclasses via Class.forName() using a
# class name stored in the RemoteViews intent. R8 has no static call site to
# see, so without this rule it can strip/rename them and the widget silently
# fails to render past its initialLayout in release builds.
-keep class * extends androidx.glance.appwidget.action.ActionCallback {
    <init>();
}

# GlanceAppWidget.sizeMode and GlanceAppWidgetReceiver.glanceAppWidget are
# read polymorphically by the Glance rendering engine, not from a call site
# inside this app's own code. Confirmed via app/build/outputs/mapping/release/usage.txt
# that R8 was stripping these overrides as "unused", which breaks widget
# rendering entirely in release builds (stuck on the static initialLayout).
-keep class * extends androidx.glance.appwidget.GlanceAppWidget {
    public androidx.glance.appwidget.SizeMode getSizeMode();
}
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver {
    public androidx.glance.appwidget.GlanceAppWidget getGlanceAppWidget();
}

# WorkManager stores the default InputMerger class name in its database and
# instantiates it reflectively. R8 kept the class name but stripped the no-arg
# constructor in release, which made Glance widget work finish immediately with
# "Could not create Input Merger androidx.work.OverwritingInputMerger".
-keep class * extends androidx.work.InputMerger {
    public <init>();
}
