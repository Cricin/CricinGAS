package miyoushe.sign

object Log {
  fun println(msg: String) = kotlin.io.println(msg)
  fun printStacktrace(t: Throwable) = t.printStackTrace(System.err)
}