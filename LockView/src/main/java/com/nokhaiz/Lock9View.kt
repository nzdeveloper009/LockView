package com.nokhaiz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.annotation.AttrRes
import androidx.annotation.RequiresApi
import androidx.annotation.StyleRes

class Lock9View : ViewGroup {
    /**
     * Node related definitions
     */
    private val nodeList: MutableList<NodeView> =
        ArrayList() // List of nodes already connected by lines

    private var x = 0f // Current finger x coordinate
    private var y = 0f // Current finger y coordinate

    /**
     * Layout and node styles
     */
    private var nodeSrc: Drawable? = null
    private var nodeOnSrc: Drawable? = null
    private var nodeSize = 0f // Node size, if not 0, padding and spacing attributes are ignored
    private var nodeAreaExpand = 0f // Expand the touch area of the node
    private var nodeOnAnim = 0 // Animation when node lights up
    private var lineColor = 0
    private var lineWidth = 0f
    private var padding = 0f // Internal padding
    private var spacing = 0f // Spacing between nodes

    /**
     * Auto-connect middle node
     */
    private var autoLink = false

    /**
     * Vibration manager
     */
    private var vibrator: Vibrator? = null
    private var enableVibrate = false
    private var vibrateTime = 0

    /**
     * Paint for drawing lines
     */
    private var paint: Paint? = null

    /**
     * Gesture callback listener interface
     */
    private var callback: GestureCallback? = null

    interface GestureCallback {
        fun onNodeConnected(numbers: IntArray)

        fun onGestureFinished(numbers: IntArray)
    }

    fun setGestureCallback(callback: GestureCallback?) {
        this.callback = callback
    }

