package org.template.classification

import io.prediction.controller.PDataSource
import io.prediction.controller.EmptyEvaluationInfo
import io.prediction.controller.EmptyActualResult
import io.prediction.controller.Params
import io.prediction.data.storage.{PropertyMap, Storage}

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import grizzled.slf4j.Logger
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.factory.Nd4j


case class DataSourceParams(appId: Int) extends Params

class DataSource(val dsp: DataSourceParams)
  extends PDataSource[TrainingData,
      EmptyEvaluationInfo, Query, EmptyActualResult] {

  @transient lazy val logger = Logger[this.type]

  override
  def readTraining(sc: SparkContext): TrainingData = {
    val eventsDb = Storage.getPEvents()
    val eventsRDD: RDD[(String, PropertyMap)] = eventsDb.aggregateProperties(
      appId = dsp.appId,
      entityType = "record",
      required = Some(List("sepal-length", "sepal-width", "petal-length", "petal-width", "species")))(sc)

    val features: INDArray = Nd4j.zeros(eventsRDD.count().toInt, 4)
    val labels: INDArray = Nd4j.zeros(eventsRDD.count().toInt, 3)

    eventsRDD.zipWithIndex.foreach { case ((entityId, properties), row) =>
      val feature = Nd4j.create(
        Array(properties.get[Double]("sepal-length"),
          properties.get[Double]("sepal-width"),
          properties.get[Double]("petal-length"),
          properties.get[Double]("petal-width")
        )
      )
      features.putRow(row.toInt, feature)
      val label = Nd4j.create(
        properties.get[String]("species") match {
          case "Iris-setosa" => Array(1.0, 0.0, 0.0)
          case "Iris-versicolor" => Array(0.0, 1.0, 0.0)
          case "Iris-virginica" => Array(0.0, 0.0, 1.0)
        }
      )
      labels.putRow(row.toInt, label)
    }

    new TrainingData(new DataSet(features, labels))

    /*val iter: DataSetIterator = new IrisDataSetIterator(150, 150)
    val next = iter.next(110)
    next.normalizeZeroMeanZeroUnitVariance
    new TrainingData(next)*/
  }
}

class TrainingData(
  val records: DataSet
) extends Serializable {
  override def toString = {
    s"events: [${records.numExamples()}] (${records.get(0)}...)"
  }
}
