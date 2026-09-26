# Verso n'active pas R8 en V1 (isMinifyEnabled = false). Fichier conservé pour la configuration release.

# Verso lit par réflexion le PagerState interne du navigateur Readium (reader/ReaderSurface.kt, pagerStateOf).
-keepclassmembers class org.readium.navigator.web.reflowable.ReflowableWebRenditionState {
    androidx.compose.foundation.pager.PagerState getPagerState*();
}
