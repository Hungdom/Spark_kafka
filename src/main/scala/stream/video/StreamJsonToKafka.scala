package stream.video

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StructType


object StreamJsonToKafka {
  def main (args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("StreamJsonToKafka").setMaster("local[3]")
    val spark = SparkSession
      .builder()
      .config(conf)
      .getOrCreate()
    //set path
    val path = "/tmp/meta"

    //Creating streaming DataFrames from Json source
    val schemaDF = new StructType().add("action", "String").add("data", "String").add("type", "String")
    val jsonDF = spark
      .readStream
      .option("sep", ";")
      .schema(schemaDF)
      .json(path)
//      .filter()

    //.load(path)
    val topic_name = "streaming_video"
    val kafka_broker = "localhost:9092"

    // Write key-value data from a DataFrame to a specific Kafka topic specified in an option
    val dsk = jsonDF
      //.select("action","data","type")
      .selectExpr("CAST(action AS STRING) as key", "CAST(data AS STRING) as value")
      //filter type = video
      .filter("type = 'video'")
      .writeStream
      .option("rowPerSecond","1")
      .format("kafka")
      .option("kafka.bootstrap.servers", kafka_broker)
      .option("checkpointLocation", "checkpoint")
      //.option("startingOffsets", "latest")
      .option("topic", topic_name)
      //.format("console")
      .start
    dsk.awaitTermination()
  }
}
