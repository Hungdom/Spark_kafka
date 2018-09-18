package stream.video

import java.sql.{DriverManager, Timestamp}

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import org.apache.spark.SparkConf
import org.apache.spark.sql.streaming.StreamingQuery
import org.apache.spark.sql.{ForeachWriter, Row, SparkSession}

object StreamDataFromKafka {

  def createSqlStatement(action:String,jsonNode:JsonNode, sysTime:String) {
    //get val from jsonNode
    println(action)
    println(jsonNode)
    ///////////////////////////////create connection to postgreSQL//////////////////////////////////////////////////////

    Class.forName("org.postgresql.Driver")
    val url = "jdbc:postgresql:streaming"
    val user = "postgres"
    val password = "dominic"
    val conn=DriverManager.getConnection(url,user,password)

    //var conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/streaming", user, password)
    ////////////////////////////////add is_delete = 1 to video /////////////////////////////////////////////////////////
    if(action == "delete"){
      val itemIdtmp = jsonNode.path("_id").toString
      val itemId = itemIdtmp.slice(1,itemIdtmp.length-1)
      val delete_date = Timestamp.valueOf(sysTime)
      val updateIsDeleteSQL =
        """
          |insert into video (item_id,is_delete,delete_date)
          |values(?,1,?)
          |ON CONFLICT (item_id) DO UPDATE
          |SET is_delete = 1, delete_date=?;
        """.stripMargin
      val preparedStmt =conn.prepareStatement(updateIsDeleteSQL)
      preparedStmt.setString(1,itemId)
      preparedStmt.setTimestamp(2, delete_date)
      preparedStmt.setTimestamp(3, delete_date)
      preparedStmt.execute()
      println("Delete or update for "+itemId)
      preparedStmt.close()
    }
    else {
      val itemIdTmp = jsonNode.path("_id").toString
      val itemId = itemIdTmp.slice(1,itemIdTmp.length-1)
      val titleTmp = jsonNode.path("title").toString
      val title = titleTmp.slice(1,titleTmp.length-1)
      val typeVTmp = jsonNode.path("type").toString
      val typeV = typeVTmp.slice(1,typeVTmp.length-1)
      val status = jsonNode.path("status").toString
      val publish = status.toInt
      //set default of publish_date
      val TimeStampDefault = "1970-01-01 00:00:00.1"
      val publishDateDefault = Timestamp.valueOf(TimeStampDefault)
      val episodeTotal = jsonNode.path("episode_total").toString
      val episodeCurrent = jsonNode.path("episode_current").toString
      val lastVAD = jsonNode.path("last_video_add_date").toString
      val tmpLastVAD = lastVAD.slice(1, lastVAD.length - 1)
      val lastVideoAddDate = Timestamp.valueOf(tmpLastVAD)
      ////////////////////////////////Insert + Update into video (table)////////////////////////////////////////////////
      //create date = sysTime*/
      val lastUpdateDate = Timestamp.valueOf(sysTime)
      //insert + update
      val insertSQL =
        """
          |insert into video (item_id,title,type,episode_total,episode_current,last_video_add_date,is_delete,delete_date,last_update_date)
          |values(?,?,?,?,?,?,?,?,?)
          |ON CONFLICT (item_id) DO UPDATE
          |SET title = EXCLUDED.title, type = EXCLUDED.type,
          |episode_total = EXCLUDED.episode_total, episode_current = EXCLUDED.episode_current,
          |last_video_add_date = EXCLUDED.last_video_add_date, last_update_date = EXCLUDED.last_update_date;
        """.stripMargin
      val preparedStmt = conn.prepareStatement(insertSQL)
      preparedStmt.setString(1, itemId)
      preparedStmt.setString(2, title)
      preparedStmt.setString(3, typeV)
      preparedStmt.setInt(4, episodeTotal.toInt)
      preparedStmt.setInt(5, episodeCurrent.toInt)
      preparedStmt.setTimestamp(6, lastVideoAddDate)
      preparedStmt.setInt(7, 0)
      preparedStmt.setTimestamp(8, publishDateDefault)
      preparedStmt.setTimestamp(9, lastUpdateDate)
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

        //publish : 0 --> 1
        val updatePublishSQL1 =
          """
            |UPDATE video SET publish = ?, publish_date = ?
            |WHERE item_id = ? AND publish_date = ?;
          """.stripMargin
        val updatedStmt2 = conn.prepareStatement(updatePublishSQL1)
        updatedStmt2.setInt(1,1)
        updatedStmt2.setTimestamp(2,lastUpdateDate)
        updatedStmt2.setString(3,itemId)
        updatedStmt2.setTimestamp(4,publishDateDefault)
        updatedStmt2.execute()
        updatedStmt2.close()
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
    val topicName = "streamvd"
    val kafkaBroker = "localhost:9092"
    val dfk = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaBroker)
      .option("subscribe", topicName)
      .load()
      .selectExpr("CAST(topic AS STRING)","CAST(timestamp AS STRING)","CAST(value AS STRING)")
    //show data Stream to console
    //println(dfk)

    //foreachWriter write data to postgresql
    val query1: StreamingQuery = dfk
      .writeStream
        .format("console")
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
            val rawData: String =record.toString()
            println(rawData)
            val tmp=rawData.slice(1,rawData.length-1)
            val splitRawData: Array[String] =tmp.split(",",3)

            //get topic, datetime and data
            //val topic=splitRawData(0)
            val datetime=splitRawData(1) //get date time
            val mapper = new ObjectMapper()
            val jNodeRowData = mapper.readTree(splitRawData(2))
            //get action
            val actionTmp = jNodeRowData.path("action").toString()
            val action = actionTmp.slice(1,actionTmp.length-1)
            println(action)
            //get type of data
            val typeOfDataTmp = jNodeRowData.path("type").toString()
            val typeOfData = typeOfDataTmp.slice(1,typeOfDataTmp.length-1)
            println(typeOfData)
            //get data and parse to json
            val dataTmp = jNodeRowData.path("data").toString()
            println(dataTmp)
            val data = dataTmp.slice(1,dataTmp.length-1)
            println(data)
            val dataOut = data.replace("\\\":\\\"", "\":\"").replace("\\\",\\\"", "\",\"")
                .replace("{\\\"", "{\"").replace("\\\":[","\":[").replace(",\\\"",",\"")
                .replace("[\\\"","[\"").replace("\\\":","\":").replace("\\\"],","\"],")
                .replace("\\\"},","\"},").replace("\\\"}]","\"}]").replace("\\\"}","\"}")
            println(dataOut)

            val jNode= mapper.readTree(dataOut)

            if(typeOfData == "video") {
              createSqlStatement(action, jNode, datetime)
            }
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