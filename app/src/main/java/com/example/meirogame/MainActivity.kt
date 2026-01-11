package com.example.meirogame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var gameView: MazeGameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = MazeGameView(this)
        setContentView(gameView)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(gameView, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(gameView)
    }

    private class MazeGameView(context: Context) : View(context), SensorEventListener {
        private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY }
        private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
        private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GREEN }
        private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLUE }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 64f
        }

        private val maze = listOf(
            "###############",
            "#S....#.......#",
            "#.##.#.#.###.#.#",
            "#....#.#...#.#.#",
            "####.#.###.#.#.#",
            "#....#.....#...#",
            "#.######.#####.#",
            "#......#.....#.#",
            "#.####.#.###.#.#",
            "#.#....#...#...#",
            "#.#.######.###.#",
            "#.#...........G#",
            "###############"
        )
        private val rows = maze.size
        private val cols = maze[0].length

        private var cellSize = 0f
        private var offsetX = 0f
        private var offsetY = 0f

        private var ballX = 0f
        private var ballY = 0f
        private var ballRadius = 0f
        private var lastTimeNanos = 0L
        private var accelX = 0f
        private var accelY = 0f
        private var win = false
        private var winToastShown = false

        init {
            findStartPosition()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            cellSize = min(w.toFloat() / cols, h.toFloat() / rows)
            offsetX = (w - cellSize * cols) / 2f
            offsetY = (h - cellSize * rows) / 2f
            ballRadius = cellSize * 0.35f
            findStartPosition()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            drawMaze(canvas)
            updatePhysics()
            drawBall(canvas)
            if (win) {
                drawWin(canvas)
            }
            postInvalidateOnAnimation()
        }

        private fun drawMaze(canvas: Canvas) {
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val left = offsetX + col * cellSize
                    val top = offsetY + row * cellSize
                    val rectPaint = when (maze[row][col]) {
                        '#' -> wallPaint
                        'S' -> startPaint
                        'G' -> goalPaint
                        else -> pathPaint
                    }
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, rectPaint)
                }
            }
        }

        private fun drawBall(canvas: Canvas) {
            canvas.drawCircle(ballX, ballY, ballRadius, ballPaint)
        }

        private fun drawWin(canvas: Canvas) {
            val message = "GOAL!"
            val textWidth = textPaint.measureText(message)
            canvas.drawText(
                message,
                width / 2f - textWidth / 2f,
                offsetY / 2f + textPaint.textSize,
                textPaint
            )
            if (!winToastShown) {
                Toast.makeText(context, "ゴール！", Toast.LENGTH_SHORT).show()
                winToastShown = true
            }
        }

        private fun updatePhysics() {
            val now = System.nanoTime()
            if (lastTimeNanos == 0L) {
                lastTimeNanos = now
                return
            }
            if (win) {
                return
            }
            val deltaSeconds = (now - lastTimeNanos) / 1_000_000_000f
            lastTimeNanos = now

            val speed = 600f
            val dx = accelX * speed * deltaSeconds
            val dy = accelY * speed * deltaSeconds

            moveBall(dx, dy)
            checkGoal()
        }

        private fun moveBall(dx: Float, dy: Float) {
            var newX = ballX + dx
            var newY = ballY + dy

            newX = clamp(newX, offsetX + ballRadius, offsetX + cols * cellSize - ballRadius)
            newY = clamp(newY, offsetY + ballRadius, offsetY + rows * cellSize - ballRadius)

            if (!isWallAt(newX, ballY)) {
                ballX = newX
            }
            if (!isWallAt(ballX, newY)) {
                ballY = newY
            }
        }

        private fun isWallAt(x: Float, y: Float): Boolean {
            val col = ((x - offsetX) / cellSize).toInt()
            val row = ((y - offsetY) / cellSize).toInt()
            if (row !in 0 until rows || col !in 0 until cols) {
                return true
            }
            return maze[row][col] == '#'
        }

        private fun checkGoal() {
            val col = ((ballX - offsetX) / cellSize).toInt()
            val row = ((ballY - offsetY) / cellSize).toInt()
            if (row in 0 until rows && col in 0 until cols && maze[row][col] == 'G') {
                win = true
            }
        }

        private fun clamp(value: Float, minValue: Float, maxValue: Float): Float {
            return max(minValue, min(value, maxValue))
        }

        private fun findStartPosition() {
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    if (maze[row][col] == 'S') {
                        val centerX = offsetX + (col + 0.5f) * cellSize
                        val centerY = offsetY + (row + 0.5f) * cellSize
                        if (cellSize == 0f) {
                            ballX = centerX
                            ballY = centerY
                        } else {
                            ballX = centerX
                            ballY = centerY
                        }
                        return
                    }
                }
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) {
                return
            }
            val rawX = event.values[0]
            val rawY = event.values[1]
            accelX = -rawX
            accelY = rawY

            if (abs(accelX) < 0.05f) accelX = 0f
            if (abs(accelY) < 0.05f) accelY = 0f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            return
        }
    }
}
