name := "Spark_kafka"

version := "0.1"

scalaVersion := "2.11.11"

val sparkVersion = "2.3.1"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-streaming" % sparkVersion,
  "org.apache.spark" %% "spark-streaming-kafka-0-10" %  sparkVersion,

    // Needed for structured streams
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,

  "org.apache.spark" %% "spark-sql" % sparkVersion
)