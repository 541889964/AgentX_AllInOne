package com.superrouter.agentxallinone

import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.deepseek.chat") {
            try {
                XposedBridge.log("【AgentX】准备启动代理服务...")
                startProxyServer(lpparam.classLoader)
            } catch (e: Throwable) {
                XposedBridge.log("【AgentX】启动出错: ${e.message}")
            }
        }
    }

    private fun startProxyServer(classLoader: ClassLoader) {
        Thread {
            try {
                val server = ServerSocket(13579)
                XposedBridge.log("【AgentX】代理服务已监听 13579")
                while (true) {
                    val socket = server.accept()
                    Thread { handleClient(socket, classLoader) }.start()
                }
            } catch (e: Exception) {
                XposedBridge.log("【AgentX】端口监听失败: ${e.message}")
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
        } catch (e: Throwable) {
            XposedBridge.log("【AgentX】客户端处理异常: ${e.message}")
        }
    }
}
