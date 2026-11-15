package com.superrouter.agentxallinone

import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.regex.Pattern

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.deepseek.chat") {
            XposedBridge.log("【AgentX】模块已加载，启动服务...")
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
            } else if (request == "REAL_TEST") {
                doRealRequest(classLoader, writer)
            }
            socket.close()
        } catch (e: Throwable) { }
    }

    // ★ 核心：直接读取官方 App 私有目录下的 MMKV 提取凭证
    private fun getOfficialCredentials(): Pair<String, String> {
        var token = ""
        var smid = ""
        try {
            val file = File("/data/data/com.deepseek.chat/files/mmkv/mmkv.default")
            if (file.exists()) {
                val content = file.readText(Charsets.ISO_8859_1)
                val tokenMatcher = Pattern.compile("\\b[a-zA-Z0-9+/=]{64}\\b").matcher(content)
                if (tokenMatcher.find()) token = tokenMatcher.group() ?: ""
                
                val smidMatcher = Pattern.compile("\\b[a-zA-Z0-9_\\-+/=]{70,120}\\b").matcher(content)
                if (smidMatcher.find()) smid = smidMatcher.group() ?: ""
            }
        } catch (e: Exception) {
            XposedBridge.log("【AgentX】读取凭证异常: ${e.message}")
        }
        if (smid.isBlank()) smid = UUID.randomUUID().toString().replace("-", "")
        return Pair(token, smid)
    }

    private fun doRealRequest(classLoader: ClassLoader, writer: PrintWriter) {
        try {
            val (token, smid) = getOfficialCredentials()
            if (token.isBlank()) {
                writer.println("ERROR: 无法从 MMKV 提取 Token，请确保官方 App 已登录。")
                return
            }
            
            writer.println("✅ 成功提取 Token: ${token.take(10)}... (SMID: ${smid.take(10)}...)")
            writer.println("--- 开始请求官方服务器 ---")

            // 1. 申请挑战
            val urlChallenge = URL("https://chat.deepseek.com/api/v0/chat/create_pow_challenge")
            val connChallenge = urlChallenge.openConnection() as HttpURLConnection
            connChallenge.requestMethod = "POST"
            connChallenge.setRequestProperty("Authorization", "Bearer $token")
            connChallenge.setRequestProperty("Content-Type", "application/json")
            connChallenge.doOutput = true
            connChallenge.outputStream.write("{\"target_path\":\"/api/v0/chat/completion\"}".toByteArray())

            val resChallenge = BufferedReader(InputStreamReader(connChallenge.inputStream)).readText()
            val rootJson = JSONObject(resChallenge)

            if (rootJson.has("code") && rootJson.getInt("code") != 0) {
                writer.println("SERVER_ERROR:${rootJson.toString()}")
                return
            }

            val dataJson = rootJson.getJSONObject("data").getJSONObject("biz_data").getJSONObject("challenge")
            val challenge = dataJson.getString("challenge")
            val salt = dataJson.getString("salt")
            val expireAt = dataJson.getLong("expire_at")
            val difficulty = dataJson.getLong("difficulty")
            val signature = dataJson.getString("signature")

            // 2. 借用底层算力
            val baseStr = "${salt}_${expireAt}_"
            val powClass = XposedHelpers.findClass("com.deepseek.crypto.PowCalculator", classLoader)
            val answer = XposedHelpers.callStaticMethod(powClass, "nativeCalculateDeepSeekHashV1Pow", challenge, baseStr, difficulty) as Long
            writer.println("✅ PoW 计算成功: $answer")

            // 3. 发起对话
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

            // 4. 原样读取流
            val reader = BufferedReader(InputStreamReader(connChat.inputStream))
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                writer.println(line)
            }
        } catch (e: Throwable) {
            writer.println("CLIENT_ERROR:${e.message}")
        }
    }
}
