package stream.video

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession


object StreamJsonToKafka {
  def main (args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("StreamJsonToKafka").setMaster("local[3]")
    val spark = SparkSession
      .builder()
      .config(conf)
      .getOrCreate()
    //set path
    val path = "/tmp/meta"

    val jsonDF = spark
      .readStream
      .text(path)

    //.load(path)
    val topicName = "streamvd"
    val kafkaBroker = "localhost:9092"

    // Write key-value data from a DataFrame to a specific Kafka topic specified in an option
    val dsk = jsonDF
      .writeStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaBroker)
      .option("checkpointLocation", "checkpoint")
      .option("topic", topicName)
      //.format("console")
      .start
    println("Start streaming data from: "+path)
    println("to topic: "+ topicName)
    dsk.awaitTermination()
  }
}