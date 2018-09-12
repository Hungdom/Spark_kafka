package stream.video

import org.apache.spark.SparkConf
import org.apache.spark.sql.types.{IntegerType, StringType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SaveMode, SparkSession}

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

    //val data1: DataFrame = {"directors_detail":1,""}

    jdbcDF.createOrReplaceTempView("video")
    spark.sql("SELECT * FROM video").show()

    val dataSchema = List(
      StructField("item_id", StringType, true),
      StructField("title", StringType, true),
      StructField("type", StringType, true),
      StructField("publish", IntegerType, true),
      StructField("publish_date", StringType, true),
      StructField("episode_total", IntegerType, true),
      StructField("episode_current", IntegerType, true),
      StructField("last_video_add_date", StringType, true),
      StructField("is_delete", IntegerType, true),
      StructField("create_day", StringType, true)
    )

    val someData = Seq(
      Row("12a","title","movie",0,"2018-08-29",1,1,"2018-08-29",0,"2018-08-29"),
      Row("12a","title","movie",0,"2018-08-29",1,1,"2018-08-29",0,"2018-08-29"),
      Row("12a","title","movie",0,"2018-08-29",1,1,"2018-08-29",0,"2018-08-29")
    )
    val newDF= spark.createDataFrame(
      spark.sparkContext.parallelize(someData),
      StructType(dataSchema)
    )
    val dfjd= jdbcDF.join(newDF, usingColumn = "item_id")

    // Saving data to a JDBC source
    dfjd.write
      .mode(SaveMode.ErrorIfExists)
      .format("jdbc")
      .option("url", "jdbc:postgresql:streaming")
      .option("dbtable", "video")
      .option("user", "postgres")
      .option("password", "dominic")
      .save()

  }
}
