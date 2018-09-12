package stream.video

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import org.apache.spark.SparkConf
import org.apache.spark.sql.streaming.StreamingQuery
import org.apache.spark.sql.types.{IntegerType, StringType, StructField}
import org.apache.spark.sql.{ForeachWriter, Row, SparkSession}

object StreamDataFromKafka {

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


  def main (args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("StreamDataFromKafka").setMaster("local[3]")
    val spark = SparkSession
      .builder()
      .config(conf)
      .getOrCreate()

    val topic_name = "streaming_video"
    val kafka_broker = "localhost:9092"

    //Creating a Kafka Source for Streaming Queries
    //kafka Source get from topic streaming_video
    val dfk = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafka_broker)
      .option("subscribe", topic_name)
      .load()
      .selectExpr("CAST(topic AS STRING)","CAST(timestamp AS STRING)","CAST(key AS STRING)","CAST(value AS STRING)")

    //show data Stream to console
    println(dfk)
    //foreachWriter write data to postgresql
    val query1: StreamingQuery = dfk
      .writeStream
        .foreach(new ForeachWriter[Row] {

          var myPartition: Option[Long] = None
          var myVersion: Option[Long] = None

          override def open(partitionId: Long, version: Long): Boolean = {
            myPartition = Some(partitionId)
            myVersion = Some(version)
            println(s"*** ForEachWriter: open partition=[$partitionId] version=[$version]")
            val processThisOne = partitionId % 2 == 0
            // We only accept this partition/batch combination if we return true -- in this case we'll only do so for
            // even numbered partitions. This decision could have been based on the version ID as well.
            processThisOne
          }

          override def process(record: Row): Unit = {
            //println(s"*** ForEachWriter: process partition=[$myPartition] version=[$myVersion] record=$record")
            //println(record)
            val rawdata: String =record.toString()
            val tmp=rawdata.slice(1,rawdata.length-1)
            val splitrawdata: Array[String] =tmp.split(",",4)

            println(splitrawdata(2))
            //get topic, datetime and action
            val topic=splitrawdata(0)
            val datetime=splitrawdata(1)
            val action = splitrawdata(2)

            //parse data to json node
            val mapper = new ObjectMapper()
            val jnode: JsonNode =mapper.readTree(splitrawdata(3))
            println(jnode)

            //load data from postgreSQL by jdbc
            val jdbcDF = spark.read
              .format("jdbc")
              .option("url", "jdbc:postgresql:streaming")
              .option("dbtable", "video")
              .option("user", "postgres")
              .option("password", "dominic")
              .load()

            jdbcDF.show()

            //working with insert/update/delete
            if(action=="insert"){
              //get from:
              // _id    | title | type | status | episode_total | episode_current | last_video_and_date
              //parse to:
              //item_id | title | type | publish | *publish_date | episode_total | episode_current | last_video_and_date | *is_delete=0 | *create_date
              println(jnode.path("_id"))
              println(jnode.path("title"))
              println(jnode.path("type"))
              println(jnode.path("status"))
              println(jnode.path("last_video_add_date"))
              println(jnode.path("episode_current"))
              println(jnode.path("episode_total"))

            }else if(action=="update"){
              // detect item_id in psql.
              // update val
              println(jnode.path("_id"))
              println(jnode.path("title"))
              println(jnode.path("type"))
              println(jnode.path("status"))
              println(jnode.path("last_video_add_date"))
              println(jnode.path("episode_current"))
              println(jnode.path("episode_total"))

            }else if(action=="delete"){
              // detect item_id in psql
              // update is_delete=1
              println(jnode.path("_id"))
            }

            // Saving data to a JDBC source
            jdbcDF.write
              .format("jdbc")
              .option("url", "jdbc:postgresql:streaming")
              .option("dbtable", "video")
              .option("user", "postgres")
              .option("password", "dominic")
              .save()

          }

          override def close(errorOrNull: Throwable): Unit = {
            println(s"*** ForEachWriter: close partition=[$myPartition] version=[$myVersion]")
            myPartition = None
            myVersion = None
          }
        })
      .start()
    query1.awaitTermination()
    /////////////////////////////////////////Finish Stream/////////////////////////////////////////////////////////////
  }
}
