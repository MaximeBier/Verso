package com.maximebier.verso.reader

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.shared.publication.Locator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Tour de page du mode pages : la page tourne autour de la reliure, posée sur des photos des pages, au-dessus du
 * lecteur Readium (non modifié). Le shader demande Android 13 ; avant, ou avec « Supprimer les animations », le tour
 * de page reste celui de Readium.
 */

/** Pliage disponible : shader (Android 13) et animations permises. */
fun pageCurlAvailable(reducedMotion: Boolean): Boolean = Build.VERSION.SDK_INT >= 33 && !reducedMotion

object PageTurnState {
    /** Un tour de page est à l’écran : le vrai pied de page se cache (il est dessiné sur les pages qui tournent). */
    var turning by mutableStateOf(false)

    /**
     * Fin (horloge `System.nanoTime`) de la fenêtre où un défilement signalé vient d’une photo de page voisine : la
     * WebView défile le temps du dessin, et l’écouteur de défilement n’est prévenu qu’à l’image suivante.
     */
    var ignoreScrollUntilNanos = 0L

    fun capturingScroll(): Boolean = System.nanoTime() < ignoreScrollUntilNanos
}

/**
 * Un tour de page. `curled` : la page qui se soulève (l’affichée en avant, la précédente en arrière) ; `under` : la
 * page dessous. [grab] : point du bord droit de `curled` tenu (à la hauteur de l’appui) ; [edge] : où il se trouve.
 * À plat : `edge == grab`. Tournée : `edge` est le reflet de `grab` de l’autre côté de la reliure.
 */
class PageTurnAnim(
    val forward: Boolean,
    curled: Bitmap?,
    under: Bitmap?,
    val grab: Offset,
    /** Point d’appui du doigt. */
    val touch: Offset,
    /** Page tournée sous la photo dès le début (bord de chapitre : page voisine inconnue d’avance). */
    val turnedEarly: Boolean,
) {
    var curled by mutableStateOf(curled)
    var under by mutableStateOf(under)
    val turned = Offset(-grab.x, grab.y)
    /** Position horizontale du bord ; fin de ressort courte (au pixel près). */
    val edgeX = Animatable(if (forward) grab.x else turned.x, visibilityThreshold = 3f)
    /** Écart vertical du doigt ; il ne compte qu’avec le chemin horizontal (aucun coin replié près de « à plat »). */
    var tilt by mutableFloatStateOf(0f)

    /** Bord de la page : à plat, le point tenu ; l’inclinaison s’efface quand la page revient à plat. */
    val edge: Offset
        get() {
            val x = edgeX.value
            val ramp = ((grab.x - x) / (TILT_RAMP * grab.x)).coerceIn(0f, 1f)
            // Au carré : l’inclinaison s’efface bien avant que la page soit à plat (pas de coin qui traîne).
            return Offset(x, grab.y + tilt * ramp * ramp)
        }
    var releasing = false
    /** Bord de chapitre : le tour anticipé a bien eu lieu (faux au bout du livre). */
    var earlyTurnDone = false
    var job: Job? = null

    /**
     * Position du bord pour un doigt en [finger]. En avant, le bord suit le doigt depuis le bord droit, sans jamais
     * dépasser sa place à plat. En arrière, la page part tournée (cachée à gauche) et revient deux fois plus vite que
     * le doigt : le pli entre par la gauche et suit le doigt. L’inclinaison verticale ne vient qu’avec le chemin
     * horizontal.
     */
    fun edgeXFor(finger: Offset): Float {
        val delta = finger - touch
        return if (forward) grab.x + min(delta.x, 0f) else turned.x + (2f * delta.x).coerceIn(0f, 2f * grab.x)
    }

    /** 0 : page à plat ; 1 : page tournée. */
    fun turnedFraction(): Float = ((grab.x - edgeX.value) / (2f * grab.x)).coerceIn(0f, 1f)

    companion object {
        const val TILT_RAMP = 0.15f
    }
}

/**
 * Pilote : photos d’avance des pages voisines ([prefetch]), geste suivi du doigt, fin du geste qui garde l’élan. La
 * vraie page tourne au lâcher, sous l’animation ; un nouveau toucher termine l’animation en cours (pages enchaînées).
 */
