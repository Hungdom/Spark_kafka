package stream.video

import java.sql.{DriverManager, Timestamp}

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import org.apache.spark.SparkConf
import org.apache.spark.sql.streaming.StreamingQuery
import org.apache.spark.sql.{ForeachWriter, Row, SparkSession}

object StreamDataFromKafka {

  def createSQLStatement(action:String,jsonNode:JsonNode, sysTime:String) {
    //get val from jsonNode
    println(action)
    println(jsonNode)
    ///////////////////////////////create connection to postgreSQL//////////////////////////////////////////////////////
    val url = "jdbc:postgresql:streaming"
    val user = "postgres"
    val password = "dominic"
    val conn=DriverManager.getConnection(url,user,password)
    ////////////////////////////////add is_delete = 1 to video /////////////////////////////////////////////////////////
    if(action == "delete"){
      val itemId = jsonNode.path("_id").toString
      val updateIsDeleteSQL =
        """
          |insert into video (item_id,is_delete)
          |values(?,1)
          |ON CONFLICT (item_id) DO UPDATE
          |SET is_delete = 1;
        """.stripMargin
      val preparedStmt =conn.prepareStatement(updateIsDeleteSQL)
      preparedStmt.setString(1,itemId)
      preparedStmt.execute()
      println("Delete or update for "+itemId)
      preparedStmt.close()
    }
    else {
      val itemId = jsonNode.path("_id").toString
      val title = jsonNode.path("title").toString
      val typeV = jsonNode.path("type").toString
      val status = jsonNode.path("status").toString
      val publish = status.toInt
      val TimeStampDefault = "1970-01-01 00:00:00.1"//set default of publish_date
      var publishDateDefault = Timestamp.valueOf(TimeStampDefault)
      val episodeTotal = jsonNode.path("episode_total").toString
      val episodeCurrent = jsonNode.path("episode_current").toString
      val lastVAD = jsonNode.path("last_video_add_date").toString
      val tmpLastVAD = lastVAD.slice(1, lastVAD.length - 1)
      val lastVideoAddDate = Timestamp.valueOf(tmpLastVAD)
      ////////////////////////////////Insert + Update into video (table)////////////////////////////////////////////////
      //create date = sysTime*/
      val createDate = Timestamp.valueOf(sysTime)
      //insert + update
      val insertSQL =
        """
          |insert into video (item_id,title,type,episode_total,episode_current,last_video_add_date,is_delete,create_date)
          |values(?,?,?,?,?,?,?,?)
          |ON CONFLICT (item_id) DO UPDATE
          |SET title = EXCLUDED.title, type = EXCLUDED.type, publish = EXCLUDED.publish, publish_date = EXCLUDED.publish_date,
          |episode_total = EXCLUDED.episode_total, episode_current = EXCLUDED.episode_current,
          |last_video_add_date = EXCLUDED.last_video_add_date, create_date = EXCLUDED.create_date;
        """.stripMargin
      val preparedStmt = conn.prepareStatement(insertSQL)
      preparedStmt.setString(1, itemId)
      preparedStmt.setString(2, title)
      preparedStmt.setString(3, typeV)
      preparedStmt.setInt(4, episodeTotal.toInt)
      preparedStmt.setInt(5, episodeCurrent.toInt)
      preparedStmt.setTimestamp(6, lastVideoAddDate)
      preparedStmt.setInt(7, 0)
      preparedStmt.setTimestamp(8, createDate)
      preparedStmt.execute()
      println("Insert or Update new row with id " + itemId)
      preparedStmt.close()
      //////////////////////////// Update publish and publish_date into video///////////////////////////////////////////
      if (publish == 0) { //write default publish date into publish date
        val updatePublishSQL =
          """
            |UPDATE video SET publish = ?, publish_date = ?
            |WHERE item_id = ?;
          """.stripMargin
        val updatedStmt = conn.prepareStatement(updatePublishSQL)
        updatedStmt.setInt(1,0)
        updatedStmt.setTimestamp(2,publishDateDefault)
        updatedStmt.setString(3,itemId)
        updatedStmt.execute()
        updatedStmt.close()
      }else if (publish == 1){
        //publish : 1 (insert)
        val updatePublishSQL2 =
          """
            |UPDATE video SET publish = ?, publish_date = ?
            |WHERE item_id = ? AND publish_date IS NULL;
          """.stripMargin
        val updatedStmt1 = conn.prepareStatement(updatePublishSQL2)
        updatedStmt1.setInt(1,1)
        updatedStmt1.setTimestamp(2,lastVideoAddDate)
        updatedStmt1.setString(3,itemId)
        updatedStmt1.execute()
        updatedStmt1.close()
        //publish : 0 --> 1
        val updatePublishSQL1 =
          """
            |UPDATE video SET publish = ?, publish_date = ?
            |WHERE item_id = ? AND publish_date = ?;
          """.stripMargin
        val updatedStmt2 = conn.prepareStatement(updatePublishSQL1)
        updatedStmt2.setInt(1,1)
        updatedStmt2.setTimestamp(2,createDate)
        updatedStmt2.setString(3,itemId)
        updatedStmt2.setTimestamp(4,publishDateDefault)
        updatedStmt2.execute()
        updatedStmt2.close()
      }
      //////////////////////////// Update publish and publish_date into video///////////////////////////////////////////
    }
    conn.close()
  }

  def main (args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("StreamDataFromKafka").setMaster("local[3]")
    val spark = SparkSession
      .builder()
      .config(conf)
      .getOrCreate()

    //Creating a Kafka Source for Streaming Queries
    //kafka Source get from topic streaming_video
    val topic_name = "streaming_video"
    val kafka_broker = "localhost:9092"
    val dfk = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafka_broker)
      .option("subscribe", topic_name)
      .load()
      .selectExpr("CAST(topic AS STRING)","CAST(timestamp AS STRING)","CAST(key AS STRING)","CAST(value AS STRING)")
    //show data Stream to console
    //println(dfk)

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

            //get topic, datetime and action
            val topic=splitrawdata(0)
            val datetime=splitrawdata(1)
            val action = splitrawdata(2)
            //println(action)
            //parse data to json node
            val mapper = new ObjectMapper()
            val jnode: JsonNode =mapper.readTree(splitrawdata(3))
            val itemId = jnode.path("_id").toString
            createSQLStatement(action,jnode,datetime)
          }

          override def close(errorOrNull: Throwable): Unit = {
            println(s"*** ForEachWriter: close partition=[$myPartition] version=[$myVersion]")
            myPartition = None
            myVersion = None
          }
        })
      .start()
    query1.awaitTermination()
    /////////////////////////////////////////Finish Stream//////////////////////////////////////////////////////////////
  }
}