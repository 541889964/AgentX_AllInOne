package com.superrouter.agentxallinone

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
            setPadding(60, 150, 60, 50)
        }
        
        tvResult = TextView(this).apply {
            textSize = 18f
            text = "请先去 LSPosed 激活本模块并勾选 DeepSeek。\n\n确保官方 DeepSeek 已在后台运行。"
        }
        
        val btnTest = Button(this).apply {
            text = "开始测试联通"
            setOnClickListener {
                tvResult.text = "正在尝试连接 127.0.0.1:13579 ..."
                runTest()
            }
        }
        
        layout.addView(btnTest)
        layout.addView(tvResult)
        setContentView(layout)
    }

    private fun runTest() {
        Thread {
            var resultMsg = ""
            try {
                val socket = Socket("127.0.0.1", 13579)
                val out = PrintWriter(socket.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))

                out.println("PING")
                val response = input.readLine() ?: "无响应"
                socket.close()
                
                resultMsg = if (response == "PONG_FROM_OFFICIAL_APP") {
                    "✅ 连接成功！\n\n你的应用已经可以完美借用官方算力了。"
                } else {
                    "⚠️ 收到未知响应：$response"
                }
            } catch (e: Exception) {
                resultMsg = "❌ 连接失败：${e.message}\n\n排查建议：\n1. 确认LSPosed已勾选DeepSeek并重启\n2. 确认官方App正在后台运行"
            }

            Handler(Looper.getMainLooper()).post {
                tvResult.text = resultMsg
            }
        }.start()
    }
}
