package stream.video

import org.apache.spark.SparkConf
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, Row, SparkSession}

object jdbc_postgresql {

  def main (args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("jdbc_postgresql").setMaster("local[3]")
    val spark = SparkSession
      .builder()
      .config(conf)
      .getOrCreate()

    val jdbcDF: DataFrame = spark.read
      .format("jdbc")
      .option("url", "jdbc:postgresql:streaming")
      .option("dbtable", "video")
      .option("user", "postgres")
      .option("password", "dominic")
      .load()




    jdbcDF.show()

    val dataSchema = List(
      StructField("item_id", StringType, true),
      StructField("title", StringType, true),
      StructField("type", StringType, true),
      StructField("publish", IntegerType, true),
      StructField("publish_date", TimestampType, true),
      StructField("episode_total", IntegerType, true),
      StructField("episode_current", IntegerType, true),
      StructField("last_video_add_date", TimestampType, true),
      StructField("is_delete", IntegerType, true),
      StructField("create_day", TimestampType, true)
    )

    val someData = Seq(
      Row("12abce","title","movie",0,"2018-08-29",1,1,"2018-08-29",1,"2018-08-29")
    )
    val newDF= spark.createDataFrame(
      spark.sparkContext.parallelize(someData),
      StructType(dataSchema)
    )
    newDF.show()
//    val dfjd= jdbcDF.union(newDF)
//    val dfjd = jdbcDF.joinWith(newDF, condition = "item_id")
    //val dfjd= jdbcDF.join(newDF, usingColumn = "item_id")

    jdbcDF.createOrReplaceTempView("video")

    newDF.write
      .insertInto("video")

    /*// Saving data to a JDBC source
    jdbcDF.write
      .mode(SaveMode.Append)
      .format("jdbc")
      .option("url", "jdbc:postgresql:streaming")
      .option("dbtable", "video")
      .option("user", "postgres")
      .option("password", "dominic")
      .save()*/

  }
}
