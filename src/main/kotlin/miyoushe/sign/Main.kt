package miyoushe.sign

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okio.ByteString

import java.io.Closeable
import java.io.IOException
import java.lang.reflect.Type
import java.net.URLEncoder
import java.util.*
import kotlin.system.exitProcess

class Main {
  companion object {
    private const val PRINT_RESPONSE = true
    private const val APP_VERSION = "2.34.1"
    private const val SALT = "9nQiU3AV0rJSIBWgdynfoGMGKaklfbM7"
    private const val CLIENT_TYPE = "5"
    private const val ACT_ID = "e202009291139501"
    private const val REGION = "cn_gf01"
    private const val ROLE_URL = "https://api-takumi.mihoyo.com/binding/api/getUserGameRolesByCookie?game_biz=hk4e_cn"
    private const val SIGN_URL = "https://api-takumi.mihoyo.com/event/bbs_sign_reward/sign"

    private val BASIC_HEADERS = mapOf(
      "User-Agent" to "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) miHoYoBBS/$APP_VERSION",
      "Referer" to "https://webstatic.mihoyo.com/bbs/event/signin-ys/index.html?bbs_auth_required=true&act_id=$ACT_ID&utm_source=bbs&utm_medium=mys&utm_campaign=icon",
      "x-rpc-channel" to "appstore",
      "accept-language" to "zh-CN,zh;q=0.9,ja-JP;q=0.8,ja;q=0.7,en-US;q=0.6,en;q=0.5",
      "x-requested-with" to "com.mihoyo.hyperion",
      "Host" to "api-takumi.mihoyo.com"
    )

    private val client = OkHttpClient()
    private val gson = Gson()

    @JvmStatic
    fun main(args: Array<String>) {
      if (System.getProperty("debugArgs") == "true") {
        Log.println("serverChanKey=" + System.getProperty("serverChanKey"))
        args.forEachIndexed { index, cookie->
          Log.println("账号$index: $cookie")
          Log.println("")
        }
        return
      }

      if (args.isEmpty()) {
        println("请输入需要签到的账号cookies.")
        return
      }
      Log.println("共${args.size}个账号.")

      val pushMessageBuilder = StringBuilder()
      for ((index, cookie) in args.withIndex()) {
        println("正在签到第${index + 1}个账号.")
        try {
          val uidAndName = requestGameUid(cookie)
          if (uidAndName == null) {
            println("无法签到此账号.")
            pushMessageBuilder.append("账号($index)无法签到")
            if (index != args.lastIndex) {
              pushMessageBuilder.append(", ")
            }
            continue
          }
          println("已获取用户信息: ${uidAndName.second}(${uidAndName.first}), 正在签到.")
          // 签到
          val signSucceed = requestSign(cookie, uidAndName.first)
          pushMessageBuilder.append(uidAndName.second)
            .append(if (signSucceed) "签到成功" else "签到失败")
          if (index != args.lastIndex) {
            pushMessageBuilder.append(", ")
          }
        } catch (e: Throwable) {
          Log.println("签到抛出异常.")
          Log.printStacktrace(e)
        }
      }

      Log.println("pushMsg=$pushMessageBuilder")

      // 发送推送消息到server酱
      sendPushMessage(pushMessageBuilder.toString())

      // okhttp线程池还在keepalive，这里退出一下
      exitProcess(0)
    }

    private fun sendPushMessage(message: String) {
      val sendKey = System.getProperty("serverChanKey")
      if (sendKey == null) {
        Log.println("server酱sendKey未配置，不发送推送信息")
        return
      }
      val urlEncodedMessage = URLEncoder.encode(message, Charsets.UTF_8)
      val request = Request.Builder()
        .url("https://sctapi.ftqq.com/${sendKey}.send?title=$urlEncodedMessage&desp=$urlEncodedMessage")
        .build()

      val pushRes = executeToModel<PushRes>(request, PushRes::class.java)
      if (pushRes != null) {
        Log.println("发送推送消息${if (pushRes.code == 0) "成功" else "失败"}, msg=${pushRes.message}")
      }
    }

    private fun makeDynamicSecrets(): String {
      val timestamp = System.currentTimeMillis() / 1000
      val random = CharArray(6) {
        "abcdefghijklmnopqrstuvwxyz0123456789".random()
      }.concatToString()
      val sign = ByteString.encodeUtf8("salt=${SALT}&t=${timestamp}&r=${random}")
        .md5()
        .hex()
      return "${timestamp},${random},${sign}"
    }

    /**
     * 签到
     */
    private fun requestSign(cookie: String, uid: String): Boolean {
      val jsonBody = gson.toJson(
        mapOf(
          "act_id" to ACT_ID,
          "region" to REGION,
          "uid" to uid
        )
      )
      val request = Request.Builder()
        .url(SIGN_URL)
        .headers(Headers.of(BASIC_HEADERS))
        .header("x-rpc-device_id", UUID.randomUUID().toString().replace("-", "").uppercase(Locale.getDefault()))
        .header("x-rpc-client_type", CLIENT_TYPE)
        .header("x-rpc-app_version", APP_VERSION)
        .header("DS", makeDynamicSecrets())
        .header("Cookie", cookie)
        .post(RequestBody.create(MediaType.parse("application/json"), jsonBody))
        .build()

      val modelType = TypeToken.getParameterized(ApiRes::class.java, SignResult::class.java).type
      val apiRes = executeToModel<ApiRes<SignResult>>(request, modelType)
      return apiRes != null && (apiRes.retcode == 0/*正常签到*/ || apiRes.retcode == -5003/*重复签到*/)
    }

    /**
     * 获取用户信息
     */
    private fun requestGameUid(cookie: String): Pair<String, String>? {
      val request = Request.Builder()
        .get()
        .url(ROLE_URL)
        .headers(Headers.of(BASIC_HEADERS))
        .header("Cookie", cookie)
        .build()
      val modelType = TypeToken.getParameterized(ApiRes::class.java, RoleList::class.java).type
      val apiRes = executeToModel<ApiRes<RoleList>>(request, modelType)

      return apiRes?.run {
        if (retcode == 0 && data != null) {
          data.list[0].game_uid to data.list[0].nickname
        } else {
          Log.println("获取用户id后端返回异常, code=${retcode}, msg=${message}")
          null
        }
      }
    }

    private fun Closeable.closeQuietly() {
      try {
        this.close()
      } catch (ignored: IOException) {
      }
    }

    private fun <T> executeToModel(request: Request, modelType: Type): T? {
      var res: Response? = null
      val responseJson = try {
        res = client.newCall(request).execute()
        if (!res.isSuccessful || res.body() == null) {
          Log.println("服务器返回码异常, code=${res.code()}, msg=${res.message()}, method=${request.method()}, url=${request.url()}.")
          null
        } else {
          res.body()!!.string()
        }
      } catch (e: IOException) {
        Log.println("发生网络错误, method=${request.method()}, url=${request.url()}.")
        Log.printStacktrace(e)
        null
      } finally {
        res?.closeQuietly()
      }
      return responseJson?.let {
        if (PRINT_RESPONSE) {
          Log.println(it)
        }
        gson.fromJson(responseJson, modelType)
      }
    }
  }
}