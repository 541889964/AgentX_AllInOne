package com.superrouter.agentxallinone

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class MainActivity : Activity() {
    private lateinit var tvResult: TextView
    private lateinit var etToken: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 40)
        }
        
        etToken = EditText(this).apply {
            hint = "输入官方 Token (不填用兜底)"
            textSize = 14f
        }
        
        val btnTest = Button(this).apply {
            text = "发送真实请求测试"
            setOnClickListener {
                tvResult.text = "正在请求官方服务器..."
                runRealTest(etToken.text.toString().trim())
            }
        }
        
        tvResult = TextView(this).apply {
            textSize = 12f
            setPadding(0, 30, 0, 0)
            movementMethod = ScrollingMovementMethod()
            text = "等待测试..."
        }
        
        layout.addView(etToken)
        layout.addView(btnTest)
        layout.addView(tvResult)
        setContentView(layout)
    }

    private fun runRealTest(inputToken: String) {
        Thread {
            try {
                val socket = Socket("127.0.0.1", 13579)
                val out = PrintWriter(socket.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))

                // 发送真实请求指令，带上 Token
                val tokenToUse = if (inputToken.isNotBlank()) inputToken else "FALLBACK"
                out.println("REAL_TEST:$tokenToUse")

                // 读取官方返回的每一行原始数据，立刻展示
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
                    tvResult.text = "❌ 测试异常：${e.message}"
                }
            }
        }.start()
    }
}
