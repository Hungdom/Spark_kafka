package stream.video

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

object StreamDataFromKafka {

  def main (args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("StreamDataFromKafka").setMaster("local[3]")
    //create SparkContext
    //val sc = new SparkContext(conf)
    //set SparkSession
    val spark = SparkSession
      .builder()
      .config(conf)
      .getOrCreate()
//    set path
//    val path = "/tmp/out"

    val topic_name = "streaming_video"
    val kafka_broker = "localhost:9092"

    //Creating a Kafka Source for Streaming Queries
    //Subscribe to 1 topic:test
    val dfk = spark
      .readStream
      .format("kafka")
      //.option("rowPerSecond","1")
      .option("kafka.bootstrap.servers", kafka_broker)
      //.option("checkpointLocation", "checkpoint")
      //.option("startingOffsets", "latest")
      .option("subscribe", topic_name)
      .load()
    //queries select key: action and value: data
    val dfkf = dfk.selectExpr("CAST(key AS STRING)","CAST(value AS STRING)")
    //show data Stream to console
    val query1 = dfkf.writeStream
      //    val query1 = dfk.writeStream
      .format("console")
      //.foreach()
      .start()


    println(dfkf)
    query1.awaitTermination()






  }
}
