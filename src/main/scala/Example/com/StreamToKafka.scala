package Example.com

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StructType

object StreamToKafka {
  def main (args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("StreamToKafka").setMaster("local[3]")
    //create SparkContext
    //val sc = new SparkContext(conf)
    //set SparkSession
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
      //.filter("type is video")

    //.load(path)
    val topic_name = "test"
    val kafka_broker = "localhost:9092"

    // Write key-value data from a DataFrame to a specific Kafka topic specified in an option
    val dsk = jsonDF
      //.select("action","data","type")
      .selectExpr("CAST(action AS STRING) as key", "CAST(data AS STRING) as value", "CAST(type AS STRING)")
      //.selectExpr("action":String,"data":String,"type":String)
      .writeStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafka_broker)

      .option("checkpointLocation", "checkpoint")

      //.option("startingOffsets", "latest")
      .option("topic", topic_name)
//      .format("console")
      .start
    dsk.awaitTermination()
  }
}
