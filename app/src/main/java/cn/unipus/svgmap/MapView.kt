package cn.unipus.svgmap

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.PathParser
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.IOException
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Created by dys on 2021/10/11
 */
class MapView @JvmOverloads constructor(
    context: Context?, attrs: AttributeSet?, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    companion object {
        private const val TAG = "MapView-> "
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
    //板块颜色数组
    private val colorArray = intArrayOf(0xFF239BD7.toInt(), 0xFF30A9E5.toInt(), 0xFF80CBF1.toInt(), 0xFF4087A3.toInt())
    private val paint by lazy { Paint().apply { isAntiAlias = true } }
    //所有省份集合
    private var itemList: ArrayList<ProvinceItem> = arrayListOf()
    //当前选中的省份
    private var selectProvince: ProvinceItem? = null
    //是否需要显示省份名
    private var isShowText = false
    //地图大小信息
    private var totalRect: RectF? = null
    //防抖阈值
    private val minDistance = 10
    //默认缩放系数
    private var scale = 1.0f
    //默认手势mode
    private var mode = NONE
    //是否是点击事件
    private var actionClick = true
    //第一个按下的手指的点
    private val startPoint by lazy { PointF() }
    private val downPoint by lazy { PointF() }
    //初始的两个手指按下的触摸点的距离
    private var oriDistance = 1f
    //x,y方向位移取值
    private var translateX = 0f
    private var translateY = 0f

    /**
     * 加载svg数据
     */
    private val loadThread: Thread = object : Thread() {
        override fun run() {
            try {
                //图形边界
                var left = -1f
                var top = -1f
                var right = -1f
                var bottom = -1f
                //获取raw下的svg文件
                val inputStream = resources.openRawResource(R.raw.china)
                //获取DocumentBuilder实例
                val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                //解析输入流 获取Document实例
                val document = builder.parse(inputStream)
                val rootElement = document.documentElement
                //获取所有path节点集合
                val pathList = rootElement.getElementsByTagName("path")
                val list: ArrayList<ProvinceItem> = ArrayList()
                for (i in 0 until pathList.length) {
                    val element = pathList.item(i) as Element
                    val pathData = element.getAttribute("d")
                    val name = element.getAttribute("title")
                    //将pathData转成Path
                    val path = PathParser.createPathFromPathData(pathData)
                    val provinceItem = ProvinceItem(path)
                    provinceItem.name = name
                    provinceItem.drawColor = colorArray[i % 4]
                    list.add(provinceItem)
                    //计算path图形边界
                    val rectF = RectF()
                    path.computeBounds(rectF, true)
                    left = if (left == -1f) rectF.left else min(left, rectF.left)
                    top = if (top == -1f) rectF.top else min(top, rectF.top)
                    right = if (right == -1f) rectF.right else max(right, rectF.right)
                    bottom = if (bottom == -1f) rectF.bottom else max(bottom, rectF.bottom)
                }
                itemList = list
                totalRect = RectF(left, top, right, bottom)
                //刷新界面
                Handler(Looper.getMainLooper()).post {
                    requestLayout()
                    invalidate()
                }
            } catch (e: ParserConfigurationException) {
                e.printStackTrace()
            } catch (e: SAXException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    init {
        //加载svg数据
        loadThread.start()
    }

    /**
     * 比onDraw先执行
     *
     * 一个MeasureSpec封装了父布局传递给子布局的布局要求，每个MeasureSpec代表了一组宽度和高度的要求。
     * 一个MeasureSpec由大小和模式组成
     * 它有三种模式：
     * UNSPECIFIED: 父元素部队自元素施加任何束缚，子元素可以得到任意想要的大小;
     * EXACTLY: 父元素决定自元素的确切大小，子元素将被限定在给定的边界里而忽略它本身大小；
     * AT_MOST: 子元素至多达到指定大小的值。
     *
     * 它常用的三个函数：
     * 1.static int getMode(int measureSpec):根据提供的测量值(格式)提取模式(上述三个模式之一)
     * 2.static int getSize(int measureSpec):根据提供的测量值(格式)提取大小值(这个大小也就是我们通常所说的大小)
     * 3.static int makeMeasureSpec(int size,int mode):根据提供的大小值和模式创建一个测量值(格式)
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        //获取当前控件的宽高
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        //计算初始scale，使绘制铺满控件
        totalRect?.let {
            val mapWidth = it.width()
            scale = width / mapWidth
            Log.d(TAG, "onMeasure() scale=$scale")
        }
        setMeasuredDimension(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action and MotionEvent.ACTION_MASK) {
            //单点触控
            MotionEvent.ACTION_DOWN         -> {
                startPoint[x] = y
                downPoint[x] = y
                mode = DRAG
                actionClick = true
            }
            //多点触控
            MotionEvent.ACTION_POINTER_DOWN -> {
                oriDistance = distance(event)
                if (oriDistance > minDistance) {
                    mode = ZOOM
                }
                actionClick = false
            }
            MotionEvent.ACTION_MOVE         -> {
                when (mode) {
                    //单指拖动
                    DRAG -> {
                        if (abs(x - downPoint.x) > minDistance || abs(y - downPoint.y) > minDistance) {
                            //当前x平移距离
                            translateX += x - startPoint.x
                            //当前y平移距离
                            translateY += y - startPoint.y
                            startPoint[x] = y
                            actionClick = false
                            invalidate()
                        }
                    }
                    //两指缩放
                    ZOOM -> {
                        //当前两指距离
                        val newDistance = distance(event)
                        if (abs(newDistance - oriDistance) > minDistance) {
                            //相对缩放系数
                            val scaleInner = newDistance / oriDistance
                            //当前缩放系数
                            scale += (scaleInner - 1)
                            Log.d(TAG, "scaleInner=$scaleInner, scale=$scale")
                            if (scale < 1) {
                                scale = 1f
                            }
                            oriDistance = newDistance
                            invalidate()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP           -> {
                //单点
                mode = NONE
                if (actionClick) {
                    //点击事件 x,y根据缩放和平移的数值换算到原始图形范围
                    handleTouch(x / scale - translateX, y / scale - translateY)
                }
            }
            MotionEvent.ACTION_POINTER_UP   ->
                //多点
                mode = NONE
        }
        return true
    }

    /**
     * 处理单击事件
     */
    private fun handleTouch(x: Float, y: Float) {
        Log.d(TAG, "handleTouch x=$x,y=$y")
        isShowText = false
        if (itemList.isEmpty()) {
            return
        }
        var selectItem: ProvinceItem? = null
        for (provinceItem in itemList) {
            if (provinceItem.isTouch(x, y)) {
                selectItem = provinceItem
                provinceItem.clickPoint = PointF(x, y)
                isShowText = true
            }
        }
        selectItem?.let {
            selectProvince = it
            invalidate()
        }
    }

    /**
     * 计算两个手指间的距离
     *
     * @param event 触摸事件
     * @return 两指间距
     */
    private fun distance(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        //两点间距离公式
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (itemList.isNotEmpty()) {
            canvas.save()
            //先缩放再位移，防止位移和点击错乱
            canvas.scale(scale, scale)
            canvas.translate(translateX, translateY)
            //绘制地图
            for (provinceItem in itemList) {
                provinceItem.drawItem(canvas, paint, provinceItem === selectProvince)
            }
            if (isShowText) {
                //绘制文本
                paint.color = Color.RED
                paint.style = Paint.Style.FILL
                paint.textSize = 40f
                selectProvince?.let {
                    canvas.drawText(it.name, it.clickPoint!!.x, it.clickPoint!!.y, paint)
                }
            }
            canvas.restore()
        }
    }
}