class PageTurnDriver(
    private val scope: CoroutineScope,
    /** Photo de l’écran (avec le pied de page), rendue de façon asynchrone ; null si indisponible. */
    private val captureScreen: (onResult: (Bitmap?) -> Unit) -> Unit,
    /**
     * Page affichée + `pages`, dessinée depuis la WebView sans la bouger, avec son pied de page (chapitre repris de
     * `screen`) ; null hors du fichier affiché.
     */
    private val captureWeb: (pages: Int, screen: Bitmap?) -> Bitmap?,
    private val turn: (forward: Boolean) -> Unit,
    private val displayed: StateFlow<Locator?>,
) {
    private var animState = mutableStateOf<PageTurnAnim?>(null)
    var anim: PageTurnAnim?
        get() = animState.value
        private set(value) {
            animState.value = value
            PageTurnState.turning = value != null
        }

    private var cacheKey: Locator? = null
    private var cachedNext: Bitmap? = null
    private var cachedPrev: Bitmap? = null
    private var screenShot: Bitmap? = null
    private var screenShotKey: Locator? = null
    private var endJob: Job? = null
    var width = 0f

    /** Photos d’avance : l’écran, puis la page suivante et la précédente (au repos, après chaque page). */
    fun prefetch() {
        if (anim != null) return
        val key = displayed.value ?: return
        captureScreen { shot ->
            if (anim != null || displayed.value != key) return@captureScreen
            if (shot != null) {
                screenShot = shot
                screenShotKey = key
            }
            cachedNext = captureWeb(1, shot)
            cachedPrev = captureWeb(-1, shot)
            cacheKey = key
        }
    }

    /**
     * Doigt posé : un tour lâché en cours d’animation se termine aussitôt (la page a déjà tourné dessous), pour
     * enchaîner les pages ; sinon, photo fraîche de l’écran (surlignages).
     */
    fun onPointerDown() {
        val a = anim
        if (a != null && a.releasing && a.job?.isActive != true) {
            endJob?.cancel()
            finish(a)
        }
        // Tour jamais lâché (geste interrompu sans lâcher) : la page revient à plat.
        if (a != null && !a.releasing) release(Offset.Zero)
        if (anim == null) {
            val key = displayed.value
            captureScreen { shot ->
                if (shot != null && anim == null && displayed.value == key) {
                    screenShot = shot
                    screenShotKey = key
                }
            }
        }
    }

    private fun finish(a: PageTurnAnim) {
        if (anim === a) anim = null
        invalidate()
    }

    /** Photos d’avance périmées (page tournée, mise en page ou thème changés). */
    fun invalidate() {
        cacheKey = null
        screenShot = null
    }

    /** Lecteur quitté (sortie, rotation) : plus d’animation ni de pied de page caché. */
    fun reset() {
        endJob?.cancel()
        anim?.job?.cancel()
        anim = null
        invalidate()
    }

    /** 1 : animation lancée ; 0 : geste laissé au lecteur ; -1 : geste avalé (tour précédent pas fini). */
    fun start(forward: Boolean, touch: Offset): Int {
        if (anim != null) return -1
        val key = displayed.value ?: return 0
        val shot = screenShot?.takeIf { screenShotKey == key }
        // Page affichée dessinée depuis la WebView (fraîche : surlignages) ; la photo de l’écran ne sert qu’au chapitre
        // du pied de page (elle contient aussi le voile qu’Android pose sous la barre d’état).
        val current = captureWeb(0, shot) ?: shot ?: return 0
        val neighbor = if (cacheKey == key) (if (forward) cachedNext else cachedPrev) else captureWeb(if (forward) 1 else -1, shot)
        val early = neighbor == null
        val grab = Offset(width, touch.y)
        val a = if (forward) {
            PageTurnAnim(true, curled = current, under = neighbor, grab = grab, touch = touch, turnedEarly = early)
        } else {
            PageTurnAnim(false, curled = neighbor, under = current, grab = grab, touch = touch, turnedEarly = early)
        }
        anim = a
        if (early) {
            a.job = scope.launch {
                val before = displayed.value
                turn(forward)
                withTimeoutOrNull(1500) { displayed.first { it != before } } ?: return@launch
                a.earlyTurnDone = true
                delay(RENDER_DELAY_MS)
                val page = captureWeb(0, null)
                if (forward) a.under = page else a.curled = page
            }
        }
        return 1
    }

    fun drag(finger: Offset) {
        val a = anim ?: return
        if (a.releasing) return
        val x = a.edgeXFor(finger)
        a.tilt = (finger.y - a.touch.y).coerceIn(-MAX_TILT * width, MAX_TILT * width)
        scope.launch { a.edgeX.snapTo(x) }
    }

    fun release(velocity: Offset) {
        val a = anim ?: return
        if (a.releasing) return
        a.releasing = true
        endJob = scope.launch {
            a.job?.join()
            // Vitesse du bord : celle du doigt, doublée en arrière (le bord va deux fois plus vite que le doigt).
            val edgeVelocity = if (a.forward) velocity.x else velocity.x * 2f
            // Chemin parcouru vers l’autre état (vers « tournée » en avant, vers « à plat » en arrière).
            val travelled = if (a.forward) a.turnedFraction() else 1f - a.turnedFraction()
            // Bout du livre : rien n’a tourné, la page revient.
            val commit = shouldCommit(a.forward, travelled, velocity.x) && !(a.turnedEarly && !a.earlyTurnDone)
            // La vraie page tourne tout de suite, sous l’animation (rien à faire si elle a déjà tourné).
            when {
                commit && !a.turnedEarly -> turn(a.forward)
                !commit && a.earlyTurnDone -> turn(!a.forward)
            }
            val target = if (a.forward == commit) a.turned.x else a.grab.x
            a.edgeX.animateTo(
                target,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = STIFFNESS),
                initialVelocity = edgeVelocity,
            )
            finish(a)
        }
    }

    companion object {
        const val RENDER_DELAY_MS = 50L
        /** Part du chemin à parcourir pour valider le tour sans élan. */
        const val COMMIT_FRACTION = 0.12f
        const val FLING_PX_S = 600f
        const val STIFFNESS = 900f
        /** Écart vertical retenu, en part de la largeur. */
        const val MAX_TILT = 0.45f

        /**
         * Au lâcher : un élan dans le sens du tour le valide, un élan contraire l’annule ; sans élan, il faut avoir
         * parcouru [COMMIT_FRACTION] du chemin (`travelled`, de 0 à 1).
         */
        fun shouldCommit(forward: Boolean, travelled: Float, velocityX: Float): Boolean {
            val fling = if (forward) -velocityX else velocityX
            return when {
                fling > FLING_PX_S -> true
                fling < -FLING_PX_S -> false
                else -> travelled > COMMIT_FRACTION
            }
        }
    }
}

