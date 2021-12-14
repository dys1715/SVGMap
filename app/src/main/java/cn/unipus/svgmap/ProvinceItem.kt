package cn.unipus.svgmap

import android.graphics.*

class ProvinceItem(var path: Path) {
    //省份名称
    var name: String = "null"
    //板块颜色
    var drawColor = 0
    //省份区域内的点击位置
    var clickPoint: PointF? = null

    /**
     * 绘制省份
     */
    fun drawItem(canvas: Canvas, paint: Paint, isSelect: Boolean) {
        paint.apply {
            if (isSelect) {
                //绘制内部颜色
                strokeWidth = 1f
                color = 0xFF3700B3.toInt()
                style = Paint.Style.FILL
                canvas.drawPath(path, paint)
                //绘制边界
                style = Paint.Style.STROKE
                color = 0xFFFFC107.toInt()
                canvas.drawPath(path, paint)
            } else {
                color = drawColor
                style = Paint.Style.FILL
                canvas.drawPath(path, paint)
            }
        }
    }

    /**
     * 判断点击是否落在当前的省份Path范围
     */
    fun isTouch(x: Float, y: Float): Boolean {
        //获取path矩形区域
        val rectF = RectF()
        path.computeBounds(rectF, true)
        val region = Region()
        //给定路径
        region.setPath(
            path,
            Region(rectF.left.toInt(), rectF.top.toInt(), rectF.right.toInt(), rectF.bottom.toInt())
        )
        return region.contains(x.toInt(), y.toInt())
    }
}