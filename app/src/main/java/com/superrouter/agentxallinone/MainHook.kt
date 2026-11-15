package com.superrouter.agentxallinone

import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.deepseek.chat") {
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
            } catch (e: Exception) { }
        }.start()
    }

    private fun handleClient(socket: Socket, classLoader: ClassLoader) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)
            val request = reader.readLine() ?: return

            if (request == "PING") {
                writer.println("PONG_FROM_OFFICIAL_APP")
            } else if (request.startsWith("REAL_TEST:")) {
                val token = request.substring(10)
                doRealRequest(token, classLoader, writer)
            }
            socket.close()
        } catch (e: Throwable) { }
    }

    private fun doRealRequest(inputToken: String, classLoader: ClassLoader, writer: PrintWriter) {
        try {
            val token = if (inputToken == "FALLBACK") "Ly2zddwXUmMZKl/sv3BbEn2l6QGLURdGW8PpDiaLRqKZFl6zr/xQF3JHKZKcjUGE" else inputToken
            val smid = UUID.randomUUID().toString().replace("-", "")

            // 1. 申请挑战
            val urlChallenge = URL("https://chat.deepseek.com/api/v0/chat/create_pow_challenge")
            val connChallenge = urlChallenge.openConnection() as HttpURLConnection
            connChallenge.requestMethod = "POST"
            connChallenge.setRequestProperty("Authorization", "Bearer $token")
            connChallenge.setRequestProperty("Content-Type", "application/json")
            connChallenge.doOutput = true
            connChallenge.outputStream.write("{\"target_path\":\"/api/v0/chat/completion\"}".toByteArray())

            val resChallenge = BufferedReader(InputStreamReader(connChallenge.inputStream)).readText()
            val challengeJson = JSONObject(resChallenge).getJSONObject("data").getJSONObject("biz_data").getJSONObject("challenge")

            val challenge = challengeJson.getString("challenge")
            val salt = challengeJson.getString("salt")
            val expireAt = challengeJson.getLong("expire_at")
            val difficulty = challengeJson.getLong("difficulty")
            val signature = challengeJson.getString("signature")

            // 2. 借用算力
            val baseStr = "${salt}_${expireAt}_"
            val powClass = XposedHelpers.findClass("com.deepseek.crypto.PowCalculator", classLoader)
            val answer = XposedHelpers.callStaticMethod(powClass, "nativeCalculateDeepSeekHashV1Pow", challenge, baseStr, difficulty) as Long

            // 3. 发起真实对话请求
            val urlChat = URL("https://chat.deepseek.com/api/v0/chat/completion")
            val connChat = urlChat.openConnection() as HttpURLConnection
            connChat.requestMethod = "POST"
            connChat.setRequestProperty("Authorization", "Bearer $token")
            connChat.setRequestProperty("Content-Type", "application/json")
            connChat.setRequestProperty("x-device-id", smid)
            connChat.setRequestProperty("x-client-platform", "android")
            connChat.setRequestProperty("x-client-version", "2.8.0")
            connChat.setRequestProperty("x-ds-pow-response", java.util.Base64.getEncoder().encodeToString(
                JSONObject().put("algorithm", "DeepSeekHashV1").put("challenge", challenge)
                .put("salt", salt).put("answer", answer).put("signature", signature)
                .put("target_path", "/api/v0/chat/completion").toString().toByteArray()
            ))
            connChat.doOutput = true

            val body = JSONObject()
                .put("chat_session_id", UUID.randomUUID().toString())
                .put("prompt", "你好")
                .put("thinking_enabled", false)
                .put("search_enabled", false)
            connChat.outputStream.write(body.toString().toByteArray())

            // 4. 原样转发官方返回的流数据
            val reader = BufferedReader(InputStreamReader(connChat.inputStream))
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                writer.println(line)
            }
        } catch (e: Throwable) {
            writer.println("ERR:${e.message}")
        }
    }
}
