package com.readingtracker.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import com.readingtracker.ui.RtColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Цвета ленты-ляссе: основа, изнанка, кромка, полоски, полоски на изнанке. */
data class RibbonPalette(
    val base: Int,
    val back: Int,
    val edge: Int,
    val stripe: Int,
    val stripeBack: Int
)

enum class RibbonPattern { DIAMONDS, DOTS, STRIPES }

object Ribbons {
    private fun c(v: Long) = v.toInt()

    val palettes = listOf(
        RibbonPalette(c(0xFF6B4CE6), c(0xFF5A3FD0), c(0xFF3F2A9E), c(0xFFD6F36B), c(0xFFBFD95C)), // фиолетовый, салатовый
        RibbonPalette(c(0xFFE8573C), c(0xFFC94A31), c(0xFF8E2F1C), c(0xFFFFE7C2), c(0xFFF2D3A6)), // коралл, крем
        RibbonPalette(c(0xFF138A72), c(0xFF0F7360), c(0xFF0A4F42), c(0xFFFFC94A), c(0xFFE6B13B)), // бирюза, янтарь
        RibbonPalette(c(0xFF2F5BD3), c(0xFF2549AE), c(0xFF18306F), c(0xFFFFB3C7), c(0xFFE89DB1)), // кобальт, розовый
        RibbonPalette(c(0xFF8E2A3B), c(0xFF76212F), c(0xFF4E1520), c(0xFFE6C27A), c(0xFFCFAE6B)), // бордо, золото
        RibbonPalette(c(0xFF2E6B3F), c(0xFF255834), c(0xFF173A22), c(0xFFBFF0C8), c(0xFFA6D8B0)), // лес, мята
        RibbonPalette(c(0xFFF28C28), c(0xFFD57818), c(0xFF9A520C), c(0xFF1F2A44), c(0xFF18213A)), // оранжевый, тёмно-синий
        RibbonPalette(c(0xFFFF5C8A), c(0xFFE04A76), c(0xFFA3304F), c(0xFF1C1C1C), c(0xFF333333))  // малиновый, чёрный
    )

    /** Семейства похожих цветов: у книг, которые читаются одновременно, ленты из разных семейств. */
    private val family = intArrayOf(0, 1, 3, 0, 2, 3, 1, 2)

    val fallback = palettes[0]

    private fun hashIndex(bookId: Long) = Random(bookId * 7919 + 17).nextInt(palettes.size)

    fun patternFor(bookId: Long): RibbonPattern =
        RibbonPattern.values()[Random(bookId * 104729 + 3).nextInt(RibbonPattern.values().size)]

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("ribbons", Context.MODE_PRIVATE)

    /**
     * Цвет ленты закрепляется за книгой раз и навсегда (хранится в настройках).
     * Книги идут от давно начатых к новым; если цвет книги из того же семейства,
     * что у читаемой рядом, ей подбирается другой — и тоже запоминается.
     */
    fun assign(ctx: Context, bookIdsOldestFirst: List<Long>): Map<Long, RibbonPalette> {
        val pr = prefs(ctx)
        val ed = pr.edit()
        val taken = mutableSetOf<Int>()
        val out = LinkedHashMap<Long, RibbonPalette>()
        for (id in bookIdsOldestFirst) {
            var idx = pr.getInt("p_$id", -1)
            if (idx !in palettes.indices || (family[idx] in taken && taken.size < 4)) {
                val start = if (idx in palettes.indices) idx else hashIndex(id)
                idx = (0 until palettes.size).map { (start + it * 3) % palettes.size }
                    .firstOrNull { family[it] !in taken } ?: start
                ed.putInt("p_$id", idx)
            }
            taken += family[idx]
            out[id] = palettes[idx]
        }
        ed.apply()
        return out
    }

    /** Палитра, закреплённая за книгой (или та, что выпала бы ей по номеру). */
    fun paletteOf(ctx: Context, bookId: Long): RibbonPalette =
        palettes.getOrElse(prefs(ctx).getInt("p_$bookId", -1)) { palettes[hashIndex(bookId)] }

    /** Палитра книги сверху стопки: в ч/б из неё берётся акцент других экранов. */
    @Volatile var current: RibbonPalette = fallback