/** Géométrie d’un instant : axe du cylindre (point, normale vers la partie soulevée) et rayon. */
private class CurlGeometry(val ax: Float, val ay: Float, val nx: Float, val ny: Float, val r: Float, val flat: Boolean)

/**
 * La page est tenue à la reliure (bord gauche) : le point tenu ne s’éloigne jamais des deux bouts de la reliure plus
 * que sur la page à plat. Le pli est la médiatrice entre le point tenu et le bord, enroulé sur un cylindre.
 */
private fun curlGeometry(grab: Offset, edge: Offset, w: Float, h: Float): CurlGeometry {
    var f = edge
    repeat(2) {
        for (spine in arrayOf(Offset(0f, 0f), Offset(0f, h))) {
            val max = (grab - spine).getDistance()
            val d = (f - spine).getDistance()
            if (d > max && d > 0f) f = spine + (f - spine) * (max / d)
        }
    }
    // Près d’un coin, le coin se soulève un peu vers le milieu en cours de route.
    val t = ((grab.y - h / 2) / (h / 2)).coerceIn(-1f, 1f)
    val k = t * abs(t)
    val progress = ((grab.x - f.x) / (2 * grab.x.coerceAtLeast(1f))).coerceIn(0f, 1f)
    f = Offset(f.x, f.y - k * h * 0.10f * sin(PI * progress).toFloat())
    val dx = grab.x - f.x
    val dy = grab.y - f.y
    val d = sqrt(dx * dx + dy * dy)
    if (d < 0.5f) return CurlGeometry(0f, 0f, 1f, 0f, 0f, flat = true)
    val nx = dx / d
    val ny = dy / d
    val r = min(w * 0.10f, d / PI.toFloat())
    val s0 = (d + PI.toFloat() * r) / 2
    return CurlGeometry(grab.x - nx * s0, grab.y - ny * s0, nx, ny, r, flat = false)
}

