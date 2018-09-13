package stream.video

import java.sql.Timestamp

object testTimestamp extends App {
  println("hello world")

  val x = "2018-09-05 15:19:43.380"

  val xtime=Timestamp.valueOf(x)

  println(xtime)

  val y = "1111-11-11 11:11:11.111"

  val ytime=Timestamp.valueOf(y)
  println(ytime)

}