    fun setCurrent(ctx: Context, pal: RibbonPalette) {
        current = pal
        prefs(ctx).edit().putInt("current", palettes.indexOf(pal).coerceAtLeast(0)).apply()
    }

    /** Акцент для других экранов (графики, теплокарта, список книг): основа ленты, читаемая на фоне. */
    fun accent(ctx: Context): Int {
        val bg = RtColors.c(ctx, com.readingtracker.R.color.rt_bg)
        val p = current
        return if (RtColors.contrast(bg, p.base) >= 2.6) p.base
            else RtColors.mostReadable(bg, p.base, p.edge, p.stripe, RtColors.mix(p.base, RtColors.c(ctx, com.readingtracker.R.color.rt_ink), 0.4f))
    }


    fun restore(ctx: Context) {
        current = palettes.getOrElse(prefs(ctx).getInt("current", 0)) { fallback }
    }
}

enum class TailKind { DOWN, UP, SIDE }

/**
 * Маршрут ленты в координатах обложки (обложка 150×225, страницы справа до x≈156, снизу до y≈231).
 * Лента крепится слева, у корешка: арка выходит из-под верхнего края обложки и уходит в страницы.
 * Хвост выходит между страницами снизу, сверху или сбоку и заканчивается бахромой.
 */
class RibbonRoute(
    val arch: Array<PointF>,
    val tail: Array<PointF>,
    val twistFrom: Float,
    val twistTo: Float,
    val seed: Long
) {
    companion object {
        // Характеры хвоста: уходит вбок, висит почти прямо, наискосок (для хвоста вниз)
        private val SHAPES = arrayOf(
            floatArrayOf(0f, 0f, 1.5f, 11.4f, 10.5f, 22.4f, 33.5f, 19.4f),
            floatArrayOf(0f, 0f, 0.5f, 12f, -2.5f, 22f, 3f, 31f),
            floatArrayOf(0f, 0f, 2f, 10f, 12f, 17f, 22f, 29f)
        )
        // Сбоку: выходит из обреза вправо и свешивается вниз
        private val SIDE = arrayOf(
            floatArrayOf(0f, 0f, 9f, 0.5f, 15f, 7f, 17f, 26f),
            floatArrayOf(0f, 0f, 10f, -1f, 19f, 6f, 21f, 22f)
        )

        fun random(rnd: Random = Random(System.nanoTime()), kind: TailKind = TailKind.DOWN): RibbonRoute {
            fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
            val x0 = r(16f, 30f)
            val w = r(22f, 34f)
            val h = r(11f, 15f)
            val arch = arrayOf(
                PointF(x0, 6f),
                PointF(x0 + r(-1f, 3f), -h),
                PointF(x0 + w * r(0.45f, 0.7f), -h * r(0.95f, 1.1f)),
                PointF(x0 + w, 6f)
            )
            val len = r(0.92f, 1.08f)
            val tail = when (kind) {
                TailKind.DOWN, TailKind.UP -> {
                    val sh = SHAPES[rnd.nextInt(SHAPES.size)]
                    val root = r(40f, 128f)
                    val dir = if (rnd.nextBoolean()) 1f else -1f
                    val y0 = if (kind == TailKind.DOWN) 228.6f else 2f
                    val sy = if (kind == TailKind.DOWN) 1f else -1f
                    Array(4) { k -> PointF(root + dir * sh[2 * k] * len, y0 + sy * sh[2 * k + 1] * len) }
                }
                TailKind.SIDE -> {
                    val sh = SIDE[rnd.nextInt(SIDE.size)]
                    val y0 = r(60f, 175f)
                    Array(4) { k -> PointF(152.5f + sh[2 * k] * len, y0 + sh[2 * k + 1] * len) }
                }
            }
            val a = r(0.22f, 0.35f)
            val b = (a + r(0.55f, 0.7f)).coerceAtMost(0.98f)
            return RibbonRoute(arch, tail, a, b, rnd.nextLong())
        }
    }
}

/** Рисует ленту. Координаты — в dp обложки; холст уже сдвинут и масштабирован. */
class RibbonPainter(private val pal: RibbonPalette, private val pattern: RibbonPattern) {

