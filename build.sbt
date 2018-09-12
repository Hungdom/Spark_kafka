name := "Spark_kafka"

version := "0.1"

scalaVersion := "2.11.11"

val sparkVersion = "2.3.1"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-streaming" % sparkVersion,
  "org.apache.spark" %% "spark-streaming-kafka-0-10" %  sparkVersion,

  //"org.apache.spark" %% "spark-streaming-kafka-0-20" %  sparkVersion,
    // Needed for structured streams
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,

  "org.apache.spark" %% "spark-sql" % sparkVersion
)
// https://mvnrepository.com/artifact/org.apache.kafka/kafka
libraryDependencies += "org.apache.kafka" %% "kafka" % "2.0.0"
libraryDependencies += "org.postgresql" % "postgresql" % "9.4-1201-jdbc41"

dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core" % "2.9.4"
dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.4"
dependencyOverrides += "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.9.4"


