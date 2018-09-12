package stream.video

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import org.apache.spark.SparkConf
import org.apache.spark.sql.streaming.StreamingQuery
import org.apache.spark.sql.{ForeachWriter, Row, SparkSession}

object StreamDataFromKafka {

  class recordRawData(var topic:String, var datetime:String, var action:String, var data:String)
  {
    //rawData:[streaming_video,2018-09-11 17:57:47.385,delete,{"_id":"54abb0a717dc13146fdcf4e1"}]
    def parseRawData(rawData:String): Unit ={
      val listraw=rawData.split(",",4)

    }
  }
  //get_Action: get first column, and delete it
  def getAction(rawData : String): Unit ={

  }


  def main (args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("StreamDataFromKafka").setMaster("local[3]")
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
    //kafka Source get from topic streaming_video
    val dfk = spark
      .readStream
      .format("kafka")
      //.option("rowPerSecond","1")
      .option("kafka.bootstrap.servers", kafka_broker)
      //.option("checkpointLocation", "checkpoint")
      //.option("startingOffsets", "latest")
      .option("subscribe", topic_name)
      .load()
      .selectExpr("CAST(topic AS STRING)","CAST(timestamp AS STRING)","CAST(key AS STRING)","CAST(value AS STRING)")
      //.selectExpr("CAST(key AS STRING)","CAST(value AS STRING)")

    //show data Stream to console
    println(dfk)
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

            val mapper = new ObjectMapper()
            val jnode: JsonNode =mapper.readTree(splitrawdata(3))
            println(jnode)

            println(jnode.findValues("_id"))


//          working with insert/update/delete
            if(splitrawdata(2)=="insert"){
              println(splitrawdata(3))
            }else if(splitrawdata(2)=="update"){
              println(splitrawdata(3))
            }else if(splitrawdata(2)=="delete"){
              println(splitrawdata(3))
            }



            //working with



          }

          override def close(errorOrNull: Throwable): Unit = {
            println(s"*** ForEachWriter: close partition=[$myPartition] version=[$myVersion]")
            myPartition = None
            myVersion = None
          }
        })
      //.format("console")
      .start()
    query1.awaitTermination()

    /////////////////////////////////////////Finish Stream/////////////////////////////////////////////////////////////
  }
}