    // Полуширина ленты; чуть толще прежних 3,5
    private val hw = 4.2f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.6f; strokeJoin = Paint.Join.ROUND
    }
    private val thread = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.6f; strokeCap = Paint.Cap.ROUND
    }
    private val shade = 0xFF1A1040.toInt()

    private class Frame(
        val x: Float, val y: Float, val nx: Float, val ny: Float,
        val tx: Float, val ty: Float, val c: Float, val s: Float, val ws: Float
    )

    private fun bezier(p: Array<PointF>, n: Int): List<PointF> = (0..n).map { i ->
        val t = i / n.toFloat(); val u = 1 - t
        PointF(
            u * u * u * p[0].x + 3 * u * u * t * p[1].x + 3 * u * t * t * p[2].x + t * t * t * p[3].x,
            u * u * u * p[0].y + 3 * u * u * t * p[1].y + 3 * u * t * t * p[2].y + t * t * t * p[3].y
        )
    }

    private fun smooth(u: Float): Float { val v = u.coerceIn(0f, 1f); return v * v * (3 - 2 * v) }

    private fun frames(pts: List<PointF>, twist: (Float) -> Float, widthScale: (Float) -> Float): List<Frame> {
        val len = FloatArray(pts.size)
        for (i in 1 until pts.size) len[i] = len[i - 1] + hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        val total = max(len.last(), 0.001f)
        return pts.indices.map { i ->
            val a = pts[max(i - 1, 0)]; val b = pts[minOf(i + 1, pts.size - 1)]
            var dx = b.x - a.x; var dy = b.y - a.y
            val m = max(hypot(dx, dy), 0.0001f); dx /= m; dy /= m
            val u = len[i] / total
            Frame(pts[i].x, pts[i].y, -dy, dx, dx, dy, cos(twist(u)), len[i], widthScale(u))
        }
    }

    private var minW = 0.3f

    private fun off(f: Frame, k: Float): PointF {
        val w = hw * f.ws * max(abs(f.c), minW) * k
        return PointF(f.x + f.nx * w, f.y + f.ny * w)
    }

    private fun poly(canvas: Canvas, pts: List<PointF>, paint: Paint) {
        if (pts.size < 3) return
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun band(canvas: Canvas, seg: List<Frame>, k1: Float, k2: Float, color: Int) {
        fill.color = color
        poly(canvas, seg.map { off(it, k1) } + seg.reversed().map { off(it, k2) }, fill)
    }

    private fun piece(
        canvas: Canvas, fr: List<Frame>, entry: Triple<Float, Float, Int>?,
        fringe: Boolean, shadeK: Float, rnd: Random, entryLen: Float = 0f
    ) {
        // Отрезки одной стороны ленты: лицо или изнанка
        val runs = mutableListOf<MutableList<Int>>()
        var cur = mutableListOf(0)
        for (i in 1 until fr.size) {
            if ((fr[i].c >= 0) != (fr[cur.last()].c >= 0)) { cur.add(i); runs.add(cur); cur = mutableListOf(i) } else cur.add(i)
        }
        runs.add(cur)
        for (r in runs) {
            val seg = r.map { fr[it] }
            if (seg.size < 2) continue
            val front = seg[seg.size / 2].c >= 0
            val outline = seg.map { off(it, 1f) } + seg.reversed().map { off(it, -1f) }
            fill.color = if (front) pal.base else pal.back
            poly(canvas, outline, fill)
            edge.color = pal.edge
            val p = Path(); p.moveTo(outline[0].x, outline[0].y)
            for (i in 1 until outline.size) p.lineTo(outline[i].x, outline[i].y)
            p.close(); canvas.drawPath(p, edge)
            val sc = if (front) pal.stripe else pal.stripeBack
            band(canvas, seg, 0.60f, 0.80f, sc)
            band(canvas, seg, -0.60f, -0.80f, sc)
            if (pattern == RibbonPattern.STRIPES) band(canvas, seg, -0.10f, 0.10f, sc)
        }
        // Узор по центру
        if (pattern != RibbonPattern.STRIPES) {
            val step = 5.2f; var next = step / 2
            for ((i, f) in fr.withIndex()) {
                if (f.s >= next && i < fr.size - 2) {
                    next += step
                    val c = abs(f.c); if (c < 0.35f) continue
                    fill.color = if (f.c >= 0) pal.stripe else pal.stripeBack
                    val h = hw * f.ws * 0.34f * c
                    if (pattern == RibbonPattern.DIAMONDS) {
                        val l = 1.7f
                        poly(canvas, listOf(
                            PointF(f.x + f.tx * l, f.y + f.ty * l), PointF(f.x + f.nx * h, f.y + f.ny * h),
                            PointF(f.x - f.tx * l, f.y - f.ty * l), PointF(f.x - f.nx * h, f.y - f.ny * h)
                        ), fill)
                    } else {
                        canvas.drawCircle(f.x, f.y, h * 0.85f, fill)
                    }
                }
            }
        }
        // Тень на перекруте
        for (i in 0 until fr.size - 1) {
            val c = minOf(abs(fr[i].c), abs(fr[i + 1].c))
            if (c < 0.95f) {
                fill.color = RtColors.withAlpha(shade, (1 - c) * shadeK)
                poly(canvas, listOf(off(fr[i], 1f), off(fr[i + 1], 1f), off(fr[i + 1], -1f), off(fr[i], -1f)), fill)
            }
        }
        // Тень там, где лента уходит в страницы
        if (entry != null) {
            val (lo, hi, dir) = entry
            for (i in 0 until fr.size - 1) {
                val y = fr[i].y
                if (y in lo..hi) {
                    val d = if (dir > 0) (y - lo) / (hi - lo) else (hi - y) / (hi - lo)
                    fill.color = RtColors.withAlpha(shade, d * 0.42f)
                    poly(canvas, listOf(off(fr[i], 1.05f), off(fr[i + 1], 1.05f), off(fr[i + 1], -1.05f), off(fr[i], -1.05f)), fill)
                }
            }
        }
        // Хвост темнее у самого выхода из страниц
        if (entryLen > 0f) {
            for (i in 0 until fr.size - 1) {
                val s0 = fr[i].s
                if (s0 < entryLen) {
                    fill.color = RtColors.withAlpha(shade, (1 - s0 / entryLen) * 0.42f)
                    poly(canvas, listOf(off(fr[i], 1.05f), off(fr[i + 1], 1.05f), off(fr[i + 1], -1.05f), off(fr[i], -1.05f)), fill)
                }
            }
        }
        // Бахрома
        if (fringe) {
            val f = fr.last(); val front = f.c >= 0
            for (j in 0 until 13) {
                val k = -0.92f + j * (1.84f / 12f)
                val bp = off(f, k)
                val ang = (rnd.nextFloat() * 0.44f - 0.22f) + k * 0.12f
                val l = 4.2f + rnd.nextFloat() * 2.4f
                val tx = f.tx * cos(ang) - f.ty * sin(ang)
                val ty = f.tx * sin(ang) + f.ty * cos(ang)
                val mx = bp.x + tx * l * 0.5f + f.nx * (rnd.nextFloat() * 0.6f - 0.3f)
                val my = bp.y + ty * l * 0.5f + f.ny * (rnd.nextFloat() * 0.6f - 0.3f)
                thread.color = if (abs(k) in 0.58f..0.82f) (if (front) pal.stripe else pal.stripeBack)
                    else (if (front) pal.base else pal.back)
                val p = Path(); p.moveTo(bp.x, bp.y); p.quadTo(mx, my, bp.x + tx * l, bp.y + ty * l)
                canvas.drawPath(p, thread)
            }
        }
    }

    fun draw(canvas: Canvas, route: RibbonRoute) {
        val rnd = Random(route.seed)
        // Арка над верхним обрезом: дальняя нога уже, на ней изнанка (перспектива)
        minW = 0.5f
        val arch = frames(bezier(route.arch, 140), { u -> (PI * smooth((u - 0.2f) / 0.6f)).toFloat() },
            { u -> 1f - 0.22f * smooth(u) })
        piece(canvas, arch, Triple(-4f, 2f, 1), false, 0.22f, rnd)
        // Хвост с перекрутом на изгибе и бахромой
        minW = 0.28f
        val a = route.twistFrom; val b = route.twistTo
        val tail = frames(bezier(route.tail, 180), { u -> (PI * smooth((u - a) / (b - a))).toFloat() }, { 1f })
        piece(canvas, tail, null, true, 0.32f, rnd, 7.4f)
    }
}
