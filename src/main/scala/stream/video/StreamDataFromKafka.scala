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
          |UPDATE video SET is_delete = 1 WHERE item_id = ?
        """.stripMargin
      val preparedStmt =conn.prepareStatement(updateIsDeleteSQL)
      preparedStmt.setString(1,itemId)
      preparedStmt.execute()
      println("set is_delete for "+itemId)
      preparedStmt.close()
    }
    else {
      val itemId = jsonNode.path("_id").toString
      val title = jsonNode.path("title").toString
      val typeV = jsonNode.path("type").toString
      val status = jsonNode.path("status").toString
      val publish = status.toInt
      val y = "1111-11-11 11:11:11.111"
      var publishDate = Timestamp.valueOf(y)
      val episodeTotal = jsonNode.path("episode_total").toString
      val episodeCurrent = jsonNode.path("episode_current").toString
      val lastVideoAndDate = jsonNode.path("last_video_add_date").toString
      val tmpLastVideoAndDate = lastVideoAndDate.slice(1, lastVideoAndDate.length - 1)
      val LastVideoAndDate = Timestamp.valueOf(tmpLastVideoAndDate)
      ////////////////////////////////Insert into video (table)/////////////////////////////////////////////////////////
      if (action == "insert") {
        //create date = sysTime*/
        val createDate = Timestamp.valueOf(sysTime)

        //video already publish: publishDate =LastVideoAndDate
        if (publish == 1) {
          publishDate = LastVideoAndDate
        }
        //insert query
        val insertSQL =
          """
            |insert into video (item_id,title,type,publish,publish_date,episode_total,episode_current,last_video_add_date,is_delete,create_date)
            |values(?,?,?,?,?,?,?,?,?,?)
          """.stripMargin
        val preparedStmt = conn.prepareStatement(insertSQL)
        preparedStmt.setString(1, itemId)
        preparedStmt.setString(2, title)
        preparedStmt.setString(3, typeV)
        preparedStmt.setInt(4, publish)
        preparedStmt.setTimestamp(5, publishDate)
        preparedStmt.setInt(6, episodeTotal.toInt)
        preparedStmt.setInt(7, episodeCurrent.toInt)
        preparedStmt.setTimestamp(8, LastVideoAndDate)
        preparedStmt.setInt(9, 0)
        preparedStmt.setTimestamp(10, createDate)
        preparedStmt.execute()
        preparedStmt.close()
      }////////////////////////////////Update into Video ///////////////////////////////////////////////////////////////
      else if (action == "update") {
        //get current status of publish
        if (publish == 1) {
          publishDate = Timestamp.valueOf(sysTime)
        }
        val updateSQL =
          """
            |UPDATE video SET  title = ?, type = ?, publish = ?, publish_date = ?, episode_total = ?, episode_current = ?, last_video_add_date = ?
            |WHERE item_id = ?
          """.stripMargin
        val preparedStmt =conn.prepareStatement(updateSQL)
        preparedStmt.setString(8, itemId)
        preparedStmt.setString(1, title)
        preparedStmt.setString(2, typeV)
        preparedStmt.setInt(3, publish)
        preparedStmt.setTimestamp(4, publishDate)
        preparedStmt.setInt(5, episodeTotal.toInt)
        preparedStmt.setInt(6, episodeCurrent.toInt)
        preparedStmt.setTimestamp(7, LastVideoAndDate)
        preparedStmt.execute()
        preparedStmt.close()
        println("updated for "+ itemId)
      }
    }
    conn.close()
  }

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
    /////////////////////////////////////////Finish Stream/////////////////////////////////////////////////////////////
  }
}
