package Example.com

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

object StreamFromKafka {
  def main (args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("StreamFromKafka").setMaster("local[3]")
    //create SparkContext
    //val sc = new SparkContext(conf)
    //set SparkSession
    val spark = SparkSession
      .builder()
      .config(conf)
      .getOrCreate()
    //set path
    val path = "/tmp/out"

    //Creating a Kafka Source for Streaming Queries
    //Subscribe to 1 topic:test
    val dfk = spark
      .readStream
      .format("kafka")
      .option("rowPerSecond","10")
      .option("kafka.bootstrap.servers", "localhost:9092")
      //.option("checkpointLocation", "checkpoint")
      //.option("startingOffsets", "latest")
      .option("subscribe", "test")
      .load()
    //queries
    val dfkf = dfk.selectExpr("CAST(key AS STRING)","CAST(value AS STRING)")

    val query1 = dfkf.writeStream
//    val query1 = dfk.writeStream
      .format("console")
      .start()

    dfkf.select("value")

    println(dfkf)
    query1.awaitTermination()

  }
}
