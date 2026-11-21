package com.docuscanpro.app.imaging

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Détection automatique des bords d'un document (feuille claire sur fond plus sombre).
 * Seuil d'Otsu → plus grande zone claire connexe → 4 coins extrêmes.
 * Renvoie 8 valeurs normalisées (HG, HD, BD, BG) ou null si rien de fiable.
 */
object DocDetector {

    /** Détection combinée : bords rectilignes (Hough) d'abord, zone claire en secours. */
    fun detect(src: Bitmap): FloatArray? = runCatching { detectLines(src) }.getOrNull() ?: detectRegion(src)

    private class Line(val theta: Double, val rho: Double)

    /**
     * Méthode principale, robuste même sur fond clair : carte de « blancheur » (luminance − chroma),
     * gradients, transformée de Hough, puis choix du quadrilatère dont les 4 côtés ont le meilleur
     * contraste intérieur/extérieur et le plus fort appui sur les bords.
     */
    fun detectLines(src: Bitmap): FloatArray? {
        val scale = 400f / max(src.width, src.height)
        val w = max(16, (src.width * scale).roundToInt())
        val h = max(16, (src.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small != src) small.recycle()

        var s = FloatArray(w * h) { i ->
            val c = px[i]
            val r = (c shr 16 and 255).toFloat(); val g = (c shr 8 and 255).toFloat(); val b = (c and 255).toFloat()
            val l = r * 0.3f + g * 0.59f + b * 0.11f
            l - 1.5f * (maxOf(r, g, b) - minOf(r, g, b))
        }
        repeat(3) {
            val o = FloatArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                var acc = 0f
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = (x + dx).coerceIn(0, w - 1); val yy = (y + dy).coerceIn(0, h - 1)
                    acc += s[yy * w + xx]
                }
                o[y * w + x] = acc / 9f
            }
            s = o
        }
        val mag = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val gx = s[y * w + (x + 1).coerceAtMost(w - 1)] - s[y * w + (x - 1).coerceAtLeast(0)]
            val gy = s[(y + 1).coerceAtMost(h - 1) * w + x] - s[(y - 1).coerceAtLeast(0) * w + x]
            mag[y * w + x] = kotlin.math.sqrt(gx * gx + gy * gy)
        }
        val sorted = mag.copyOf().also { it.sort() }
        val thr = sorted[(sorted.size * 0.90).toInt().coerceAtMost(sorted.size - 1)]
        if (thr <= 0f) return null

        // Transformée de Hough pondérée
        val nT = 180
        val diag = kotlin.math.hypot(w.toDouble(), h.toDouble()).toInt() + 1
        val acc = Array(nT) { FloatArray(2 * diag + 1) }
        val cosT = DoubleArray(nT) { kotlin.math.cos(Math.toRadians(it.toDouble())) }
        val sinT = DoubleArray(nT) { kotlin.math.sin(Math.toRadians(it.toDouble())) }
        for (y in 0 until h) for (x in 0 until w) {
            val m = mag[y * w + x]
            if (m <= thr) continue
            for (t in 0 until nT) {
                val r = (x * cosT[t] + y * sinT[t]).roundToInt() + diag
                acc[t][r] += m
            }
        }
        fun peaks(accept: (Int) -> Boolean, k: Int = 8): List<Line> {
            val a = Array(nT) { t -> if (accept(t)) acc[t].copyOf() else FloatArray(2 * diag + 1) }
            val out = mutableListOf<Line>()
            repeat(k) {
                var bt = -1; var br = -1; var bv = 0f
                for (t in 0 until nT) for (r in a[t].indices) if (a[t][r] > bv) { bv = a[t][r]; bt = t; br = r }
                if (bt < 0) return out
                out += Line(Math.toRadians(bt.toDouble()), (br - diag).toDouble())
                for (t in (bt - 6)..(bt + 6)) {
                    val tt = ((t % nT) + nT) % nT
                    for (r in (br - 8)..(br + 8)) if (r in a[tt].indices) a[tt][r] = 0f
                }
            }
            return out
        }
        val horiz = peaks({ it in 56..124 })
        val vert = peaks({ it < 35 || it > 145 })
        if (horiz.size < 2 || vert.size < 2) return null

        fun inter(a: Line, b: Line): DoubleArray? {
            val a1 = kotlin.math.cos(a.theta); val b1 = kotlin.math.sin(a.theta)
            val a2 = kotlin.math.cos(b.theta); val b2 = kotlin.math.sin(b.theta)
            val det = a1 * b2 - a2 * b1
            if (kotlin.math.abs(det) < 1e-6) return null
            return doubleArrayOf((a.rho * b2 - b.rho * b1) / det, (a1 * b.rho - a2 * a.rho) / det)
        }
        fun at(x: Int, y: Int, arr: FloatArray) = arr[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
        fun side(pa: DoubleArray, pb: DoubleArray, cx: Double, cy: Double): Double {
            val n = 24
            val dx = pb[0] - pa[0]; val dy = pb[1] - pa[1]
            val ln = kotlin.math.hypot(dx, dy).takeIf { it > 0 } ?: return -1e9
            var nx = -dy / ln; var ny = dx / ln
            if ((cx - pa[0]) * nx + (cy - pa[1]) * ny < 0) { nx = -nx; ny = -ny }
            var tot = 0.0; var cnt = 0; var edge = 0.0
            for (k in 1 until n) {
                val t = k.toDouble() / n
                val x = pa[0] + dx * t; val y = pa[1] + dy * t
                val xi = (x + nx * 4).toInt(); val yi = (y + ny * 4).toInt()
                val xo = (x - nx * 4).toInt(); val yo = (y - ny * 4).toInt()
                if (xi in 0 until w && yi in 0 until h && xo in 0 until w && yo in 0 until h) {
                    tot += s[yi * w + xi] - s[yo * w + xo]; cnt++
                }
                edge += at(x.toInt(), y.toInt(), mag)
            }
            if (cnt < n / 2) return -1e9
            return tot / cnt + 0.5 * edge / n
        }

        var best: Array<DoubleArray>? = null
        var bestS = Double.NEGATIVE_INFINITY
        val minArea = 0.12 * w * h
        for (i1 in horiz.indices) for (i2 in horiz.indices) {
            if (i1 == i2) continue
            for (j1 in vert.indices) for (j2 in vert.indices) {
                if (j1 == j2) continue
                val tl = inter(horiz[i1], vert[j1]) ?: continue
                val tr = inter(horiz[i1], vert[j2]) ?: continue
                val br = inter(horiz[i2], vert[j2]) ?: continue
                val bl = inter(horiz[i2], vert[j1]) ?: continue
                val pts = arrayOf(tl, tr, br, bl)
                if (pts.any { it[0] < -0.05 * w || it[0] > 1.05 * w || it[1] < -0.05 * h || it[1] > 1.05 * h }) continue
                if (tl[1] >= bl[1] || tr[1] >= br[1] || tl[0] >= tr[0] || bl[0] >= br[0]) continue
                var ar = 0.0
                for (k in 0 until 4) { val p = pts[k]; val q = pts[(k + 1) % 4]; ar += p[0] * q[1] - q[0] * p[1] }
                ar = kotlin.math.abs(ar) / 2
                if (ar < minArea) continue
                val cx = pts.sumOf { it[0] } / 4; val cy = pts.sumOf { it[1] } / 4
                var sc = 0.0
                for (k in 0 until 4) sc += side(pts[k], pts[(k + 1) % 4], cx, cy)
                if (sc <= 0) continue
                sc *= 0.6 + 0.4 * ar / (w.toDouble() * h)
                if (sc > bestS) { bestS = sc; best = pts }
            }
        }
        val b = best ?: return null
        return FloatArray(8) { i ->
            val p = b[i / 2]
            if (i % 2 == 0) (p[0] / w).toFloat().coerceIn(0f, 1f) else (p[1] / h).toFloat().coerceIn(0f, 1f)
        }
    }

    /** Méthode de secours : plus grande zone claire (seuil d'Otsu). */
    fun detectRegion(src: Bitmap): FloatArray? {
        val scale = 320f / max(src.width, src.height)
        val w = max(8, (src.width * scale).roundToInt())
        val h = max(8, (src.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small != src) small.recycle()

        // Niveaux de gris + flou 3x3
        val g = IntArray(w * h) { i -> val c = px[i]; ((c shr 16 and 255) * 77 + (c shr 8 and 255) * 150 + (c and 255) * 29) shr 8 }
        val b = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0; var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) { s += g[yy * w + xx]; n++ }
            }
            b[y * w + x] = s / n
        }

        // Seuil d'Otsu
        val hist = IntArray(256); b.forEach { hist[it]++ }
        val total = w * h
        var sum = 0.0; for (i in 0..255) sum += i * hist[i].toDouble()
        var sumB = 0.0; var wB = 0; var best = 0.0; var thr = 128
        for (t in 0..255) {
            wB += hist[t]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; thr = t }
        }

        // Plus grande composante claire (4-connexité)
        val label = IntArray(total)
        val queue = IntArray(total)
        var bestLabel = 0; var bestSize = 0; var cur = 0
        for (start in 0 until total) {
            if (b[start] <= thr || label[start] != 0) continue
            cur++
            var head = 0; var tail = 0
            queue[tail++] = start; label[start] = cur
            var size = 0
            while (head < tail) {
                val i = queue[head++]; size++
                val x = i % w; val y = i / w
                if (x > 0) { val j = i - 1; if (label[j] == 0 && b[j] > thr) { label[j] = cur; queue[tail++] = j } }
                if (x < w - 1) { val j = i + 1; if (label[j] == 0 && b[j] > thr) { label[j] = cur; queue[tail++] = j } }
                if (y > 0) { val j = i - w; if (label[j] == 0 && b[j] > thr) { label[j] = cur; queue[tail++] = j } }
                if (y < h - 1) { val j = i + w; if (label[j] == 0 && b[j] > thr) { label[j] = cur; queue[tail++] = j } }
            }
            if (size > bestSize) { bestSize = size; bestLabel = cur }
        }
        val ratio = bestSize.toFloat() / total
        if (bestLabel == 0 || ratio < 0.12f || ratio > 0.97f) return null

        // Coins extrêmes de la zone
        var tl = Int.MAX_VALUE; var br = Int.MIN_VALUE; var tr = Int.MIN_VALUE; var bl = Int.MAX_VALUE
        var tlI = 0; var brI = 0; var trI = 0; var blI = 0
        for (i in 0 until total) {
            if (label[i] != bestLabel) continue
            val x = i % w; val y = i / w
            val s = x + y; val d = x - y
            if (s < tl) { tl = s; tlI = i }
            if (s > br) { br = s; brI = i }
            if (d > tr) { tr = d; trI = i }
            if (d < bl) { bl = d; blI = i }
        }
        fun nx(i: Int) = ((i % w) + 0.5f) / w
        fun ny(i: Int) = ((i / w) + 0.5f) / h
        val q = floatArrayOf(nx(tlI), ny(tlI), nx(trI), ny(trI), nx(brI), ny(brI), nx(blI), ny(blI))
        // Garde-fou : quadrilatère trop petit ou dégénéré
        val area = 0.5f * kotlin.math.abs(
            (q[0] * q[3] - q[2] * q[1]) + (q[2] * q[5] - q[4] * q[3]) + (q[4] * q[7] - q[6] * q[5]) + (q[6] * q[1] - q[0] * q[7])
        )
        return if (area < 0.1f) null else q
    }

    /** Coins quand la page est pivotée de 90° dans le sens horaire. */
    fun rotateQuad90(q: FloatArray): FloatArray {
        // (x, y) → (1 - y, x) ; l'ancien bas-gauche devient le haut-gauche
        fun p(i: Int) = floatArrayOf(1f - q[2 * i + 1], q[2 * i])
        val bl = p(3); val tl = p(0); val tr = p(1); val br = p(2)
        return floatArrayOf(bl[0], bl[1], tl[0], tl[1], tr[0], tr[1], br[0], br[1])
    }

    val FULL = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
}
