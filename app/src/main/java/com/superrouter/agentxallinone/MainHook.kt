package com.superrouter.agentxallinone

import android.util.Log
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import okhttp3.OkHttpClient
import okhttp3.Request

class MainHook : IXposedHookLoadPackage {
    private val client = OkHttpClient()

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 当勾选的是官方 DeepSeek 时，启动幽灵代理服务
        if (lpparam.packageName == "com.deepseek.chat") {
            XposedBridge.log("【AgentX】寄生成功，正在启动代理服务...")
            startProxyServer(lpparam.classLoader)
        }
    }

    private fun startProxyServer(classLoader: ClassLoader) {
        Thread {
            try {
                val server = ServerSocket(13579)
                while (true) {
                    val socket = server.accept()
                    Thread { handleClient(socket, classLoader) }.start()
                }
            } catch (e: Exception) {
                XposedBridge.log("代理服务异常: ${e.message}")
            }
        }.start()
    }

    private fun handleClient(socket: Socket, classLoader: ClassLoader) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)
            val request = reader.readLine() ?: return

            if (request == "PING") {
                writer.println("PONG_FROM_OFFICIAL_APP")
            } else if (request.startsWith("CALC:")) {
                val parts = request.substring(5).split("|")
                val powClass = XposedHelpers.findClass("com.deepseek.crypto.PowCalculator", classLoader)
                val answer = XposedHelpers.callStaticMethod(powClass, "nativeCalculateDeepSeekHashV1Pow", parts[0], parts[1], parts[2].toLong()) as Long
                writer.println("OK:$answer")
            }
            socket.close()
        } catch (e: Exception) { }
    }
}