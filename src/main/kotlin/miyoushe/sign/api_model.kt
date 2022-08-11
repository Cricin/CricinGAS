package miyoushe.sign

data class ApiRes<Model>(
  val retcode: Int,
  val message: String,
  val data: Model?
)

/**
 * {
 *   "retcode":0,
 *   "message":"OK",
 *   "data": {
 *     "list":[
 *       {
 *         "game_biz":"hk4e_cn",
 *         "region":"cn_gf01",
 *         "game_uid":"170484691",
 *         "nickname":"炒面",
 *         "level":59,
 *         "is_chosen":false,
 *         "region_name":"天空岛",
 *         "is_official":true
 *       }
 *     ]
 *   }
 * }
 */
data class RoleList(
  val list: List<Role>
)

data class Role(
  val game_biz: String,
  val region: String,
  val game_uid: String,
  val nickname: String,
  val level: Int,
  val is_chosen: Boolean,
  val region_name: String,
  val is_official: Boolean,
)

/**
 * 正常签到
 * {"retcode":0,"message":"OK","data":{"code":"","risk_code":0,"gt":"","challenge":"","success":0}}
 *
 * 重复签到
 * {"data":null,"message":"旅行者,你已经签到过了","retcode":-5003}
 *
 * 未登录/登录过期
 * {"data":null,"message":"尚未登录","retcode":-100}
 */
class SignResult {
}

/**
 * {"code":0,"message":"","data":{"pushid":"79480596","readkey":"SCTbJ5pwPSO22qZ","error":"SUCCESS","errno":0}}
 */
data class PushRes(
  val code: Int,
  val message: String,
)