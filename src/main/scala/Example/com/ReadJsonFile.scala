package Example.com

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StructType

object ReadJsonFile {

  //case class DatasStructure(Action: String, Data: String, Type: String)

  def main (args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("ReadJsonFile").setMaster("local[3]")
    //create SparkContext
    //val sc = new SparkContext(conf)
    //set SparkSession
    val spark = SparkSession
      .builder()
      .config(conf)
      .getOrCreate()
    //set path
    val path = "/tmp/meta"

    //val df = spark.read.json(path)
    //df.show()
    //println(df.isStreaming)

    //Creating streaming DataFrames
    val schemaDF = new StructType().add("action", "String").add("data", "String").add("type", "String")
    val jsonDF = spark
      .readStream
      .option("sep", ";")
      .schema(schemaDF)
      .json(path)

      //.load(path)

    //jsonDF.show()
    //val topic_name = "test"
    //val kafka_broker = "localhost:9092"


    // Write key-value data from a DataFrame to a specific Kafka topic specified in an option
    val ds = jsonDF
      //.select("action","data","type")
      .selectExpr("CAST(action AS STRING)", "CAST(data AS STRING) as value", "CAST(type AS STRING)")
      //.selectExpr("action":String,"data":String,"type":String)
      .writeStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")

      .option("checkpointLocation", "checkpoint")
//      .format("console")
      .option("startingOffsets", "latest")
      .option("topic", "test")
      .start
    ds.awaitTermination()
    // Write key-value data from a DataFrame to a specific Kafka topic specified in an option
    /*
    val schemaDF = new StructType().add("action", "String").add("data", "String").add("type", "String")
    val path = "/tmp/meta"

    import spark.implicits._
    /*val lines = spark.readStream
      .format("socket")
      .option("host", "localhost")
      .option("port", 9999)
      .load()

    val lines = spark
      .readStream
      .textFile(path)

    // Split the lines into words
    val words = lines.as[String]flatMap(_.split(" "))

    // Generate running word count
    val wordCounts = words.groupBy("value").count()

    val query = wordCounts.writeStream
      .outputMode("complete")
      .format("console")
      .start()

    query.awaitTermination()*/*/

/*
    import org.apache.spark.sql.Dataset
    import org.apache.spark.sql.Encoders
    val kafkaStreamSet = sparkSession
      .readStream
      .format("kafka")
      .option("kafka.bootstrap
      .servers", kafkaBootstrap)
      .option("subscribe", kafkaTopic)
      .option("startingOffsets", "latest")
      .option("failOnDataLoss", false)
      .option("maxOffsetsPerTrigger", offsetsPerTrigger)
      .load

    //raw message to ClickStream
    val ds1 = kafkaStreamSet.mapPartitions(processClickStreamMessages, Encoders.bean(classOf[Nothing]))*/



  }
}
