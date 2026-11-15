package com.superrouter.agentxallinone

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class MainActivity : Activity() {
    private lateinit var tvResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 40)
        }
        
        val btnTest = Button(this).apply {
            text = "内置提取凭证并发送真实请求"
            setOnClickListener {
                tvResult.text = "正在通过 LSPosed 提取官方凭证并请求网络..."
                runRealTest()
            }
        }
        
        tvResult = TextView(this).apply {
            textSize = 11f
            setPadding(0, 30, 0, 0)
            movementMethod = ScrollingMovementMethod()
            text = "点击上方按钮开始测试\n\n如果成功，下方会滚动显示官方最原始的 SSE 数据流。"
        }
        
        layout.addView(btnTest)
        layout.addView(tvResult)
        setContentView(layout)
    }

    private fun runRealTest() {
        Thread {
            try {
                val socket = Socket("127.0.0.1", 13579)
                val out = PrintWriter(socket.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))

                // 发送真实请求指令
                out.println("REAL_TEST")

                var line: String?
                val sb = StringBuilder()
                while (true) {
                    line = input.readLine() ?: break
                    sb.append(line).append("\n")
                    
                    val finalText = sb.toString()
                    Handler(Looper.getMainLooper()).post {
                        tvResult.text = finalText
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    tvResult.text = "❌ 连接异常：${e.message}\n请确保官方 App 正在后台运行。"
                }
            }
        }.start()
    }
}