@Composable
fun PageTurnOverlay(anim: PageTurnAnim?, background: Int, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < 33) return
    // Gardé entre deux tours : le shader n’est compilé qu’une fois.
    val renderer = remember { PageCurlRenderer() }
    if (anim == null) return
    val edge = anim.edge
    val curled = anim.curled
    val under = anim.under
    Canvas(modifier) {
        drawIntoCanvas { c ->
            val g = curlGeometry(anim.grab, edge, size.width, size.height)
            renderer.draw(c.nativeCanvas, size.width, size.height, curled, under, g, background, density)
        }
    }
}

@RequiresApi(33)
private class PageCurlRenderer {
    private val shader = RuntimeShader(CURL_SHADER)
    private val paint = Paint()
    private var curledSource: Bitmap? = null
    private var underSource: Bitmap? = null
    private var blankColor = 0
    private var blank: Bitmap? = null

    private fun blank(bg: Int): Bitmap {
        val b = blank
        if (b != null && blankColor == bg) return b
        return createBitmap(4, 4).also {
            it.eraseColor(bg)
            blank = it
            blankColor = bg
        }
    }

    fun draw(c: android.graphics.Canvas, w: Float, h: Float, curled: Bitmap?, under: Bitmap?, g: CurlGeometry, bg: Int, density: Float) {
        val curledBmp = curled ?: blank(bg)
        val underBmp = under ?: blank(bg)
        if (curledBmp !== curledSource) {
            shader.setInputShader("curled", BitmapShader(curledBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            curledSource = curledBmp
        }
        if (underBmp !== underSource) {
            shader.setInputShader("under", BitmapShader(underBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            underSource = underBmp
        }
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("curledSize", curledBmp.width.toFloat(), curledBmp.height.toFloat())
        shader.setFloatUniform("underSize", underBmp.width.toFloat(), underBmp.height.toFloat())
        shader.setFloatUniform("axisPoint", g.ax, g.ay)
        shader.setFloatUniform("normal", g.nx, g.ny)
        shader.setFloatUniform("radius", g.r)
        shader.setFloatUniform("isFlat", if (g.flat) 1f else 0f)
        shader.setFloatUniform("shadowLen", 36f * density)
        val dark = android.graphics.Color.luminance(bg) < 0.5f
        shader.setFloatUniform(
            "paper",
            android.graphics.Color.red(bg) / 255f, android.graphics.Color.green(bg) / 255f, android.graphics.Color.blue(bg) / 255f,
        )
        shader.setFloatUniform("backLight", if (dark) 0.07f else -0.04f)
        paint.shader = shader
        c.drawRect(0f, 0f, w, h, paint)
    }
}

/**
 * Pour chaque pixel : distance s à l’axe le long de la normale. Couches, de bas en haut : page du dessous ombrée,
 * devant de la page (à plat, puis sur le cylindre), dos de la page (sur le haut du cylindre, puis à plat au-dessus).
 * Chaque couche est fondue sur ses bords (anticrénelage) ; le dos est la page en miroir pâlie au papier. L’ombre sur
 * la page du dessous part du vrai bord de la partie enroulée, ligne par ligne (le long de l’axe).
 */
private const val CURL_SHADER = """
uniform shader curled;
uniform shader under;
uniform float2 size;
uniform float2 curledSize;
uniform float2 underSize;
uniform float2 axisPoint;
uniform float2 normal;
uniform float radius;
uniform float isFlat;
uniform float shadowLen;
uniform float3 paper;
uniform float backLight;

const float PI = 3.14159265;
const float BIG = 1.0e6;

float inside(float2 p) {
    return min(min(p.x, size.x - p.x), min(p.y, size.y - p.y));
}

float coverage(float2 p) {
    return clamp(inside(p) + 0.5, 0.0, 1.0);
}

half4 pageAt(float2 p) {
    return curled.eval(p * curledSize / size);
}

// Intervalle [entrée, sortie] de la demi-droite o + n·u (u ≥ 0) dans la page ; sortie < entrée si vide.
float2 rayInPage(float2 o, float2 n) {
    float2 lo = float2(0.0);
    float2 hi = float2(BIG);
    if (abs(n.x) > 1.0e-5) {
        float a = (0.0 - o.x) / n.x;
        float b = (size.x - o.x) / n.x;
        lo.x = min(a, b);
        hi.x = max(a, b);
    } else if (o.x < 0.0 || o.x > size.x) {
        return float2(1.0, -1.0);
    } else {
        lo.x = -BIG;
    }
    if (abs(n.y) > 1.0e-5) {
        float a = (0.0 - o.y) / n.y;
        float b = (size.y - o.y) / n.y;
        lo.y = min(a, b);
        hi.y = max(a, b);
    } else if (o.y < 0.0 || o.y > size.y) {
        return float2(1.0, -1.0);
    } else {
        lo.y = -BIG;
    }
    return float2(max(max(lo.x, lo.y), 0.0), min(hi.x, hi.y));
}

half4 main(float2 p) {
    if (isFlat > 0.5) {
        return pageAt(p);
    }
    float r = max(radius, 0.001);
    float s = dot(p - axisPoint, normal);

    // Page du dessous, ombrée par la partie enroulée : sur cette ligne, son bord extérieur est à r·sin(u/r), u étant
    // la plus grande distance à l’axe atteinte par la page (au plus le haut du cylindre).
    half3 color = under.eval(p * underSize / size).rgb;
    if (s > 0.0) {
        float2 span = rayInPage(p - normal * s, normal);
        // Sur cette ligne, la page n’occupe que [entrée, sortie] le long de la normale ; seule sa part enroulée
        // (au plus πr) dépasse l’axe à l’écran, jusqu’au point le plus proche du haut du cylindre.
        if (span.y > span.x && span.x < PI * r) {
            float u = clamp(PI * r * 0.5, span.x, min(span.y, PI * r));
            float outer = r * sin(u / r);
            if (s > outer) {
                float k = clamp(1.0 - (s - outer) / shadowLen, 0.0, 1.0);
                color *= 1.0 - 0.42 * k * k;
            }
        }
    }

    if (s <= 0.0) {
        // Devant à plat.
        color = pageAt(p).rgb;
    } else if (s < r) {
        // Devant sur le cylindre (visible là où le dos ne le couvre pas).
        float th = asin(clamp(s / r, 0.0, 1.0));
        float2 src = p + normal * (r * th - s);
        // Recto de la partie qui se soulève (visible là où le dos ne la couvre pas) : plus sombre à mesure qu’il se redresse.
        color = mix(color, pageAt(src).rgb * (0.8 + 0.2 * cos(th)), coverage(src));
    }

    // Dos, au-dessus de tout.
    if (s < r) {
        float sSrc;
        float light = 1.0;
        if (s <= 0.0) {
            sSrc = PI * r - s;
        } else {
            float th = PI - asin(clamp(s / r, 0.0, 1.0));
            sSrc = r * th;
            light = 0.6 + 0.4 * abs(cos(th));
        }
        float2 src = p + normal * (sSrc - s);
        // Ombre de contact du dos sur ce qu’il recouvre, le long de son bord : sur la page à plat comme sur la partie
        // qui se soulève (sinon elle s’arrête net au pli).
        float m = inside(src);
        if (m < 0.0) {
            float k = clamp(1.0 + m / (shadowLen * 0.6), 0.0, 1.0);
            color *= 1.0 - 0.22 * k * k;
        }
        float cov = coverage(src);
        if (cov > 0.0) {
            half3 back = mix(pageAt(src).rgb, half3(paper), 0.78) + backLight;
            color = mix(color, back * light, cov);
        }
    }
    return half4(color, 1.0);
}
"""