    /**
     * Constructor
     */
    constructor(context: Context) : super(context) {
        init(context, null, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs, defStyleAttr, 0)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs, defStyleAttr, defStyleRes)
    }

    /**
     * Initialization
     */
    private fun init(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) {
        // Get defined attributes
        val a =
            context.obtainStyledAttributes(attrs, R.styleable.Lock9View, defStyleAttr, defStyleRes)

        nodeSrc = a.getDrawable(R.styleable.Lock9View_lock9_nodeSrc)
        nodeOnSrc = a.getDrawable(R.styleable.Lock9View_lock9_nodeOnSrc)
        nodeSize = a.getDimension(R.styleable.Lock9View_lock9_nodeSize, 0f)
        nodeAreaExpand = a.getDimension(R.styleable.Lock9View_lock9_nodeAreaExpand, 0f)
        nodeOnAnim = a.getResourceId(R.styleable.Lock9View_lock9_nodeOnAnim, 0)
        lineColor = a.getColor(R.styleable.Lock9View_lock9_lineColor, Color.argb(0, 0, 0, 0))
        lineWidth = a.getDimension(R.styleable.Lock9View_lock9_lineWidth, 0f)
        padding = a.getDimension(R.styleable.Lock9View_lock9_padding, 0f)
        spacing = a.getDimension(R.styleable.Lock9View_lock9_spacing, 0f)

        autoLink = a.getBoolean(R.styleable.Lock9View_lock9_autoLink, false)

        enableVibrate = a.getBoolean(R.styleable.Lock9View_lock9_enableVibrate, false)
        vibrateTime = a.getInt(R.styleable.Lock9View_lock9_vibrateTime, 20)

        a.recycle()

        // Initialize the vibrator
        if (enableVibrate && !isInEditMode) {
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Initialize the paint for drawing
        paint = Paint(Paint.DITHER_FLAG)
        paint!!.style = Paint.Style.STROKE
        paint!!.strokeWidth = lineWidth
        paint!!.color = lineColor
        paint!!.isAntiAlias = true // Anti-aliasing

        // Build nodes
        for (n in 0..8) {
            val node = NodeView(getContext(), n + 1)
            addView(node)
        }

        // Clear FLAG, otherwise onDraw() will not be called, because ViewGroup does not need to call onDraw() with transparent background
        setWillNotDraw(false)
    }

    /**
     * We make the height equal to the width - method to be verified
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = measureSize(widthMeasureSpec) // Measure width
        setMeasuredDimension(size, size)
    }

    /**
     * Measure size
     */
    private fun measureSize(measureSpec: Int): Int {
        val specMode = MeasureSpec.getMode(measureSpec) // Get mode
        val specSize = MeasureSpec.getSize(measureSpec) // Get size
        return when (specMode) {
            MeasureSpec.EXACTLY, MeasureSpec.AT_MOST -> specSize
            MeasureSpec.UNSPECIFIED -> 0
            else -> 0
        }
    }

    /**
     * Layout the nodes here
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (changed) {
            if (nodeSize > 0) { // If nodeSize is set, draw nodes in the center of the 9 equal areas
                val areaWidth = ((right - left) / 3).toFloat()
                for (n in 0..8) {
                    val node = getChildAt(n) as NodeView
                    // Get the coordinates in the 3x3 grid
                    val row = n / 3
                    val col = n % 3
                    // Calculate the actual coordinates
                    val l = (col * areaWidth + (areaWidth - nodeSize) / 2).toInt()
                    val t = (row * areaWidth + (areaWidth - nodeSize) / 2).toInt()
                    val r = (l + nodeSize).toInt()
                    val b = (t + nodeSize).toInt()
                    node.layout(l, t, r, b)
                }
            } else { // Otherwise, layout according to the padding and spacing attributes, manually calculating node size
                val nodeSize = (right - left - padding * 2 - spacing * 2) / 3
                for (n in 0..8) {
                    val node = getChildAt(n) as NodeView
                    // Get the coordinates in the 3x3 grid
                    val row = n / 3
                    val col = n % 3
                    // Calculate the actual coordinates, including padding and spacing
                    val l = (padding + col * (nodeSize + spacing)).toInt()
                    val t = (padding + row * (nodeSize + spacing)).toInt()
                    val r = (l + nodeSize).toInt()
                    val b = (t + nodeSize).toInt()
                    node.layout(l, t, r, b)
                }
            }
        }
    }

    /**
     * Handle gestures here
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                x = event.x // Continuously record the finger's coordinates
                y = event.y
                val currentNode = getNodeAt(x, y)
                if (currentNode != null && !currentNode.isHighLighted) { // A new unlit node is touched
                    if (nodeList.size > 0) {  // If there are already lit nodes
                        if (autoLink) { // Auto-link middle node is enabled
                            val lastNode = nodeList[nodeList.size - 1]
                            val middleNode = getNodeBetween(lastNode, currentNode)
                            if (middleNode != null && !middleNode.isHighLighted) { // If there is a middle node that hasn't been lit
                                // Light up the middle node
                                middleNode.setHighLighted(true, true)
                                nodeList.add(middleNode)
                                handleOnNodeConnectedCallback()
                            }
                        }
                    }
                    // Light up the currently touched node
                    currentNode.setHighLighted(true, false)
                    nodeList.add(currentNode)
                    handleOnNodeConnectedCallback()
                }
                // Only redraw if there are lit nodes
                if (nodeList.size > 0) {
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> if (nodeList.size > 0) { // If there are lit nodes
                // Gesture completed
                handleOnGestureFinishedCallback()
                // Clear the state
                nodeList.clear()
                var n = 0
                while (n < childCount) {
                    val node = getChildAt(n) as NodeView
                    node.setHighLighted(false, false)
                    n++
                }
                // Notify redraw
                invalidate()
            }
        }
        return true
    }

    /**
     * Generate the current number list
     */
    private fun generateCurrentNumbers(): IntArray {
        val numbers = IntArray(nodeList.size)
        for (i in nodeList.indices) {
            val node = nodeList[i]
            numbers[i] = node.number
        }
        return numbers
    }

    /**
     * Each time a point is connected
     */
    private fun handleOnNodeConnectedCallback() {
        if (callback != null) {
            callback!!.onNodeConnected(generateCurrentNumbers())
        }
    }

    /**
     * Gesture completed
     */
    private fun handleOnGestureFinishedCallback() {
        if (callback != null) {
            callback!!.onGestureFinished(generateCurrentNumbers())
        }
    }

    /**
     * System draw callback - mainly for drawing lines
     */
    override fun onDraw(canvas: Canvas) {
        // First draw the existing lines
        for (n in 1 until nodeList.size) {
            val firstNode = nodeList[n - 1]
            val secondNode = nodeList[n]
            canvas.drawLine(
                firstNode.centerX.toFloat(),
                firstNode.centerY.toFloat(),
                secondNode.centerX.toFloat(),
                secondNode.centerY.toFloat(),
                paint!!
            )
        }
        // If there are already lit points, draw a line between the lit point and the finger position
        if (nodeList.size > 0) {
            val lastNode = nodeList[nodeList.size - 1]
            canvas.drawLine(
                lastNode.centerX.toFloat(),
                lastNode.centerY.toFloat(),
                x,
                y,
                paint!!
            )
        }
    }

    /**
     * Get the node at the given coordinate point, return null if the finger is between two nodes
     */
    private fun getNodeAt(x: Float, y: Float): NodeView? {
        for (n in 0 until childCount) {
            val node = getChildAt(n) as NodeView
            if (!(x >= node.left - nodeAreaExpand && x < node.right + nodeAreaExpand)) {
                continue
            }
            if (!(y >= node.top - nodeAreaExpand && y < node.bottom + nodeAreaExpand)) {
                continue
            }
            return node
        }
        return null
    }

    /**
     * Get the node between two nodes, return null if no middle node exists
     */
    private fun getNodeBetween(na: NodeView, nb: NodeView): NodeView? {
        var na = na
        var nb = nb
        if (na.number > nb.number) { // Ensure na is smaller than nb
            val nc = na
            na = nb
            nb = nc
        }
        return if (na.number % 3 == 1 && nb.number - na.number == 2) { // Horizontal case
            getChildAt(na.number) as NodeView
        } else if (na.number <= 3 && nb.number - na.number == 6) { // Vertical case
            getChildAt(na.number + 2) as NodeView
        } else if ((na.number == 1 && nb.number == 9) || (na.number == 3 && nb.number == 7)) { // Diagonal case
            getChildAt(4) as NodeView
        } else {
            null
        }
    }

    /**
     * Node description class
     */
    private inner class NodeView(context: Context?, val number: Int) : View(context) {
        var isHighLighted: Boolean = false
            private set

        init {
            setBackgroundDrawable(nodeSrc)
        }

        fun setHighLighted(highLighted: Boolean, isMid: Boolean) {
            if (this.isHighLighted != highLighted) {
                this.isHighLighted = highLighted
                if (nodeOnSrc != null) { // If no highlight image is set, no change
                    setBackgroundDrawable(if (highLighted) nodeOnSrc else nodeSrc)
                }
                if (nodeOnAnim != 0) { // Play animation
                    if (highLighted) {
                        startAnimation(AnimationUtils.loadAnimation(context, nodeOnAnim))
                    } else {
                        clearAnimation()
                    }
                }
                if (enableVibrate && !isMid) { // Vibration
                    if (highLighted) {
                        vibrator!!.vibrate(vibrateTime.toLong())
                    }
                }
            }
        }

        val centerX: Int
            get() = (left + right) / 2

        val centerY: Int
            get() = (top + bottom) / 2
    }
}